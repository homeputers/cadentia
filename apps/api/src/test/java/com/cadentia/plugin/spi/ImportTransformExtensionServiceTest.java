package com.cadentia.plugin.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.policy.PluginPolicyModels.CanonicalPolicySnapshot;
import com.cadentia.plugin.policy.PluginPolicyModels.SanitizedPluginOutput;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExecutionStatus;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginExecutionMetadata;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeResult;
import com.cadentia.plugin.runtime.PluginRuntimeGateway;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.ImportConnectorCandidateV1;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.JobStatus;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.MetadataTransformChangeSet;
import com.cadentia.songimport.CandidateValidationResult;
import com.cadentia.songimport.ConnectorExecutionContext;
import com.cadentia.songimport.NormalizedImportCandidate;
import com.cadentia.songimport.SharedImportPipeline;
import com.cadentia.songimport.StagedImportCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportTransformExtensionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-18T00:00:00Z");
    private static final UUID PLUGIN_VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private FakeRuntimeGateway runtimeService;
    private PluginRegistryAuditRecorder auditRecorder;
    private ImportTransformExtensionService service;

    @BeforeEach
    void setUp() {
        runtimeService = new FakeRuntimeGateway();
        auditRecorder = new PluginRegistryAuditRecorder();
        service = new ImportTransformExtensionService(runtimeService, new StagingPipeline(), auditRecorder, new ObjectMapper());
    }

    @Test
    void stagesValidPluginImportCandidatesThroughSharedImportPipelineOnly() {
        // Given
        runtimeService.result = success(Map.of(
                "stagedImportCandidates", List.of(candidate("song-1", List.of("ext-1"), List.of("license-ref"),
                        List.of("prov-ref"), Map.of()))));

        // When
        var result = service.runImportConnector(invocation("IMPORT_CONNECTOR"), context());

        // Then
        assertThat(result.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(result.stagedCandidates())
                .extracting(StagedImportCandidate::status)
                .containsExactly(ImportCandidateStatus.STAGED);
        assertThat(auditRecorder.events())
                .extracting(PluginRegistryAuditRecorder.PluginRegistryAuditEvent::action)
                .contains("PLUGIN_IMPORT_CANDIDATES_STAGED");
    }

    @Test
    void rejectsDuplicateMissingProvenanceUnauthorizedInstanceAndInvalidLicensingImportCandidates() {
        // Given
        runtimeService.result = success(Map.of(
                "stagedImportCandidates", List.of(candidate("song-1", List.of("dup", "dup"), List.of(), List.of(),
                        Map.of("churchInstanceId", "other")))));

        // When
        var result = service.runImportConnector(invocation("IMPORT_CONNECTOR"), context());

        // Then
        assertThat(result.status()).isEqualTo(JobStatus.DEGRADED);
        assertThat(result.stagedCandidates()).isEmpty();
        assertThat(result.safeErrors()).contains(
                "PLUGIN_IMPORT_PROVENANCE_MISSING",
                "PLUGIN_IMPORT_LICENSE_INVALID",
                "PLUGIN_IMPORT_EXTERNAL_IDENTIFIER_DUPLICATE",
                "PLUGIN_IMPORT_INSTANCE_DATA_UNAUTHORIZED");
    }

    @Test
    void turnsFailedImportRuntimeIntoJobStatusRetryMetadataAndAuditEvent() {
        // Given
        runtimeService.result = new PluginRuntimeResult(
                ExecutionStatus.JOB_FAILED, List.of(), List.of(), List.of("PLUGIN_EXCEPTION"), true);

        // When
        var result = service.runImportConnector(invocation("IMPORT_CONNECTOR"), context());

        // Then
        assertThat(result.status()).isEqualTo(JobStatus.FAILED);
        assertThat(result.retryable()).isTrue();
        assertThat(result.safeErrors()).containsExactly("PLUGIN_EXCEPTION");
        assertThat(auditRecorder.events())
                .extracting(PluginRegistryAuditRecorder.PluginRegistryAuditEvent::action)
                .contains("PLUGIN_IMPORT_JOB_FAILED");
    }

    @Test
    void proposesMetadataTransformChangesetsForReviewWithoutDirectApprovalMutation() {
        // Given
        runtimeService.result = success(Map.of(
                "metadataChangeSets", List.of(new MetadataTransformChangeSet(null, "candidate-1", true,
                        Map.of("normalizedTitle", "Song"), List.of("theme:praise"), List.of("normalized"), null))));

        // When
        var result = service.runMetadataTransform(invocation("METADATA_TRANSFORM"));

        // Then
        assertThat(result.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(result.changeSets()).hasSize(1);
        assertThat(result.changeSets().get(0).reviewStatus()).isEqualTo(ImportCandidateStatus.DEDUPLICATION_REVIEW);
        assertThat(result.changeSets().get(0).changeSetId()).isNotNull();
    }

    @Test
    void rejectsInvalidTagMappingsAndAttemptsToApproveOrRecommendContent() {
        // Given
        runtimeService.result = success(Map.of(
                "metadataChangeSets", List.of(new MetadataTransformChangeSet(null, "candidate-1", true,
                        Map.of("approved", "true", "recommendable", "true"), List.of("freeform:new-tag"), List.of(), null))));

        // When
        var result = service.runMetadataTransform(invocation("METADATA_TRANSFORM"));

        // Then
        assertThat(result.status()).isEqualTo(JobStatus.DEGRADED);
        assertThat(result.changeSets()).isEmpty();
        assertThat(result.safeErrors()).contains(
                "PLUGIN_TRANSFORM_RECOMMENDATION_OR_APPROVAL_ATTEMPT",
                "PLUGIN_TRANSFORM_TAG_MAPPING_INVALID");
    }

    private PluginRuntimeResult success(Map<String, Object> attributes) {
        var output = new SanitizedPluginOutput(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of("river"), attributes, List.of());
        var metadata = new PluginExecutionMetadata(PLUGIN_VERSION_ID, UUID.randomUUID(), "stable", "1.0.0", "IMPORT_CONNECTOR",
                ExecutionStatus.SUCCEEDED, null, 1, "in", "out", 1);
        return new PluginRuntimeResult(ExecutionStatus.SUCCEEDED, List.of(output), List.of(metadata), List.of(), false);
    }

    private ImportConnectorCandidateV1 candidate(String sourceRecordId, List<String> externalIdentifiers,
            List<String> licensingReferences, List<String> provenanceReferences, Map<String, String> safeMetadata) {
        return new ImportConnectorCandidateV1("connector", "provider", ImportMethod.API_IMPORT, sourceRecordId,
                "https://example.test/song/" + sourceRecordId, LicenseType.CCLI, NOW, "raw", "norm", "Song", "song",
                "Artist", null, "{}", safeMetadata, externalIdentifiers, licensingReferences, provenanceReferences);
    }

    private ConnectorExecutionContext context() {
        return new ConnectorExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "actor", NOW);
    }

    private PluginRuntimeInvocation invocation(String extensionPoint) {
        return new PluginRuntimeInvocation("river", Environment.PRODUCTION, extensionPoint, "1.0.0", "actor",
                Set.of("CATALOG_ADMIN"), Set.of("CCLI"), Map.of(), new CanonicalPolicySnapshot(Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of("CCLI"), Set.of("CATALOG_ADMIN"), Set.of(), Set.of("river"),
                Map.of()), "catalog", "policy", 1L, false, null);
    }

    private static class StagingPipeline implements SharedImportPipeline {
        @Override
        public CandidateValidationResult validate(ConnectorExecutionContext context, NormalizedImportCandidate candidate) {
            return CandidateValidationResult.valid(candidate);
        }

        @Override
        public StagedImportCandidate stage(ConnectorExecutionContext context, NormalizedImportCandidate candidate) {
            return new StagedImportCandidate(context.importBatchId(), context.runId(), candidate.sourceRecordId(),
                    candidate.normalizedTitle(), ImportCandidateStatus.STAGED);
        }
    }

    private static class FakeRuntimeGateway implements PluginRuntimeGateway {
        private PluginRuntimeResult result;

        @Override
        public PluginRuntimeResult execute(PluginRuntimeInvocation invocation) {
            return result;
        }
    }

}

