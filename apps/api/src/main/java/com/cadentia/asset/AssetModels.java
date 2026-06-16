package com.cadentia.asset;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssetModels {

    private AssetModels() {
    }

    public enum AssetTypeCode {
        PDF,
        CHORD_CHART,
        STEM,
        BACKING_TRACK,
        CLICK_TRACK,
        MIDI_CUE,
        REHEARSAL_RECORDING,
        PREVIEW,
        LOCAL_EXTENSION
    }

    public enum AssetLifecycleStatusCode {
        DRAFT,
        AVAILABLE,
        ARCHIVED,
        QUARANTINED,
        REPLACED,
        DELETED
    }

    public enum AssetProcessingStatusCode {
        NOT_REQUIRED,
        PENDING_SCAN,
        SCANNING,
        PROCESSING,
        READY,
        FAILED,
        REJECTED
    }

    public enum AssetLicenseStatusCode {
        UNKNOWN,
        NOT_REQUIRED,
        CCLI_COVERED,
        DIRECT_PERMISSION,
        PURCHASED,
        EXPIRES,
        RESTRICTED,
        EXPIRED,
        REVOKED
    }

    public enum AssetAttachmentTargetTypeCode {
        SONG,
        ARRANGEMENT,
        SERVICE,
        SERVICE_ITEM,
        REHEARSAL_SESSION,
        REHEARSAL_ISSUE,
        REHEARSAL_ISSUE_ACTION,
        SERVICE_ARRANGEMENT_OVERRIDE
    }

    public enum AssetAttachmentPurposeCode {
        PRIMARY_CHART,
        REFERENCE,
        REHEARSAL,
        PERFORMANCE,
        EVIDENCE,
        FOLLOW_UP,
        LOCAL_OVERRIDE
    }

    public enum AssetAttachmentAuditEventType {
        CREATED,
        REORDERED,
        ARCHIVED,
        REPLACED
    }

    public enum AssetAccessPolicyCode {
        PUBLIC_METADATA,
        CATALOG_REVIEWERS,
        WORSHIP_TEAM,
        SERVICE_PARTICIPANTS,
        ADMINS_ONLY,
        LOCAL_POLICY
    }

    public record ControlledVocabularyRecord(
            String code,
            String displayName,
            boolean active,
            int sortOrder) {
    }

    public record CreateAssetCommand(
            AssetTypeCode assetTypeCode,
            String title,
            String description,
            String ownerActor,
            String owningMinistry,
            AssetAccessPolicyCode defaultAccessPolicyCode,
            AssetLifecycleStatusCode lifecycleStatusCode,
            String createdBy) {
    }

    public record CreateAssetVersionCommand(
            UUID assetId,
            int versionNumber,
            String revisionCode,
            String storageProviderCode,
            String storageRegion,
            String storageBucketAlias,
            String storageKey,
            String checksumAlgorithm,
            String checksumValue,
            String mimeType,
            long byteSize,
            String sourceUri,
            String provenanceSummary,
            String createdBy,
            AssetLifecycleStatusCode lifecycleStatusCode,
            AssetProcessingStatusCode processingStatusCode,
            AssetAccessPolicyCode accessPolicyCode,
            LicenseMetadata licenseMetadata,
            boolean makeCurrent) {
    }

    public record LicenseMetadata(
            AssetLicenseStatusCode licenseStatusCode,
            String licenseSource,
            String licenseReference,
            String usageRestrictions,
            String licenseHolder,
            Instant effectiveAt,
            Instant expiresAt,
            AssetAccessPolicyCode visibilityPolicyCode) {
    }

    public record CreateAssetAttachmentCommand(
            AssetAttachmentTargetTypeCode targetTypeCode,
            UUID targetId,
            UUID servicePlanId,
            UUID assetVersionId,
            AssetTypeCode attachmentTypeCode,
            String displayLabel,
            int sortOrder,
            AssetAttachmentPurposeCode purposeCode,
            boolean requiredForUse,
            Instant effectiveFrom,
            Instant effectiveUntil,
            AssetAccessPolicyCode visibilityPolicyCode,
            String createdBy) {
    }

    public record ReorderAssetAttachmentCommand(
            UUID attachmentId,
            int sortOrder,
            String updatedBy,
            String reason) {
    }

    public record ArchiveAssetAttachmentCommand(
            UUID attachmentId,
            String archivedBy,
            String reason) {
    }

    public record AssetAttachmentRecord(
            UUID id,
            AssetAttachmentTargetTypeCode targetTypeCode,
            UUID targetId,
            UUID servicePlanId,
            UUID assetVersionId,
            AssetTypeCode attachmentTypeCode,
            String displayLabel,
            int sortOrder,
            AssetAttachmentPurposeCode purposeCode,
            boolean requiredForUse,
            Instant effectiveFrom,
            Instant effectiveUntil,
            AssetAccessPolicyCode visibilityPolicyCode,
            Instant archivedAt,
            String archivedBy,
            String archiveReason,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record AssetAttachmentAuditEventRecord(
            UUID id,
            UUID attachmentId,
            AssetAttachmentAuditEventType eventType,
            UUID previousAssetVersionId,
            UUID newAssetVersionId,
            Integer previousSortOrder,
            Integer newSortOrder,
            String reason,
            String changedBy,
            Instant changedAt) {
    }

    public record AssetRecord(
            UUID id,
            UUID stableIdentifier,
            AssetTypeCode assetTypeCode,
            String title,
            String description,
            String ownerActor,
            String owningMinistry,
            AssetAccessPolicyCode defaultAccessPolicyCode,
            AssetLifecycleStatusCode lifecycleStatusCode,
            UUID currentAssetVersionId,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            List<AssetVersionRecord> versions) {
    }

    public record AssetVersionRecord(
            UUID id,
            UUID stableIdentifier,
            UUID assetId,
            int versionNumber,
            String revisionCode,
            String storageProviderCode,
            String storageRegion,
            String storageBucketAlias,
            String storageKey,
            String checksumAlgorithm,
            String checksumValue,
            String mimeType,
            long byteSize,
            String sourceUri,
            String provenanceSummary,
            String createdBy,
            Instant createdAt,
            AssetLifecycleStatusCode lifecycleStatusCode,
            AssetProcessingStatusCode processingStatusCode,
            AssetAccessPolicyCode accessPolicyCode,
            LicenseMetadata licenseMetadata) {
    }
}
