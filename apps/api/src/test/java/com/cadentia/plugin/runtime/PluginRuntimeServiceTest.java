package com.cadentia.plugin.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.plugin.InMemoryPluginRegistryRepository;
import com.cadentia.plugin.PluginModels.CertificationStatus;
import com.cadentia.plugin.PluginModels.DeprecationStatus;
import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import com.cadentia.plugin.PluginModels.TrustTier;
import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.policy.PluginPolicyEnforcementService;
import com.cadentia.plugin.policy.PluginPolicyModels.CanonicalPolicySnapshot;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExecutionStatus;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginAdapterOutput;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginRuntimeServiceTest {
    private final UUID pluginVersionId = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private final UUID configurationVersionId = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private InMemoryPluginRegistryRepository repository;
    private PluginAdapterRegistry adapterRegistry;
    private PluginRuntimeService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPluginRegistryRepository();
        PluginRegistryAuditRecorder auditRecorder = new PluginRegistryAuditRecorder();
        adapterRegistry = new PluginAdapterRegistry();
        service = new PluginRuntimeService(repository, new PluginPolicyEnforcementService(repository, auditRecorder), adapterRegistry,
                auditRecorder, new ObjectMapper());
        repository.savePackage(pluginPackage(LifecycleStatus.ENABLED));
        repository.saveConfiguration(new PluginConfigurationSnapshot(configurationVersionId, pluginVersionId, "river",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", null, Map.of(), Instant.now(), "admin"));
        repository.saveEnablement(new PluginEnablement(UUID.randomUUID(), pluginVersionId, configurationVersionId, "river",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", LifecycleStatus.ENABLED, Instant.now(), null, "admin", null));
    }

    @Test
    void executesEnabledPluginThroughApprovedAdapterAndSanitizesUnsafeOutput() {
        adapterRegistry.register(pluginVersionId, envelope -> output(List.of("approved-visible"), List.of("approved-visible", "unapproved"), Map.of()));

        var result = service.execute(invocation(false));

        assertThat(result.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(result.outputs()).hasSize(1);
        assertThat(result.outputs().get(0).recommendableSongIds()).containsExactly("approved-visible");
        assertThat(result.metadata().get(0).inputDigest()).isNotBlank();
    }

    @Test
    void doesNotExecuteDisabledOrRevokedPluginVersions() {
        repository.savePackage(pluginPackage(LifecycleStatus.REVOKED));
        adapterRegistry.register(pluginVersionId, envelope -> output(List.of("approved-visible"), List.of("approved-visible"), Map.of()));

        var result = service.execute(invocation(false));

        assertThat(result.status()).isEqualTo(ExecutionStatus.DEGRADED);
        assertThat(result.safeErrors()).containsExactly("PLUGIN_DISABLED");
    }

    @Test
    void rejectsExecutionInsideCoreTransactionBeforeCallingAdapter() {
        adapterRegistry.register(pluginVersionId, envelope -> output(List.of("approved-visible"), List.of("approved-visible"), Map.of()));

        var result = service.execute(invocation(true));

        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED_CLOSED);
        assertThat(result.safeErrors()).containsExactly("PLUGIN_RUNTIME_POLICY_DENIED");
    }

    @Test
    void returnsDegradedResultForTimeoutExceptionInvalidAndDuplicateOutput() {
        adapterRegistry.register(pluginVersionId, envelope -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return output(List.of("approved-visible"), List.of("approved-visible"), Map.of());
        });
        assertThat(service.execute(invocation(false)).safeErrors()).containsExactly("PLUGIN_TIMEOUT");

        adapterRegistry.register(pluginVersionId, envelope -> {
            throw new IllegalStateException("boom with unsafe details");
        });
        assertThat(service.execute(invocation(false)).safeErrors()).containsExactly("PLUGIN_CIRCUIT_OPEN");

        setUp();
        adapterRegistry.register(pluginVersionId, envelope -> output(List.of("approved-visible", "approved-visible"), List.of(), Map.of()));
        assertThat(service.execute(invocation(false)).safeErrors()).containsExactly("PLUGIN_OUTPUT_INVALID");

        setUp();
        adapterRegistry.register(pluginVersionId, envelope -> output(List.of("approved-visible"), List.of(), Map.of("generatedAt", Instant.now().toString())));
        assertThat(service.execute(invocation(false)).safeErrors()).containsExactly("PLUGIN_NON_DETERMINISTIC_OUTPUT");
    }

    @Test
    void producesRepeatableRecommendationMetadataForSameStableInputs() {
        adapterRegistry.register(pluginVersionId, envelope -> output(List.of("approved-visible"), List.of("approved-visible"),
                Map.of("seed", envelope.deterministicSeed())));

        var first = service.execute(invocation(false));
        var second = service.execute(invocation(false));

        assertThat(first.metadata().get(0).inputDigest()).isEqualTo(second.metadata().get(0).inputDigest());
        assertThat(first.metadata().get(0).outputDigest()).isEqualTo(second.metadata().get(0).outputDigest());
    }

    @Test
    void supportsConcurrentExecutionWithoutPartialCanonicalMutation() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        adapterRegistry.register(pluginVersionId, envelope -> {
            try {
                start.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return output(List.of("approved-visible"), List.of("approved-visible", "foreign"), Map.of());
        });
        var executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> service.execute(invocation(false)));
        var second = executor.submit(() -> service.execute(invocation(false)));
        start.countDown();

        assertThat(first.get().outputs().get(0).recommendableSongIds()).containsExactly("approved-visible");
        assertThat(second.get().outputs().get(0).recommendableSongIds()).containsExactly("approved-visible");
        executor.shutdownNow();
    }

    private PluginRuntimeInvocation invocation(boolean coreTransactionActive) {
        return new PluginRuntimeInvocation("river", Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", "1.0.0", "actor",
                Set.of("WORSHIP_PLANNER"), Set.of("CCLI"), Map.of("candidateIds", List.of("approved-visible"), "locale", "en-US"),
                canonical(), "catalog-v1", "policy-v1", 42L, coreTransactionActive, null);
    }

    private CanonicalPolicySnapshot canonical() {
        return new CanonicalPolicySnapshot(Set.of("approved-visible", "unapproved"), Set.of("approved-visible"),
                Set.of("approved-visible"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of("CCLI"), Set.of("WORSHIP_PLANNER"),
                Set.of(), Set.of("river"), Map.of("package", Set.of("CCLI")));
    }

    private PluginPackage pluginPackage(LifecycleStatus status) {
        return new PluginPackage(pluginVersionId, "stable", "package", "provider", "1.0.0", List.of("1.0.0"),
                List.of("RECOMMENDATION_CONSTRAINT"), TrustTier.CORE, "checksum", "signature", CertificationStatus.CERTIFIED,
                "registry", status, DeprecationStatus.ACTIVE, null, Instant.now(), Instant.now(), "admin", "admin");
    }

    private PluginAdapterOutput output(List<String> songs, List<String> recommendable, Map<String, Object> attributes) {
        return new PluginAdapterOutput(songs, recommendable, List.of(), List.of(), List.of(), List.of(), List.of(), List.of("CCLI"),
                List.of("river"), attributes);
    }
}
