package com.cadentia.plugin.runtime;

import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.policy.PluginPolicyModels.CanonicalPolicySnapshot;
import com.cadentia.plugin.policy.PluginPolicyModels.SanitizedPluginOutput;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PluginRuntimeModels {
    private PluginRuntimeModels() {
    }

    public enum FailureAction {
        FAIL_CLOSED,
        SKIP_PLUGIN_CONTINUE,
        RETRY_ASYNCHRONOUSLY,
        MARK_JOB_FAILED,
        REQUIRE_ADMIN_INTERVENTION
    }

    public enum ExecutionStatus {
        SUCCEEDED,
        DEGRADED,
        FAILED_CLOSED,
        RETRY_SCHEDULED,
        JOB_FAILED,
        ADMIN_INTERVENTION_REQUIRED
    }

    public record ExtensionPointRuntimePolicy(
            String extensionPoint,
            Duration timeout,
            int maxAttempts,
            int circuitBreakerFailureThreshold,
            FailureAction failureAction,
            boolean recommendationPath,
            boolean fallbackToCoreDefaults) {
    }

    public record PluginRuntimeInvocation(
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            String requiredSpiVersion,
            String actorId,
            Set<String> actorRoles,
            Set<String> licenseScopes,
            Map<String, Object> input,
            CanonicalPolicySnapshot canonicalPolicySnapshot,
            String catalogSnapshotId,
            String policySnapshotId,
            Long deterministicSeed,
            boolean coreTransactionActive,
            String idempotencyKey) {
    }

    public record PluginInvocationEnvelope(
            UUID pluginVersionId,
            UUID configurationVersionId,
            String stablePluginId,
            String semanticVersion,
            String extensionPoint,
            Map<String, Object> input,
            String catalogSnapshotId,
            String policySnapshotId,
            Long deterministicSeed) {
    }

    public record PluginAdapterOutput(
            List<String> songIds,
            List<String> recommendableSongIds,
            List<String> arrangementIds,
            List<String> assetIds,
            List<String> peopleIds,
            List<String> servicePlanIds,
            List<String> reviewNoteIds,
            List<String> licenseScopes,
            List<String> instanceIds,
            Map<String, Object> attributes) {
    }

    public record PluginExecutionMetadata(
            UUID pluginVersionId,
            UUID configurationVersionId,
            String stablePluginId,
            String semanticVersion,
            String extensionPoint,
            ExecutionStatus status,
            String safeCode,
            long durationMillis,
            String inputDigest,
            String outputDigest,
            int attempt) {
    }

    public record PluginRuntimeResult(
            ExecutionStatus status,
            List<SanitizedPluginOutput> outputs,
            List<PluginExecutionMetadata> metadata,
            List<String> safeErrors,
            boolean degraded) {
    }
}
