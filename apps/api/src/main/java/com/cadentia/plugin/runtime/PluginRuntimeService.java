package com.cadentia.plugin.runtime;

import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.PluginRegistryRepository;
import com.cadentia.plugin.policy.PluginPolicyEnforcementService;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginInvocationRequest;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginOutput;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExecutionStatus;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExtensionPointRuntimePolicy;
import com.cadentia.plugin.runtime.PluginRuntimeModels.FailureAction;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginAdapterOutput;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginExecutionMetadata;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginInvocationEnvelope;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

@Service
public class PluginRuntimeService implements PluginRuntimeGateway {
    private static final Map<String, ExtensionPointRuntimePolicy> DEFAULT_POLICIES = Map.of(
            "IMPORT_CONNECTOR", new ExtensionPointRuntimePolicy("IMPORT_CONNECTOR", Duration.ofSeconds(30), 1, 3,
                    FailureAction.MARK_JOB_FAILED, false, false),
            "METADATA_TRANSFORM", new ExtensionPointRuntimePolicy("METADATA_TRANSFORM", Duration.ofSeconds(5), 1, 3,
                    FailureAction.SKIP_PLUGIN_CONTINUE, false, true),
            "EXPORT_RENDERER", new ExtensionPointRuntimePolicy("EXPORT_RENDERER", Duration.ofSeconds(10), 1, 3,
                    FailureAction.FAIL_CLOSED, false, false),
            "OUTBOUND_PUBLISH_HOOK", new ExtensionPointRuntimePolicy("OUTBOUND_PUBLISH_HOOK", Duration.ofSeconds(5), 2, 5,
                    FailureAction.RETRY_ASYNCHRONOUSLY, false, true),
            "RECOMMENDATION_CONSTRAINT", new ExtensionPointRuntimePolicy("RECOMMENDATION_CONSTRAINT", Duration.ofMillis(500), 1, 1,
                    FailureAction.SKIP_PLUGIN_CONTINUE, true, true),
            "SCORING_CONTRIBUTION", new ExtensionPointRuntimePolicy("SCORING_CONTRIBUTION", Duration.ofMillis(500), 1, 1,
                    FailureAction.FAIL_CLOSED, true, false));

    private final PluginRegistryRepository repository;
    private final PluginPolicyEnforcementService policyEnforcementService;
    private final PluginAdapterRegistry adapterRegistry;
    private final PluginRegistryAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Integer> circuitBreakerFailures = new ConcurrentHashMap<>();

    public PluginRuntimeService(PluginRegistryRepository repository, PluginPolicyEnforcementService policyEnforcementService,
            PluginAdapterRegistry adapterRegistry, PluginRegistryAuditRecorder auditRecorder, ObjectMapper objectMapper) {
        this.repository = repository;
        this.policyEnforcementService = policyEnforcementService;
        this.adapterRegistry = adapterRegistry;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    public PluginRuntimeResult execute(PluginRuntimeInvocation invocation) {
        ExtensionPointRuntimePolicy runtimePolicy = DEFAULT_POLICIES.get(invocation.extensionPoint());
        if (runtimePolicy == null || invocation.coreTransactionActive()) {
            return new PluginRuntimeResult(ExecutionStatus.FAILED_CLOSED, List.of(), List.of(), List.of("PLUGIN_RUNTIME_POLICY_DENIED"), true);
        }
        return repository.findEnablement(invocation.churchInstanceId(), invocation.environment(), invocation.extensionPoint())
                .filter(enablement -> enablement.status() == LifecycleStatus.ENABLED)
                .map(enablement -> executeEnabled(invocation, runtimePolicy, enablement))
                .orElseGet(() -> degraded(runtimePolicy, "PLUGIN_DISABLED", List.of()));
    }

    private PluginRuntimeResult executeEnabled(PluginRuntimeInvocation invocation, ExtensionPointRuntimePolicy runtimePolicy,
            PluginEnablement enablement) {
        PluginPackage pluginPackage = repository.findPackage(enablement.pluginVersionId()).orElse(null);
        if (pluginPackage == null || pluginPackage.lifecycleStatus() == LifecycleStatus.DISABLED
                || pluginPackage.lifecycleStatus() == LifecycleStatus.REVOKED) {
            return degraded(runtimePolicy, "PLUGIN_DISABLED", List.of());
        }
        if (circuitBreakerFailures.getOrDefault(pluginPackage.pluginVersionId(), 0) >= runtimePolicy.circuitBreakerFailureThreshold()) {
            return degraded(runtimePolicy, "PLUGIN_CIRCUIT_OPEN", List.of());
        }
        PluginAdapter adapter = adapterRegistry.find(pluginPackage, invocation.extensionPoint()).orElse(null);
        if (adapter == null) {
            return degraded(runtimePolicy, "PLUGIN_ADAPTER_NOT_APPROVED", List.of());
        }
        var request = new PluginInvocationRequest(pluginPackage.pluginVersionId(), enablement.configurationVersionId(),
                invocation.churchInstanceId(), invocation.environment(), invocation.extensionPoint(), invocation.requiredSpiVersion(),
                invocation.actorId(), invocation.actorRoles(), pluginPackage.packageName(), invocation.licenseScopes(), invocation.input());
        var executionPolicy = policyEnforcementService.authorize(request, invocation.canonicalPolicySnapshot());
        Map<String, Object> stableInput = stableMap(executionPolicy.inputView());
        String inputDigest = digest(Map.of("input", stableInput, "catalogSnapshotId", invocation.catalogSnapshotId(),
                "policySnapshotId", invocation.policySnapshotId(), "seed", invocation.deterministicSeed()));
        Callable<PluginAdapterOutput> callable = () -> adapter.execute(new PluginInvocationEnvelope(pluginPackage.pluginVersionId(),
                enablement.configurationVersionId(), pluginPackage.stablePluginId(), pluginPackage.semanticVersion(), invocation.extensionPoint(),
                stableInput, invocation.catalogSnapshotId(), invocation.policySnapshotId(), invocation.deterministicSeed()));
        Instant start = Instant.now();
        try {
            PluginAdapterOutput adapterOutput = callWithTimeout(callable, runtimePolicy.timeout());
            validate(adapterOutput, runtimePolicy);
            PluginOutput output = toPolicyOutput(adapterOutput);
            var sanitized = policyEnforcementService.sanitizeOutput(executionPolicy, output, invocation.canonicalPolicySnapshot());
            String outputDigest = digest(sanitized);
            circuitBreakerFailures.remove(pluginPackage.pluginVersionId());
            var metadata = new PluginExecutionMetadata(pluginPackage.pluginVersionId(), enablement.configurationVersionId(),
                    pluginPackage.stablePluginId(), pluginPackage.semanticVersion(), invocation.extensionPoint(), ExecutionStatus.SUCCEEDED,
                    null, Duration.between(start, Instant.now()).toMillis(), inputDigest, outputDigest, 1);
            auditRecorder.record("PLUGIN_EXECUTION_SUCCEEDED", pluginPackage.pluginVersionId(), invocation.actorId());
            return new PluginRuntimeResult(ExecutionStatus.SUCCEEDED, List.of(sanitized), List.of(metadata), List.of(), false);
        } catch (RuntimeException ex) {
            circuitBreakerFailures.merge(pluginPackage.pluginVersionId(), 1, Integer::sum);
            var metadata = new PluginExecutionMetadata(pluginPackage.pluginVersionId(), enablement.configurationVersionId(),
                    pluginPackage.stablePluginId(), pluginPackage.semanticVersion(), invocation.extensionPoint(), status(runtimePolicy),
                    safeCode(ex), Duration.between(start, Instant.now()).toMillis(), inputDigest, null, 1);
            auditRecorder.record("PLUGIN_EXECUTION_DEGRADED", pluginPackage.pluginVersionId(), invocation.actorId());
            return degraded(runtimePolicy, safeCode(ex), List.of(metadata));
        }
    }

    private PluginAdapterOutput callWithTimeout(Callable<PluginAdapterOutput> callable, Duration timeout) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PluginAdapterOutput> future = executor.submit(callable);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new PluginRuntimeException("PLUGIN_TIMEOUT");
        } catch (Exception ex) {
            throw new PluginRuntimeException("PLUGIN_EXCEPTION");
        } finally {
            executor.shutdownNow();
        }
    }

    private void validate(PluginAdapterOutput output, ExtensionPointRuntimePolicy policy) {
        if (output == null || hasDuplicates(output.songIds()) || hasDuplicates(output.recommendableSongIds())) {
            throw new PluginRuntimeException("PLUGIN_OUTPUT_INVALID");
        }
        if (policy.recommendationPath() && output.attributes() != null
                && (output.attributes().containsKey("generatedAt") || output.attributes().containsKey("wallClockMillis"))) {
            throw new PluginRuntimeException("PLUGIN_NON_DETERMINISTIC_OUTPUT");
        }
    }

    private PluginRuntimeResult degraded(ExtensionPointRuntimePolicy policy, String code, List<PluginExecutionMetadata> metadata) {
        ExecutionStatus status = status(policy);
        return new PluginRuntimeResult(status, List.of(), metadata, List.of(code), true);
    }

    private ExecutionStatus status(ExtensionPointRuntimePolicy policy) {
        if (policy == null) {
            return ExecutionStatus.FAILED_CLOSED;
        }
        return switch (policy.failureAction()) {
            case FAIL_CLOSED -> ExecutionStatus.FAILED_CLOSED;
            case SKIP_PLUGIN_CONTINUE -> ExecutionStatus.DEGRADED;
            case RETRY_ASYNCHRONOUSLY -> ExecutionStatus.RETRY_SCHEDULED;
            case MARK_JOB_FAILED -> ExecutionStatus.JOB_FAILED;
            case REQUIRE_ADMIN_INTERVENTION -> ExecutionStatus.ADMIN_INTERVENTION_REQUIRED;
        };
    }

    private static boolean hasDuplicates(List<String> values) {
        return values != null && values.stream().filter(Objects::nonNull).distinct().count() != values.size();
    }

    private static String safeCode(RuntimeException ex) {
        return ex instanceof PluginRuntimeException runtimeException ? runtimeException.safeCode() : "PLUGIN_EXCEPTION";
    }

    private PluginOutput toPolicyOutput(PluginAdapterOutput output) {
        return new PluginOutput(output.songIds(), output.recommendableSongIds(), output.arrangementIds(), output.assetIds(),
                output.peopleIds(), output.servicePlanIds(), output.reviewNoteIds(), output.licenseScopes(), output.instanceIds(),
                output.attributes());
    }

    private Map<String, Object> stableMap(Map<String, Object> source) {
        Map<String, Object> sorted = new TreeMap<>();
        source.forEach((key, value) -> sorted.put(key, stableValue(value)));
        return new LinkedHashMap<>(sorted);
    }

    private Object stableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nestedValue) -> sorted.put(String.valueOf(key), stableValue(nestedValue)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stableValue).sorted((left, right) -> String.valueOf(left).compareTo(String.valueOf(right))).toList();
        }
        return value;
    }

    private String digest(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to digest plugin payload", ex);
        }
    }

    private static final class PluginRuntimeException extends RuntimeException {
        private final String safeCode;

        private PluginRuntimeException(String safeCode) {
            super("Plugin runtime failure");
            this.safeCode = safeCode;
        }

        private String safeCode() {
            return safeCode;
        }
    }
}
