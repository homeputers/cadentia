package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.QUARANTINED;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.FAILED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.PENDING_SCAN;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.REJECTED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.DEAD_LETTERED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.SUCCEEDED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.AUDIO_TRANSCODING;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.METADATA_EXTRACTION;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.PREVIEW_GENERATION;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.VIRUS_SCAN;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.WAVEFORM_ANALYSIS;
import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetProcessingOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-06-16T12:00:00Z");

    private MutableAssetRepository assetRepository;
    private InMemoryAssetProcessingRepository processingRepository;
    private AssetProcessingOrchestrator orchestrator;
    private AssetVersionRecord version;

    @BeforeEach
    void setUp() {
        assetRepository = new MutableAssetRepository();
        processingRepository = new InMemoryAssetProcessingRepository();
        orchestrator = new AssetProcessingOrchestrator(
                assetRepository,
                processingRepository,
                new DeterministicAssetProcessingHandlers().handlers(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        version = version("audio/mpeg", "safe-checksum");
        assetRepository.version = version;
    }

    @Test
    void schedulesRequiredJobsForAudioVersionAndIsIdempotent() {
        // Arrange / Act
        var first = orchestrator.scheduleForFinalizedVersion(version);
        var replay = orchestrator.scheduleForFinalizedVersion(version);

        // Assert
        assertThat(first).hasSize(5);
        assertThat(replay).containsExactlyElementsOf(first);
        assertThat(first).extracting(job -> job.jobType())
                .containsExactly(VIRUS_SCAN, METADATA_EXTRACTION, WAVEFORM_ANALYSIS, AUDIO_TRANSCODING, PREVIEW_GENERATION);
        assertThat(assetRepository.version.processingStatusCode()).isEqualTo(PENDING_SCAN);
    }

    @Test
    void recordsSuccessfulProcessingResultsWithoutMutatingSourceMetadata() {
        // Arrange
        orchestrator.scheduleForFinalizedVersion(version);

        // Act
        orchestrator.processDueJobs();

        // Assert
        assertThat(processingRepository.jobsForVersion(version.id())).allSatisfy(job -> assertThat(job.status()).isEqualTo(SUCCEEDED));
        assertThat(processingRepository.resultsForVersion(version.id())).hasSize(5);
        assertThat(processingRepository.resultsForVersion(version.id()))
                .filteredOn(result -> result.resultType() == AUDIO_TRANSCODING)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.outputStorageKey()).contains("/derived/transcode.mp3");
                    assertThat(result.inputChecksum()).isEqualTo(version.checksumValue());
                });
        assertThat(assetRepository.version.storageKey()).isEqualTo(version.storageKey());
        assertThat(assetRepository.version.processingStatusCode()).isEqualTo(READY);
    }

    @Test
    void failedVirusScanQuarantinesVersionAndBlocksAvailability() {
        // Arrange
        version = version("audio/mpeg", "contains-virus-signature");
        assetRepository.version = version;
        orchestrator.scheduleForFinalizedVersion(version);

        // Act
        orchestrator.processJob(processingRepository.jobsForVersion(version.id()).get(0).id());

        // Assert
        assertThat(assetRepository.version.lifecycleStatusCode()).isEqualTo(QUARANTINED);
        assertThat(assetRepository.version.processingStatusCode()).isEqualTo(REJECTED);
        assertThat(processingRepository.resultsForVersion(version.id())).singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo(AssetProcessingModels.AssetProcessingResultStatus.UNSAFE));
    }

    @Test
    void retryExhaustionDeadLettersWithoutSensitivePayloadDetails() {
        // Arrange
        AssetProcessingHandler failing = new AssetProcessingHandler() {
            @Override
            public AssetProcessingModels.AssetProcessingJobType jobType() {
                return VIRUS_SCAN;
            }

            @Override
            public String processorType() {
                return "failing-scan";
            }

            @Override
            public String processorVersion() {
                return "1.0";
            }

            @Override
            public AssetProcessingOutcome process(AssetVersionRecord version, AssetProcessingModels.AssetProcessingJobRecord job) {
                return AssetProcessingOutcome.failure("TEMPORARY_FAILURE", "token=abc https://signed.example.invalid/object");
            }
        };
        List<AssetProcessingHandler> handlers = new java.util.ArrayList<>(new DeterministicAssetProcessingHandlers().handlers());
        handlers.removeIf(handler -> handler.jobType() == VIRUS_SCAN);
        handlers.add(failing);
        orchestrator = new AssetProcessingOrchestrator(
                assetRepository,
                processingRepository,
                handlers,
                Clock.fixed(NOW, ZoneOffset.UTC));
        orchestrator.scheduleForFinalizedVersion(version);
        UUID jobId = processingRepository.jobsForVersion(version.id()).get(0).id();

        // Act
        orchestrator.processJob(jobId);
        processingRepository.findJob(jobId).ifPresent(job -> processingRepository.markFailed(jobId, job.errorCode(), job.sanitizedErrorDetail(), NOW));
        orchestrator.processJob(jobId);
        processingRepository.findJob(jobId).ifPresent(job -> processingRepository.markFailed(jobId, job.errorCode(), job.sanitizedErrorDetail(), NOW));
        var exhausted = orchestrator.processJob(jobId);

        // Assert
        assertThat(exhausted.status()).isEqualTo(DEAD_LETTERED);
        assertThat(exhausted.sanitizedErrorDetail()).doesNotContain("abc", "https://signed.example.invalid/object");
        assertThat(assetRepository.version.processingStatusCode()).isEqualTo(FAILED);
    }

    private AssetVersionRecord version(String mimeType, String checksum) {
        return new AssetVersionRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "rev-a", "LOCAL_FILESYSTEM", null,
                "local:local-development", "local-development/assets/asset/v1/object", "SHA-256", checksum, mimeType,
                256L, "https://example.invalid/source", "Fixture provenance", "planner", NOW, AVAILABLE, PENDING_SCAN,
                WORSHIP_TEAM, new LicenseMetadata(CCLI_COVERED, "Fixture", "REF", "Use", "Holder", NOW, null, WORSHIP_TEAM));
    }

    private static class MutableAssetRepository implements AssetRepository {
        private AssetVersionRecord version;

        @Override
        public AssetModels.AssetRecord createAsset(AssetModels.CreateAssetCommand command) { return null; }

        @Override
        public AssetVersionRecord createVersion(AssetModels.CreateAssetVersionCommand command) { return null; }

        @Override
        public Optional<AssetModels.AssetRecord> findAsset(UUID assetId) { return Optional.empty(); }

        @Override
        public List<AssetModels.AssetRecord> listAssets() { return List.of(); }

        @Override
        public Optional<AssetVersionRecord> findVersion(UUID assetVersionId) { return Optional.of(version); }

        @Override
        public AssetVersionRecord transitionProcessingStatus(UUID assetVersionId, AssetModels.AssetProcessingStatusCode processingStatusCode, String changedBy, String reasonCode) {
            version = copy(version, version.lifecycleStatusCode(), processingStatusCode);
            return version;
        }

        @Override
        public AssetVersionRecord quarantineVersion(UUID assetVersionId, String changedBy, String reasonCode) {
            version = copy(version, QUARANTINED, REJECTED);
            return version;
        }

        @Override
        public AssetModels.AssetRecord archiveAsset(UUID assetId, String archivedBy, String reason) { return null; }

        @Override
        public AssetVersionRecord archiveVersion(UUID assetVersionId, String archivedBy, String reason) { return null; }

        @Override
        public List<AssetModels.ControlledVocabularyRecord> listAssetTypes() { return List.of(); }

        @Override
        public List<AssetModels.ControlledVocabularyRecord> listLifecycleStatuses() { return List.of(); }

        @Override
        public List<AssetModels.ControlledVocabularyRecord> listProcessingStatuses() { return List.of(); }

        @Override
        public List<AssetModels.ControlledVocabularyRecord> listLicenseStatuses() { return List.of(); }

        @Override
        public List<AssetModels.ControlledVocabularyRecord> listAccessPolicies() { return List.of(); }

        private AssetVersionRecord copy(
                AssetVersionRecord source,
                AssetModels.AssetLifecycleStatusCode lifecycleStatusCode,
                AssetModels.AssetProcessingStatusCode processingStatusCode) {
            return new AssetVersionRecord(source.id(), source.stableIdentifier(), source.assetId(), source.versionNumber(),
                    source.revisionCode(), source.storageProviderCode(), source.storageRegion(), source.storageBucketAlias(),
                    source.storageKey(), source.checksumAlgorithm(), source.checksumValue(), source.mimeType(), source.byteSize(),
                    source.sourceUri(), source.provenanceSummary(), source.createdBy(), source.createdAt(), lifecycleStatusCode,
                    processingStatusCode, source.accessPolicyCode(), source.licenseMetadata());
        }
    }
}
