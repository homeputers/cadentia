package com.cadentia.plugin.spi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpiContracts {
    public static final String VERSION = "1.0.0";

    private SpiContracts() {
    }

    public enum ExtensionPoint {
        IMPORT_CONNECTOR,
        METADATA_TRANSFORM,
        RECOMMENDATION_CONSTRAINT,
        SCORING_CONTRIBUTION,
        EXPORT_RENDERER,
        OUTBOUND_PUBLISH_HOOK
    }

    public enum Status {
        SUCCESS,
        DEGRADED,
        FAILURE
    }

    public enum Environment {
        DEVELOPMENT,
        STAGING,
        PRODUCTION
    }

    public record SpiEnvelope(
            String spiVersion,
            ExtensionPoint extensionPoint,
            UUID executionId,
            String correlationId,
            String churchInstanceId,
            Environment environment,
            UUID pluginVersionId,
            UUID configurationVersionId,
            String registrySnapshotId,
            String policySnapshotId,
            String catalogSnapshotId,
            String actorContext,
            String systemContext,
            String locale,
            String deterministicSeed,
            String runId) {
    }

    public record SafeError(String code, String message, boolean retryable, String retryAfter) {
    }

    public record ImportConnectorInput(SpiEnvelope envelope, String sourcePointer, String legalMode,
            List<String> secretRefs, Map<String, String> options) {
    }

    public record ImportConnectorOutput(SpiEnvelope envelope, Status status, List<ImportDocument> documents,
            List<SafeError> errors, List<String> warnings) {
    }

    public record ImportDocument(String sourceId, String title, String contentHashSha256, String provenanceRef,
            String licenseClaim, String stagedCandidateId) {
    }

    public record MetadataTransformInput(SpiEnvelope envelope, String stagedCandidateId, String candidateSnapshotId,
            String controlledVocabularyVersion, Map<String, String> metadata) {
    }

    public record MetadataTransformOutput(SpiEnvelope envelope, Status status, Map<String, String> proposedMetadata,
            double confidence, List<SafeError> errors, List<String> reviewNotes) {
    }

    public record RecommendationConstraintInput(SpiEnvelope envelope, String requestSnapshotId,
            List<String> allowedConstraintCodes, List<String> candidateIds) {
    }

    public record RecommendationConstraintOutput(SpiEnvelope envelope, Status status, List<ConstraintDeclaration> constraints,
            List<SafeError> errors) {
    }

    public record ConstraintDeclaration(String code, String type, double weight, String explanationLabel) {
    }

    public record ScoringContributionInput(SpiEnvelope envelope, String scoringProfileVersion,
            List<String> allowedComponentCodes, List<String> candidateIds) {
    }

    public record ScoringContributionOutput(SpiEnvelope envelope, Status status, List<ScoreAdjustment> adjustments,
            List<SafeError> errors) {
    }

    public record ScoreAdjustment(String candidateId, String componentCode, double delta, String reasonCode) {
    }

    public record ExportRendererInput(SpiEnvelope envelope, String servicePlanSnapshotId, String requestedFormat,
            Map<String, String> exportOptions) {
    }

    public record ExportRendererOutput(SpiEnvelope envelope, Status status, String mimeType, String filename,
            String checksumSha256, String artifactRef, List<SafeError> errors, List<String> warnings) {
    }

    public record OutboundPublishHookInput(SpiEnvelope envelope, String eventSchemaId, String idempotencyKey,
            Map<String, Object> redactedEvent) {
    }

    public record OutboundPublishHookOutput(SpiEnvelope envelope, Status status, String externalReference,
            String reconciliationStatus, List<SafeError> errors) {
    }
}
