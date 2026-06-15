package com.cadentia.asset;

import com.cadentia.asset.AssetUploadModels.PendingUploadRecord;
import com.cadentia.asset.AssetUploadModels.PendingUploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingAssetUploadRepository {

    PendingUploadRecord save(PendingUploadRecord record);

    Optional<PendingUploadRecord> find(UUID uploadId);

    PendingUploadRecord updateStatus(UUID uploadId, PendingUploadStatus status, String reasonCode, Instant completedAt);

    List<PendingUploadRecord> cleanupCandidates(Instant now);
}
