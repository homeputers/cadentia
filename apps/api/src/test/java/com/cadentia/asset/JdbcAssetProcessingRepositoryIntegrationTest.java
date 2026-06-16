package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.PENDING_SCAN;
import static com.cadentia.asset.AssetModels.AssetTypeCode.BACKING_TRACK;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.DEAD_LETTERED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.QUEUED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.RUNNING;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus.SUCCEEDED;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.AUDIO_TRANSCODING;
import static com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType.VIRUS_SCAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.asset.AssetModels.CreateAssetCommand;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAssetProcessingRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcAssetRepository assetRepository;
    private JdbcAssetProcessingRepository processingRepository;
    private UUID assetVersionId;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        assetRepository = new JdbcAssetRepository(jdbcTemplate);
        processingRepository = new JdbcAssetProcessingRepository(jdbcTemplate);
        assetVersionId = seedAssetVersion();
    }

    @Test
    void enqueuesJobsIdempotentlyAndFindsRunnableWork() {
        // Arrange
        Instant now = Instant.parse("2026-06-16T12:00:00Z");

        // Act
        var first = processingRepository.enqueue(
                assetVersionId,
                VIRUS_SCAN,
                "clamav",
                "1.2.3",
                "checksum-a",
                3,
                now);
        var replay = processingRepository.enqueue(
                assetVersionId,
                VIRUS_SCAN,
                "clamav",
                "1.2.3",
                "checksum-a",
                3,
                now.plusSeconds(60));

        // Assert
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.status()).isEqualTo(QUEUED);
        assertThat(processingRepository.findJob(first.id())).contains(replay);
        assertThat(processingRepository.jobsForVersion(assetVersionId)).singleElement()
                .satisfies(job -> {
                    assertThat(job.assetVersionId()).isEqualTo(assetVersionId);
                    assertThat(job.jobType()).isEqualTo(VIRUS_SCAN);
                    assertThat(job.processorType()).isEqualTo("clamav");
                    assertThat(job.inputChecksum()).isEqualTo("checksum-a");
                });
        assertThat(processingRepository.runnableJobs(now))
                .extracting(job -> job.id())
                .containsExactly(first.id());
    }

    @Test
    void recordsSuccessfulResultsAndMarksJobSucceeded() {
        // Arrange
        Instant now = Instant.parse("2026-06-16T12:00:00Z");
        var job = processingRepository.enqueue(
                assetVersionId,
                AUDIO_TRANSCODING,
                "ffmpeg",
                "6.1",
                "checksum-a",
                2,
                now);
        var running = processingRepository.markRunning(job.id(), now.plusSeconds(5));
        var result = new AssetProcessingResultRecord(
                UUID.randomUUID(),
                running.id(),
                assetVersionId,
                AUDIO_TRANSCODING,
                running.processorType(),
                running.processorVersion(),
                running.inputChecksum(),
                AssetProcessingModels.AssetProcessingResultStatus.AVAILABLE,
                "derived/transcode.mp3",
                "audio/mpeg",
                4_096L,
                Map.of("profile", "rehearsal-stream"),
                now.plusSeconds(10));

        // Act
        processingRepository.saveResult(result);
        var succeeded = processingRepository.markSucceeded(running.id(), result.id(), now.plusSeconds(11));

        // Assert
        assertThat(running.status()).isEqualTo(RUNNING);
        assertThat(running.attempts()).isOne();
        assertThat(succeeded.status()).isEqualTo(SUCCEEDED);
        assertThat(succeeded.outputResultId()).isEqualTo(result.id());
        assertThat(processingRepository.resultsForVersion(assetVersionId)).singleElement()
                .satisfies(record -> {
                    assertThat(record.jobId()).isEqualTo(job.id());
                    assertThat(record.outputStorageKey()).isEqualTo("derived/transcode.mp3");
                    assertThat(record.outputMimeType()).isEqualTo("audio/mpeg");
                    assertThat(record.outputByteSize()).isEqualTo(4_096L);
                });
        assertThat(processingRepository.hasIncompleteRequiredJobs(assetVersionId)).isFalse();
    }

    @Test
    void retriesAndDeadLettersWithoutDeletingDiagnostics() {
        // Arrange
        Instant now = Instant.parse("2026-06-16T12:00:00Z");
        var job = processingRepository.enqueue(
                assetVersionId,
                VIRUS_SCAN,
                "clamav",
                "1.2.3",
                "checksum-a",
                1,
                now);

        // Act
        var running = processingRepository.markRunning(job.id(), now.plusSeconds(1));
        var queued = processingRepository.markFailed(
                running.id(),
                "SCANNER_TIMEOUT",
                "scanner timeout after 30 seconds",
                now.plusSeconds(61));
        var deadLettered = processingRepository.markDeadLettered(
                queued.id(),
                "SCANNER_TIMEOUT",
                "scanner timeout after 30 seconds",
                now.plusSeconds(120));

        // Assert
        assertThat(queued.status()).isEqualTo(QUEUED);
        assertThat(queued.errorCode()).isEqualTo("SCANNER_TIMEOUT");
        assertThat(queued.sanitizedErrorDetail()).isEqualTo("scanner timeout after 30 seconds");
        assertThat(processingRepository.runnableJobs(now.plusSeconds(60))).isEmpty();
        assertThat(deadLettered.status()).isEqualTo(DEAD_LETTERED);
        assertThat(deadLettered.completedAt()).isEqualTo(now.plusSeconds(120));
        assertThat(processingRepository.hasRequiredJobStatus(assetVersionId, DEAD_LETTERED)).isTrue();
        assertThat(processingRepository.hasIncompleteRequiredJobs(assetVersionId)).isTrue();
    }

    @Test
    void rejectsUnknownJobTypesAndOrphanedVersionReferences() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> processingRepository.enqueue(
                        UUID.randomUUID(),
                        VIRUS_SCAN,
                        "clamav",
                        "1.2.3",
                        "checksum-a",
                        3,
                        Instant.parse("2026-06-16T12:00:00Z")))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID seedAssetVersion() {
        var asset = assetRepository.createAsset(new CreateAssetCommand(
                BACKING_TRACK,
                "Backing Track",
                "Licensed rehearsal track.",
                "planner@cadentia.test",
                "Worship Team",
                WORSHIP_TEAM,
                AVAILABLE,
                "planner@cadentia.test"));
        return assetRepository.createVersion(new CreateAssetVersionCommand(
                asset.id(),
                1,
                "rev-a",
                "S3_COMPATIBLE",
                "us-east-1",
                "worship-media",
                "assets/audio/backing-track.mp3",
                "SHA-256",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "audio/mpeg",
                123_456L,
                "fixture://backing-track",
                "Verified fixture provenance.",
                "planner@cadentia.test",
                AVAILABLE,
                PENDING_SCAN,
                WORSHIP_TEAM,
                new LicenseMetadata(
                        CCLI_COVERED,
                        "Fixture License Source",
                        "FIXTURE-1",
                        "Fixture usage restriction.",
                        "Cadentia Test Church",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null,
                        WORSHIP_TEAM),
                true)).id();
    }
}
