package com.cadentia.asset;

import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetProcessingRepository {

    AssetProcessingJobRecord enqueue(
            UUID assetVersionId,
            AssetProcessingJobType jobType,
            String processorType,
            String processorVersion,
            String inputChecksum,
            int maxAttempts,
            Instant now);

    Optional<AssetProcessingJobRecord> findJob(UUID jobId);

    List<AssetProcessingJobRecord> jobsForVersion(UUID assetVersionId);

    List<AssetProcessingJobRecord> runnableJobs(Instant now);

    AssetProcessingJobRecord markRunning(UUID jobId, Instant now);

    AssetProcessingJobRecord markSucceeded(UUID jobId, UUID resultId, Instant now);

    AssetProcessingJobRecord markFailed(UUID jobId, String errorCode, String sanitizedErrorDetail, Instant retryAt);

    AssetProcessingJobRecord markDeadLettered(UUID jobId, String errorCode, String sanitizedErrorDetail, Instant now);

    AssetProcessingResultRecord saveResult(AssetProcessingResultRecord result);

    List<AssetProcessingResultRecord> resultsForVersion(UUID assetVersionId);

    boolean hasIncompleteRequiredJobs(UUID assetVersionId);

    boolean hasRequiredJobStatus(UUID assetVersionId, AssetProcessingJobStatus status);
}
