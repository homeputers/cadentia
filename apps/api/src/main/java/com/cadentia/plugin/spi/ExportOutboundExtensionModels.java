package com.cadentia.plugin.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExportOutboundExtensionModels {
    private ExportOutboundExtensionModels() {
    }

    public enum JobStatus {
        SUCCEEDED,
        DEGRADED,
        FAILED,
        RETRY_SCHEDULED,
        DUPLICATE_SUPPRESSED
    }

    public enum SourceType {
        SETLIST,
        SERVICE_PLAN,
        ASSET,
        CATALOG_SNAPSHOT
    }

    public enum IntegrationFailureKind {
        NONE,
        RETRYABLE,
        NON_RETRYABLE
    }

    public record ExportRequest(
            String sourceSnapshotId,
            SourceType sourceType,
            String requestedFormat,
            String exportPurpose,
            Instant generationTimestamp,
            Map<String, String> options,
            List<String> authorizedSongIds,
            List<String> authorizedAssetIds,
            List<String> authorizedReviewNoteIds,
            List<String> licenseScopes) {
    }

    public record ExportArtifact(
            UUID artifactId,
            String sourceSnapshotId,
            SourceType sourceType,
            String artifactRef,
            String mimeType,
            String filename,
            String checksumSha256,
            String pluginId,
            String pluginVersion,
            UUID pluginVersionId,
            UUID configurationVersionId,
            Instant generatedAt,
            String actorContext,
            Map<String, String> provenanceMetadata,
            List<String> visibleSongIds,
            List<String> visibleAssetIds,
            List<String> visibleReviewNoteIds,
            List<String> licenseScopes,
            boolean revocable) {
    }

    public record ExportJobResult(JobStatus status, ExportArtifact artifact, List<String> safeErrors, List<String> warnings) {
    }

    public record OutboundPublishRequest(
            String sourceSnapshotId,
            SourceType sourceType,
            String eventSchemaId,
            String idempotencyKey,
            String correlationId,
            String credentialRef,
            RetryPolicy retryPolicy,
            Map<String, Object> redactedPayload) {
    }

    public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
    }

    public record IntegrationAttempt(
            UUID attemptId,
            String sourceSnapshotId,
            SourceType sourceType,
            String eventSchemaId,
            String idempotencyKey,
            String correlationId,
            String credentialRef,
            String externalReference,
            String reconciliationStatus,
            String pluginId,
            String pluginVersion,
            UUID pluginVersionId,
            UUID configurationVersionId,
            Instant attemptedAt,
            int attemptNumber,
            IntegrationFailureKind failureKind,
            List<String> safeErrors) {
    }

    public record OutboundPublishResult(JobStatus status, IntegrationAttempt attempt) {
    }
}
