package com.cadentia.plugin.spi;

import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.songimport.StagedImportCandidate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ImportTransformExtensionModels {
    private ImportTransformExtensionModels() {
    }

    public enum JobStatus {
        SUCCEEDED,
        DEGRADED,
        FAILED
    }

    public record ImportConnectorCandidateV1(
            String connectorId,
            String providerName,
            ImportMethod importMethod,
            String sourceRecordId,
            String sourceReference,
            LicenseType licenseType,
            Instant retrievedAt,
            String rawContentHash,
            String normalizedContentHash,
            String rawTitle,
            String normalizedTitle,
            String sourceArtistName,
            String ccliNumber,
            String sourcePayloadJson,
            Map<String, String> safeMetadata,
            List<String> externalIdentifiers,
            List<String> licensingReferences,
            List<String> provenanceReferences) {
    }

    public record ImportConnectorJobResult(
            JobStatus status,
            List<StagedImportCandidate> stagedCandidates,
            List<String> safeErrors,
            boolean retryable,
            Instant retryAfter) {
    }

    public record MetadataTransformChangeSet(
            UUID changeSetId,
            String targetId,
            boolean canonicalEligible,
            Map<String, String> proposedMetadata,
            List<String> tagMappings,
            List<String> reviewNotes,
            ImportCandidateStatus reviewStatus) {
    }

    public record MetadataTransformJobResult(
            JobStatus status,
            List<MetadataTransformChangeSet> changeSets,
            List<String> safeErrors,
            boolean retryable,
            Instant retryAfter) {
    }
}
