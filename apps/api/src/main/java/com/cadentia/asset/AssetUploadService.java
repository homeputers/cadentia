package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetUploadErrorCode.ASSET_TYPE_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.BYTE_SIZE_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.CHECKSUM_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.EXPIRED_UPLOAD;
import static com.cadentia.asset.AssetUploadErrorCode.INSTANCE_SCOPE_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.MIME_TYPE_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.MIME_TYPE_NOT_ALLOWED;
import static com.cadentia.asset.AssetUploadErrorCode.OBJECT_NOT_FOUND;
import static com.cadentia.asset.AssetUploadErrorCode.OBJECT_TOO_LARGE;
import static com.cadentia.asset.AssetUploadErrorCode.STORAGE_FAILURE;
import static com.cadentia.asset.AssetUploadErrorCode.STORAGE_KEY_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.UNAUTHORIZED_ACTOR;
import static com.cadentia.asset.AssetUploadErrorCode.UPLOAD_ALREADY_COMPLETED;
import static com.cadentia.asset.AssetUploadErrorCode.UPLOAD_NOT_FOUND;
import static com.cadentia.asset.AssetUploadModels.PendingUploadStatus.EXPIRED;
import static com.cadentia.asset.AssetUploadModels.PendingUploadStatus.FAILED;
import static com.cadentia.asset.AssetUploadModels.PendingUploadStatus.FINALIZED;
import static com.cadentia.asset.AssetUploadModels.PendingUploadStatus.REJECTED;

import com.cadentia.asset.AssetModels.AssetRecord;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import com.cadentia.asset.AssetUploadModels.CleanupCandidate;
import com.cadentia.asset.AssetUploadModels.CleanupPlan;
import com.cadentia.asset.AssetUploadModels.CreatePendingUploadCommand;
import com.cadentia.asset.AssetUploadModels.FinalizeUploadCommand;
import com.cadentia.asset.AssetUploadModels.PendingUploadRecord;
import com.cadentia.asset.AssetUploadModels.PendingUploadStatus;
import com.cadentia.asset.AssetUploadModels.UploadInstructions;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetUploadService {

    private final AssetRepository assetRepository;
    private final PendingAssetUploadRepository pendingUploadRepository;
    private final AssetStorageAdapter storageAdapter;
    private final AssetStorageProperties storageProperties;
    private final Clock clock;

    public AssetUploadService(
            AssetRepository assetRepository,
            PendingAssetUploadRepository pendingUploadRepository,
            AssetStorageAdapter storageAdapter,
            AssetStorageProperties storageProperties) {
        this(assetRepository, pendingUploadRepository, storageAdapter, storageProperties, Clock.systemUTC());
    }

    AssetUploadService(
            AssetRepository assetRepository,
            PendingAssetUploadRepository pendingUploadRepository,
            AssetStorageAdapter storageAdapter,
            AssetStorageProperties storageProperties,
            Clock clock) {
        this.assetRepository = assetRepository;
        this.pendingUploadRepository = pendingUploadRepository;
        this.storageAdapter = storageAdapter;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    public UploadInstructions createPendingUpload(CreatePendingUploadCommand command) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(storageProperties.getPendingUploadTtl());
        UUID uploadId = UUID.randomUUID();
        String storageKey = processingStorageKey(command, uploadId);
        PendingUploadRecord pendingUpload = new PendingUploadRecord(
                uploadId,
                command.instanceId(),
                command.assetId(),
                command.assetTypeCode(),
                command.versionNumber(),
                command.revisionCode(),
                storageKey,
                StoredObjectMetadata.normalizeAlgorithm(command.expectedChecksumAlgorithm()),
                command.expectedChecksumValue(),
                command.expectedMimeType(),
                command.expectedByteSize(),
                command.sourceUri(),
                command.provenanceSummary(),
                command.actor(),
                command.accessPolicyCode(),
                command.licenseMetadata() == null
                        ? AssetUploadModels.defaultLicense(command.accessPolicyCode())
                        : command.licenseMetadata(),
                PendingUploadStatus.PENDING,
                null,
                now,
                expiresAt,
                null);
        pendingUploadRepository.save(pendingUpload);
        var signedUrl = storageAdapter.signedUploadUrl(
                storageKey,
                command.expectedMimeType(),
                command.expectedByteSize(),
                storageProperties.getSignedUploadUrlTtl());
        return new UploadInstructions(
                uploadId,
                storageKey,
                signedUrl.method(),
                signedUrl.url().toString(),
                signedUrl.expiresAt(),
                pendingUpload.expectedChecksumAlgorithm(),
                pendingUpload.expectedChecksumValue(),
                pendingUpload.expectedMimeType(),
                pendingUpload.expectedByteSize());
    }

    public AssetVersionRecord finalizeUpload(FinalizeUploadCommand command) {
        PendingUploadRecord pendingUpload = pendingUploadRepository.find(command.uploadId())
                .orElseThrow(() -> new AssetUploadException(UPLOAD_NOT_FOUND, "Pending upload not found"));
        try {
            verifyPendingUploadCommand(command, pendingUpload);
            StoredObjectMetadata objectMetadata = storageAdapter.metadata(command.storageKey())
                    .orElseThrow(() -> new AssetUploadException(OBJECT_NOT_FOUND, "Uploaded object was not found"));
            verifyObjectMetadata(pendingUpload, objectMetadata);
            AssetRecord asset = assetRepository.findAsset(pendingUpload.assetId())
                    .orElseThrow(() -> new AssetUploadException(UPLOAD_NOT_FOUND, "Target asset was not found"));
            if (asset.assetTypeCode() != pendingUpload.assetTypeCode()) {
                throw new AssetUploadException(ASSET_TYPE_MISMATCH, "Pending upload asset type does not match target asset");
            }
            String availableStorageKey = availableStorageKey(pendingUpload);
            storageAdapter.move(pendingUpload.storageKey(), availableStorageKey);
            AssetVersionRecord version = assetRepository.createVersion(new CreateAssetVersionCommand(
                    pendingUpload.assetId(),
                    pendingUpload.versionNumber(),
                    pendingUpload.revisionCode(),
                    storageAdapter.providerCode(),
                    null,
                    storageProperties.bucketAlias(),
                    availableStorageKey,
                    pendingUpload.expectedChecksumAlgorithm(),
                    pendingUpload.expectedChecksumValue(),
                    objectMetadata.mimeType(),
                    objectMetadata.byteSize(),
                    pendingUpload.sourceUri(),
                    pendingUpload.provenanceSummary(),
                    pendingUpload.actor(),
                    AVAILABLE,
                    READY,
                    pendingUpload.accessPolicyCode(),
                    pendingUpload.licenseMetadata(),
                    true));
            pendingUploadRepository.updateStatus(pendingUpload.id(), FINALIZED, null, clock.instant());
            return version;
        } catch (AssetUploadException ex) {
            markRejectedOrFailed(pendingUpload, ex.errorCode());
            throw ex;
        } catch (AssetStorageException ex) {
            pendingUploadRepository.updateStatus(pendingUpload.id(), FAILED, STORAGE_FAILURE.name(), clock.instant());
            throw new AssetUploadException(STORAGE_FAILURE, "Storage adapter failed during upload finalization", ex);
        }
    }

    public CleanupPlan cleanupPlan() {
        Instant now = clock.instant();
        return new CleanupPlan(pendingUploadRepository.cleanupCandidates(now).stream()
                .map(record -> new CleanupCandidate(record.id(), record.storageKey(), record.status(), now))
                .toList());
    }

    public void cleanupPendingObject(UUID uploadId) {
        PendingUploadRecord pendingUpload = pendingUploadRepository.find(uploadId)
                .orElseThrow(() -> new AssetUploadException(UPLOAD_NOT_FOUND, "Pending upload not found"));
        if (pendingUpload.status() == FINALIZED) {
            return;
        }
        storageAdapter.delete(pendingUpload.storageKey());
    }

    private void verifyPendingUploadCommand(FinalizeUploadCommand command, PendingUploadRecord pendingUpload) {
        if (pendingUpload.isTerminal()) {
            throw new AssetUploadException(UPLOAD_ALREADY_COMPLETED, "Pending upload is already completed");
        }
        if (!pendingUpload.instanceId().equals(command.instanceId())) {
            throw new AssetUploadException(INSTANCE_SCOPE_MISMATCH, "Pending upload belongs to a different instance");
        }
        if (!pendingUpload.actor().equals(command.actor())) {
            throw new AssetUploadException(UNAUTHORIZED_ACTOR, "Only the actor that created the pending upload may finalize it");
        }
        if (pendingUpload.expiresAt().isBefore(clock.instant())) {
            pendingUploadRepository.updateStatus(pendingUpload.id(), EXPIRED, EXPIRED_UPLOAD.name(), clock.instant());
            throw new AssetUploadException(EXPIRED_UPLOAD, "Pending upload has expired");
        }
        if (!pendingUpload.storageKey().equals(command.storageKey())) {
            throw new AssetUploadException(STORAGE_KEY_MISMATCH, "Finalization storage key does not match pending upload");
        }
    }

    private void verifyObjectMetadata(PendingUploadRecord pendingUpload, StoredObjectMetadata objectMetadata) {
        if (objectMetadata.byteSize() != pendingUpload.expectedByteSize()) {
            throw new AssetUploadException(BYTE_SIZE_MISMATCH, "Uploaded object byte size does not match expected size");
        }
        if (objectMetadata.byteSize() > storageProperties.getMaximumObjectSizeBytes()) {
            throw new AssetUploadException(OBJECT_TOO_LARGE, "Uploaded object exceeds maximum configured size");
        }
        if (!objectMetadata.mimeType().equals(pendingUpload.expectedMimeType())) {
            throw new AssetUploadException(MIME_TYPE_MISMATCH, "Uploaded object MIME type does not match expected MIME type");
        }
        if (!storageProperties.isAllowedMimeType(pendingUpload.assetTypeCode(), objectMetadata.mimeType())) {
            throw new AssetUploadException(MIME_TYPE_NOT_ALLOWED, "MIME type is not allowed for asset type");
        }
        String actualDigest = objectMetadata.digest(pendingUpload.expectedChecksumAlgorithm())
                .orElseThrow(() -> new AssetUploadException(CHECKSUM_MISMATCH, "Uploaded object digest is unavailable"));
        if (!actualDigest.equalsIgnoreCase(pendingUpload.expectedChecksumValue())) {
            throw new AssetUploadException(CHECKSUM_MISMATCH, "Uploaded object checksum does not match expected checksum");
        }
    }

    private void markRejectedOrFailed(PendingUploadRecord pendingUpload, AssetUploadErrorCode errorCode) {
        if (pendingUpload.isTerminal() || errorCode == EXPIRED_UPLOAD) {
            return;
        }
        PendingUploadStatus status = errorCode == STORAGE_FAILURE ? FAILED : REJECTED;
        pendingUploadRepository.updateStatus(pendingUpload.id(), status, errorCode.name(), clock.instant());
        try {
            if (storageAdapter.exists(pendingUpload.storageKey())) {
                storageAdapter.quarantine(pendingUpload.storageKey(), errorCode.name());
            }
        } catch (AssetStorageException ex) {
            pendingUploadRepository.updateStatus(pendingUpload.id(), FAILED, STORAGE_FAILURE.name(), clock.instant());
        }
    }

    private String processingStorageKey(CreatePendingUploadCommand command, UUID uploadId) {
        return storageProperties.getNamespace()
                + "/" + storageProperties.getProcessingPrefix()
                + "/" + command.assetId()
                + "/" + uploadId;
    }

    private String availableStorageKey(PendingUploadRecord pendingUpload) {
        return storageProperties.getNamespace()
                + "/" + storageProperties.getAvailablePrefix()
                + "/" + pendingUpload.assetId()
                + "/v" + pendingUpload.versionNumber()
                + "/" + pendingUpload.id();
    }
}
