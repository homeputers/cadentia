package com.cadentia.plugin.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.cadentia.plugin.policy.PluginPolicyModels.CanonicalPolicySnapshot;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginInvocationRequest;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginOutput;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PluginPolicyEnforcementServiceTest {
    private final UUID pluginVersionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID configurationVersionId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private InMemoryPluginRegistryRepository repository;
    private PluginRegistryAuditRecorder auditRecorder;
    private PluginPolicyEnforcementService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPluginRegistryRepository();
        auditRecorder = new PluginRegistryAuditRecorder();
        service = new PluginPolicyEnforcementService(repository, auditRecorder);
        repository.savePackage(pluginPackage(LifecycleStatus.ENABLED, TrustTier.COMMUNITY, "RECOMMENDATION_CONSTRAINT"));
        repository.saveConfiguration(new PluginConfigurationSnapshot(configurationVersionId, pluginVersionId, "river",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", null, Map.of(), Instant.now(), "admin"));
        repository.saveEnablement(new PluginEnablement(UUID.randomUUID(), pluginVersionId, configurationVersionId, "river",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", LifecycleStatus.ENABLED, Instant.now(), null, "admin", null));
    }

    @Test
    void buildsMinimalInputViewFromCanonicalInstanceVisibleApprovedData() {
        PluginInvocationRequest request = request(Map.of(
                "candidateIds", List.of("approved-visible", "unapproved", "other-instance"),
                "reviewNoteIds", List.of("privileged-note"),
                "locale", "en-US"));

        var policy = service.authorize(request, canonical());

        assertThat(policy.inputView()).containsEntry("candidateIds", List.of("approved-visible"));
        assertThat(policy.inputView()).containsEntry("locale", "en-US");
        assertThat(policy.inputView()).doesNotContainKey("reviewNoteIds");
    }

    @Test
    void filtersOutputThroughApprovalLicensingVisibilityAndCanonicalExistence() {
        var policy = service.authorize(request(Map.of()), canonical());
        PluginOutput output = new PluginOutput(
                List.of("approved-visible", "other-instance"),
                List.of("approved-visible", "unapproved"),
                List.of("arrangement-1", "arrangement-foreign"),
                List.of("asset-1", "asset-foreign"),
                List.of("person-1", "person-foreign"),
                List.of("plan-1", "plan-foreign"),
                List.of("privileged-note"),
                List.of("CCLI", "UNLICENSED"),
                List.of("river", "hillside"),
                Map.of("approvalStatus", "APPROVED", "safe", "value"));

        var sanitized = service.sanitizeOutput(policy, output, canonical());

        assertThat(sanitized.songIds()).containsExactly("approved-visible");
        assertThat(sanitized.recommendableSongIds()).containsExactly("approved-visible");
        assertThat(sanitized.reviewNoteIds()).isEmpty();
        assertThat(sanitized.licenseScopes()).containsExactly("CCLI");
        assertThat(sanitized.instanceIds()).containsExactly("river");
        assertThat(sanitized.attributes()).containsEntry("safe", "value").doesNotContainKey("approvalStatus");
        assertThat(sanitized.strippedFields()).contains("songIds", "recommendableSongIds", "reviewNoteIds", "licenseScopes", "instanceIds");
    }

    @Test
    void deniesDisabledPluginWithSafeDiagnosticsAndAudit() {
        repository.saveEnablement(new PluginEnablement(UUID.randomUUID(), pluginVersionId, configurationVersionId, "river",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", LifecycleStatus.DISABLED, Instant.now(), null, "admin", null));

        assertThatThrownBy(() -> service.authorize(request(Map.of()), canonical()))
                .isInstanceOf(PluginPolicyException.class)
                .hasMessage("Plugin execution is not permitted by policy.")
                .extracting("safeCode")
                .isEqualTo("PLUGIN_DISABLED");
        assertThat(auditRecorder.events()).hasSize(1);
    }

    @Test
    void deniesLicenseBypassAndForeignInstanceScope() {
        PluginInvocationRequest badLicense = new PluginInvocationRequest(pluginVersionId, configurationVersionId, "river",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", "1.0.0", "actor", Set.of("WORSHIP_PLANNER"),
                "package", Set.of("PREMIUM"), Map.of());
        PluginInvocationRequest foreignInstance = new PluginInvocationRequest(pluginVersionId, configurationVersionId, "hillside",
                Environment.PRODUCTION, "RECOMMENDATION_CONSTRAINT", "1.0.0", "actor", Set.of("WORSHIP_PLANNER"),
                "package", Set.of("CCLI"), Map.of());

        assertThatThrownBy(() -> service.authorize(badLicense, canonical()))
                .isInstanceOf(PluginPolicyException.class)
                .extracting("safeCode")
                .isEqualTo("PLUGIN_LICENSE_DENIED");
        assertThatThrownBy(() -> service.authorize(foreignInstance, canonical()))
                .isInstanceOf(PluginPolicyException.class)
                .extracting("safeCode")
                .isEqualTo("PLUGIN_DISABLED");
    }

    private PluginInvocationRequest request(Map<String, Object> input) {
        return new PluginInvocationRequest(pluginVersionId, configurationVersionId, "river", Environment.PRODUCTION,
                "RECOMMENDATION_CONSTRAINT", "1.0.0", "actor", Set.of("WORSHIP_PLANNER"), "package", Set.of("CCLI"), input);
    }

    private CanonicalPolicySnapshot canonical() {
        return new CanonicalPolicySnapshot(
                Set.of("approved-visible", "unapproved"),
                Set.of("approved-visible"),
                Set.of("approved-visible"),
                Set.of("arrangement-1"),
                Set.of("asset-1"),
                Set.of("person-1"),
                Set.of("plan-1"),
                Set.of("CCLI"),
                Set.of("WORSHIP_PLANNER"),
                Set.of(),
                Set.of("river"),
                Map.of("package", Set.of("CCLI")));
    }

    private PluginPackage pluginPackage(LifecycleStatus status, TrustTier trustTier, String extensionPoint) {
        return new PluginPackage(pluginVersionId, "stable", "package", "provider", "1.0.0", List.of("1.0.0"),
                List.of(extensionPoint), trustTier, "checksum", "signature", CertificationStatus.CERTIFIED, "registry", status,
                DeprecationStatus.ACTIVE, null, Instant.now(), Instant.now(), "admin", "admin");
    }
}
