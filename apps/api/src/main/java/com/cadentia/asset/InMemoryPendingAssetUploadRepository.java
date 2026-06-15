package com.cadentia.asset;

import com.cadentia.asset.AssetUploadModels.PendingUploadRecord;
import com.cadentia.asset.AssetUploadModels.PendingUploadStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPendingAssetUploadRepository implements PendingAssetUploadRepository {

    private final ConcurrentHashMap<UUID, PendingUploadRecord> records = new ConcurrentHashMap<>();

    @Override
    public PendingUploadRecord save(PendingUploadRecord record) {
        records.put(record.id(), record);
        return record;
    }

    @Override
    public Optional<PendingUploadRecord> find(UUID uploadId) {
        return Optional.ofNullable(records.get(uploadId));
    }

    @Override
    public PendingUploadRecord updateStatus(UUID uploadId, PendingUploadStatus status, String reasonCode, Instant completedAt) {
        return records.compute(uploadId, (id, existing) -> {
            if (existing == null) {
                throw new AssetUploadException(AssetUploadErrorCode.UPLOAD_NOT_FOUND, "Pending upload not found");
            }
            return new PendingUploadRecord(
                    existing.id(),
                    existing.instanceId(),
                    existing.assetId(),
                    existing.assetTypeCode(),
                    existing.versionNumber(),
                    existing.revisionCode(),
                    existing.storageKey(),
                    existing.expectedChecksumAlgorithm(),
                    existing.expectedChecksumValue(),
                    existing.expectedMimeType(),
                    existing.expectedByteSize(),
                    existing.sourceUri(),
                    existing.provenanceSummary(),
                    existing.actor(),
                    existing.accessPolicyCode(),
                    existing.licenseMetadata(),
                    status,
                    reasonCode,
                    existing.createdAt(),
                    existing.expiresAt(),
                    completedAt);
        });
    }

    @Override
    public List<PendingUploadRecord> cleanupCandidates(Instant now) {
        return records.values().stream()
                .filter(record -> record.status() == PendingUploadStatus.PENDING && record.expiresAt().isBefore(now)
                        || record.status() == PendingUploadStatus.REJECTED
                        || record.status() == PendingUploadStatus.FAILED
                        || record.status() == PendingUploadStatus.EXPIRED)
                .sorted(Comparator.comparing(PendingUploadRecord::createdAt))
                .toList();
    }
}
