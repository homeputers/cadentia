package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.FAILED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.PENDING_SCAN;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.PROCESSING;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.DEAD_LETTERED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.SUCCEEDED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.AUDIO_TRANSCODING;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.METADATA_EXTRACTION;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.PREVIEW_GENERATION;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.VIRUS_SCAN;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.WAVEFORM_ANALYSIS;

import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingOutcome;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetProcessingOrchestrator {

    private static final int MAX_ATTEMPTS = 3;

    private final AssetRepository assetRepository;
    private final AssetProcessingRepository processingRepository;
    private final Map<AssetProcessingJobType, AssetProcessingHandler> handlers;
    private final Clock clock;

    @Autowired
    public AssetProcessingOrchestrator(
            AssetRepository assetRepository,
            AssetProcessingRepository processingRepository,
            DeterministicAssetProcessingHandlers handlers) {
        this(assetRepository, processingRepository, handlers.handlers(), Clock.systemUTC());
    }

    AssetProcessingOrchestrator(
            AssetRepository assetRepository,
            AssetProcessingRepository processingRepository,
            List<AssetProcessingHandler> handlers,
            Clock clock) {
        this.assetRepository = assetRepository;
        this.processingRepository = processingRepository;
        this.handlers = handlers.stream().collect(Collectors.toMap(AssetProcessingHandler::jobType, Function.identity(), (a, b) -> a,
                () -> new EnumMap<>(AssetProcessingJobType.class)));
        this.clock = clock;
    }

    public List<AssetProcessingJobRecord> scheduleForFinalizedVersion(AssetVersionRecord version) {
        if (version.lifecycleStatusCode() != AVAILABLE) {
            return List.of();
        }
        List<AssetProcessingJobType> jobTypes = requiredJobs(version);
        Instant now = clock.instant();
        if (!jobTypes.isEmpty()) {
            assetRepository.transitionProcessingStatus(version.id(), PENDING_SCAN, "asset-processing", "PROCESSING_SCHEDULED");
        }
        return jobTypes.stream()
                .map(jobType -> {
                    AssetProcessingHandler handler = handlers.get(jobType);
                    return processingRepository.enqueue(version.id(), jobType, handler.processorType(), handler.processorVersion(),
                            version.checksumValue(), MAX_ATTEMPTS, now);
                })
                .toList();
    }

    public void processDueJobs() {
        processingRepository.runnableJobs(clock.instant()).forEach(job -> processJob(job.id()));
    }

    public AssetProcessingJobRecord processJob(UUID jobId) {
        AssetProcessingJobRecord existing = processingRepository.findJob(jobId).orElseThrow();
        if (existing.status() == SUCCEEDED || existing.status() == DEAD_LETTERED) {
            return existing;
        }
        AssetProcessingJobRecord running = processingRepository.markRunning(jobId, clock.instant());
        AssetVersionRecord version = assetRepository.findVersion(running.assetVersionId()).orElseThrow();
        assetRepository.transitionProcessingStatus(version.id(), running.jobType() == VIRUS_SCAN ? PENDING_SCAN : PROCESSING,
                "asset-processing", "JOB_RUNNING");
        AssetProcessingOutcome outcome;
        try {
            outcome = handlers.get(running.jobType()).process(version, running);
        } catch (RuntimeException ex) {
            outcome = AssetProcessingOutcome.failure("PROCESSOR_EXCEPTION", ex.getClass().getSimpleName());
        }
        return complete(running, version, outcome);
    }

    private AssetProcessingJobRecord complete(
            AssetProcessingJobRecord job,
            AssetVersionRecord version,
            AssetProcessingOutcome outcome) {
        Instant now = clock.instant();
        if (outcome.status() == AssetProcessingResultStatus.UNSAFE) {
            AssetProcessingResultRecord result = saveResult(job, outcome, now);
            processingRepository.markSucceeded(job.id(), result.id(), now);
            assetRepository.quarantineVersion(version.id(), "asset-processing", outcome.errorCode());
            return processingRepository.findJob(job.id()).orElseThrow();
        }
        if (outcome.status() == AssetProcessingResultStatus.FAILED) {
            if (job.attempts() >= job.maxAttempts()) {
                AssetProcessingJobRecord dead = processingRepository.markDeadLettered(
                        job.id(), outcome.errorCode(), sanitize(outcome.sanitizedErrorDetail()), now);
                assetRepository.transitionProcessingStatus(version.id(), FAILED, "asset-processing", "PROCESSING_DEAD_LETTERED");
                return dead;
            }
            return processingRepository.markFailed(job.id(), outcome.errorCode(), sanitize(outcome.sanitizedErrorDetail()),
                    now.plus(Duration.ofMinutes(job.attempts())));
        }
        AssetProcessingResultRecord result = saveResult(job, outcome, now);
        AssetProcessingJobRecord succeeded = processingRepository.markSucceeded(job.id(), result.id(), now);
        if (!processingRepository.hasIncompleteRequiredJobs(version.id())) {
            assetRepository.transitionProcessingStatus(version.id(), READY, "asset-processing", "PROCESSING_COMPLETE");
        }
        return succeeded;
    }

    private AssetProcessingResultRecord saveResult(AssetProcessingJobRecord job, AssetProcessingOutcome outcome, Instant now) {
        return processingRepository.saveResult(new AssetProcessingResultRecord(
                UUID.randomUUID(), job.id(), job.assetVersionId(), job.jobType(), job.processorType(), job.processorVersion(),
                job.inputChecksum(), outcome.status(), outcome.outputStorageKey(), outcome.outputMimeType(), outcome.outputByteSize(),
                outcome.resultMetadata(), now));
    }

    private List<AssetProcessingJobType> requiredJobs(AssetVersionRecord version) {
        boolean audio = version.mimeType().startsWith("audio/");
        boolean pdf = version.mimeType().equals("application/pdf");
        boolean image = version.mimeType().startsWith("image/");
        List<AssetProcessingJobType> derived = audio
                ? List.of(VIRUS_SCAN, METADATA_EXTRACTION, WAVEFORM_ANALYSIS, AUDIO_TRANSCODING, PREVIEW_GENERATION)
                : pdf || image || version.assetId() != null && version.byteSize() > 0
                        ? List.of(VIRUS_SCAN, METADATA_EXTRACTION, PREVIEW_GENERATION)
                        : List.of(VIRUS_SCAN, METADATA_EXTRACTION);
        if (version.assetId() == null || version.byteSize() <= 0 || version.mimeType() == null) {
            return List.of(VIRUS_SCAN);
        }
        return derived;
    }

    private String sanitize(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.replaceAll("https?://\\S+", "[redacted-url]").replaceAll("(?i)(password|token|secret)=\\S+", "$1=[redacted]");
    }
}
