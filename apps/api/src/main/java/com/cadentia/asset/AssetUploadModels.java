package com.cadentia.asset;

import com.cadentia.asset.AssetModels.AssetAccessPolicyCode;
import com.cadentia.asset.AssetModels.AssetLicenseStatusCode;
import com.cadentia.asset.AssetModels.AssetTypeCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssetUploadModels {

    private AssetUploadModels() {
    }

    public enum PendingUploadStatus {
        PENDING,
        FINALIZED,
        REJECTED,
        FAILED,
        EXPIRED
    }

    public record CreatePendingUploadCommand(
            String instanceId,
            UUID assetId,
            AssetTypeCode assetTypeCode,
            int versionNumber,
            String revisionCode,
            String expectedChecksumAlgorithm,
            String expectedChecksumValue,
            String expectedMimeType,
            long expectedByteSize,
            String sourceUri,
            String provenanceSummary,
            String actor,
            AssetAccessPolicyCode accessPolicyCode,
            AssetModels.LicenseMetadata licenseMetadata) {
    }

    public record PendingUploadRecord(
            UUID id,
            String instanceId,
            UUID assetId,
            AssetTypeCode assetTypeCode,
            int versionNumber,
            String revisionCode,
            String storageKey,
            String expectedChecksumAlgorithm,
            String expectedChecksumValue,
            String expectedMimeType,
            long expectedByteSize,
            String sourceUri,
            String provenanceSummary,
            String actor,
            AssetAccessPolicyCode accessPolicyCode,
            AssetModels.LicenseMetadata licenseMetadata,
            PendingUploadStatus status,
            String failureReasonCode,
            Instant createdAt,
            Instant expiresAt,
            Instant completedAt) {

        public boolean isTerminal() {
            return status == PendingUploadStatus.FINALIZED
                    || status == PendingUploadStatus.REJECTED
                    || status == PendingUploadStatus.FAILED
                    || status == PendingUploadStatus.EXPIRED;
        }
    }

    public record UploadInstructions(
            UUID uploadId,
            String storageKey,
            String method,
            String uploadUrl,
            Instant expiresAt,
            String checksumAlgorithm,
            String checksumValue,
            String mimeType,
            long byteSize) {
    }

    public record FinalizeUploadCommand(UUID uploadId, String instanceId, String actor, String storageKey) {
    }

    public record CleanupCandidate(UUID uploadId, String storageKey, PendingUploadStatus status, Instant eligibleAt) {
    }

    public record CleanupPlan(List<CleanupCandidate> candidates) {
        public CleanupPlan {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public static AssetModels.LicenseMetadata defaultLicense(AssetAccessPolicyCode visibilityPolicyCode) {
        return new AssetModels.LicenseMetadata(
                AssetLicenseStatusCode.UNKNOWN,
                "Pending upload metadata",
                null,
                "Access is denied until upload verification and licensing review complete.",
                null,
                null,
                null,
                visibilityPolicyCode);
    }
}
