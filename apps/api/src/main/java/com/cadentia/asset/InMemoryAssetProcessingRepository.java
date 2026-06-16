package com.cadentia.asset;

import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAssetProcessingRepository implements AssetProcessingRepository {

    private final Map<UUID, AssetProcessingJobRecord> jobs = new LinkedHashMap<>();
    private final Map<UUID, AssetProcessingResultRecord> results = new LinkedHashMap<>();

    @Override
    public synchronized AssetProcessingJobRecord enqueue(
            UUID assetVersionId,
            AssetProcessingJobType jobType,
            String processorType,
            String processorVersion,
            String inputChecksum,
            int maxAttempts,
            Instant now) {
        Optional<AssetProcessingJobRecord> existing = jobs.values().stream()
                .filter(job -> job.assetVersionId().equals(assetVersionId))
                .filter(job -> job.jobType() == jobType)
                .filter(job -> job.processorType().equals(processorType))
                .filter(job -> job.processorVersion().equals(processorVersion))
                .filter(job -> job.inputChecksum().equals(inputChecksum))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        AssetProcessingJobRecord job = new AssetProcessingJobRecord(
                UUID.randomUUID(), assetVersionId, jobType, processorType, processorVersion, inputChecksum,
                AssetProcessingJobStatus.QUEUED, 0, maxAttempts, now, now, null, null, null, null, null);
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public synchronized Optional<AssetProcessingJobRecord> findJob(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public synchronized List<AssetProcessingJobRecord> jobsForVersion(UUID assetVersionId) {
        return jobs.values().stream().filter(job -> job.assetVersionId().equals(assetVersionId)).toList();
    }

    @Override
    public synchronized List<AssetProcessingJobRecord> runnableJobs(Instant now) {
        return jobs.values().stream()
                .filter(job -> job.status() == AssetProcessingJobStatus.QUEUED)
                .filter(job -> !job.availableAt().isAfter(now))
                .sorted(Comparator.comparing(AssetProcessingJobRecord::createdAt))
                .toList();
    }

    @Override
    public synchronized AssetProcessingJobRecord markRunning(UUID jobId, Instant now) {
        AssetProcessingJobRecord job = required(jobId);
        return put(new AssetProcessingJobRecord(job.id(), job.assetVersionId(), job.jobType(), job.processorType(),
                job.processorVersion(), job.inputChecksum(), AssetProcessingJobStatus.RUNNING, job.attempts() + 1,
                job.maxAttempts(), job.createdAt(), job.availableAt(), now, null, null, null, job.outputResultId()));
    }

    @Override
    public synchronized AssetProcessingJobRecord markSucceeded(UUID jobId, UUID resultId, Instant now) {
        AssetProcessingJobRecord job = required(jobId);
        return put(new AssetProcessingJobRecord(job.id(), job.assetVersionId(), job.jobType(), job.processorType(),
                job.processorVersion(), job.inputChecksum(), AssetProcessingJobStatus.SUCCEEDED, job.attempts(),
                job.maxAttempts(), job.createdAt(), job.availableAt(), job.startedAt(), now, null, null, resultId));
    }

    @Override
    public synchronized AssetProcessingJobRecord markFailed(UUID jobId, String errorCode, String sanitizedErrorDetail, Instant retryAt) {
        AssetProcessingJobRecord job = required(jobId);
        return put(new AssetProcessingJobRecord(job.id(), job.assetVersionId(), job.jobType(), job.processorType(),
                job.processorVersion(), job.inputChecksum(), AssetProcessingJobStatus.QUEUED, job.attempts(),
                job.maxAttempts(), job.createdAt(), retryAt, job.startedAt(), null, errorCode, sanitizedErrorDetail, null));
    }

    @Override
    public synchronized AssetProcessingJobRecord markDeadLettered(UUID jobId, String errorCode, String sanitizedErrorDetail, Instant now) {
        AssetProcessingJobRecord job = required(jobId);
        return put(new AssetProcessingJobRecord(job.id(), job.assetVersionId(), job.jobType(), job.processorType(),
                job.processorVersion(), job.inputChecksum(), AssetProcessingJobStatus.DEAD_LETTERED, job.attempts(),
                job.maxAttempts(), job.createdAt(), job.availableAt(), job.startedAt(), now, errorCode, sanitizedErrorDetail, null));
    }

    @Override
    public synchronized AssetProcessingResultRecord saveResult(AssetProcessingResultRecord result) {
        results.put(result.id(), result);
        return result;
    }

    @Override
    public synchronized List<AssetProcessingResultRecord> resultsForVersion(UUID assetVersionId) {
        return new ArrayList<>(results.values()).stream()
                .filter(result -> result.assetVersionId().equals(assetVersionId))
                .toList();
    }

    @Override
    public synchronized boolean hasIncompleteRequiredJobs(UUID assetVersionId) {
        return jobsForVersion(assetVersionId).stream().anyMatch(job -> job.status() != AssetProcessingJobStatus.SUCCEEDED);
    }

    @Override
    public synchronized boolean hasRequiredJobStatus(UUID assetVersionId, AssetProcessingJobStatus status) {
        return jobsForVersion(assetVersionId).stream().anyMatch(job -> job.status() == status);
    }

    private AssetProcessingJobRecord required(UUID jobId) {
        return findJob(jobId).orElseThrow();
    }

    private AssetProcessingJobRecord put(AssetProcessingJobRecord job) {
        jobs.put(job.id(), job);
        return job;
    }
}
