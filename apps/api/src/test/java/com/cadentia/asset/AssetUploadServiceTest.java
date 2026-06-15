package com.cadentia.asset;

import static com.cadentia.asset.AssetModels.AssetAccessPolicyCode.WORSHIP_TEAM;
import static com.cadentia.asset.AssetModels.AssetLifecycleStatusCode.AVAILABLE;
import static com.cadentia.asset.AssetModels.AssetLicenseStatusCode.CCLI_COVERED;
import static com.cadentia.asset.AssetModels.AssetProcessingStatusCode.READY;
import static com.cadentia.asset.AssetModels.AssetTypeCode.CHORD_CHART;
import static com.cadentia.asset.AssetModels.AssetTypeCode.PDF;
import static com.cadentia.asset.AssetUploadErrorCode.CHECKSUM_MISMATCH;
import static com.cadentia.asset.AssetUploadErrorCode.EXPIRED_UPLOAD;
import static com.cadentia.asset.AssetUploadErrorCode.MIME_TYPE_NOT_ALLOWED;
import static com.cadentia.asset.AssetUploadErrorCode.STORAGE_FAILURE;
import static com.cadentia.asset.AssetUploadErrorCode.UNAUTHORIZED_ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.asset.AssetModels.AssetRecord;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import com.cadentia.asset.AssetStorageAdapter.SignedStorageUrl;
import com.cadentia.asset.AssetUploadModels.CreatePendingUploadCommand;
import com.cadentia.asset.AssetUploadModels.FinalizeUploadCommand;
import com.cadentia.asset.AssetUploadModels.PendingUploadStatus;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-12T12:00:00Z");
    private static final String INSTANCE_ID = "church-a";
    private static final String ACTOR = "planner@cadentia.test";
    private static final String SHA_256 = "SHA-256";
    private static final String DIGEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private AssetRepository assetRepository;
    private InMemoryPendingAssetUploadRepository pendingUploadRepository;
    private AssetStorageAdapter storageAdapter;
    private AssetStorageProperties storageProperties;
    private AssetUploadService service;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        pendingUploadRepository = new InMemoryPendingAssetUploadRepository();
        storageAdapter = mock(AssetStorageAdapter.class);
        storageProperties = new AssetStorageProperties();
        storageProperties.setPendingUploadTtl(Duration.ofHours(1));
        service = new AssetUploadService(
                assetRepository,
                pendingUploadRepository,
                storageAdapter,
                storageProperties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assetId = UUID.randomUUID();
        when(storageAdapter.providerCode()).thenReturn("LOCAL_FILESYSTEM");
        when(storageAdapter.signedUploadUrl(any(), any(), eq(128L), any()))
                .thenAnswer(invocation -> new SignedStorageUrl(
                        URI.create("local-asset://cadentia/upload"),
                        NOW.plus(Duration.ofMinutes(15)),
                        "PUT",
                        invocation.getArgument(0)));
        when(assetRepository.findAsset(assetId)).thenReturn(Optional.of(assetRecord(CHORD_CHART)));
    }

    @Test
    void finalizesUploadAfterVerifyingStorageMetadata() {
        // Arrange
        var instructions = service.createPendingUpload(command(CHORD_CHART, "application/pdf", 128L));
        when(storageAdapter.metadata(instructions.storageKey())).thenReturn(Optional.of(metadata(
                instructions.storageKey(),
                "application/pdf",
                128L,
                DIGEST)));
        when(assetRepository.createVersion(any())).thenAnswer(invocation -> versionRecord(invocation.getArgument(0)));

        // Act
        AssetVersionRecord version = service.finalizeUpload(new FinalizeUploadCommand(
                instructions.uploadId(),
                INSTANCE_ID,
                ACTOR,
                instructions.storageKey()));

        // Assert
        assertThat(version.lifecycleStatusCode()).isEqualTo(AVAILABLE);
        assertThat(version.processingStatusCode()).isEqualTo(READY);
        assertThat(version.storageKey()).contains("local-development/assets/" + assetId + "/v1/");
        assertThat(version.storageBucketAlias()).isEqualTo("local:local-development");
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().status()).isEqualTo(PendingUploadStatus.FINALIZED);
        verify(storageAdapter).move(eq(instructions.storageKey()), eq(version.storageKey()));
    }

    @Test
    void rejectsChecksumMismatchAndKeepsUploadNonDownloadable() {
        // Arrange
        var instructions = service.createPendingUpload(command(CHORD_CHART, "application/pdf", 128L));
        when(storageAdapter.metadata(instructions.storageKey())).thenReturn(Optional.of(metadata(
                instructions.storageKey(),
                "application/pdf",
                128L,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")));
        when(storageAdapter.exists(instructions.storageKey())).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> service.finalizeUpload(new FinalizeUploadCommand(
                instructions.uploadId(),
                INSTANCE_ID,
                ACTOR,
                instructions.storageKey())))
                .isInstanceOf(AssetUploadException.class)
                .extracting("errorCode")
                .isEqualTo(CHECKSUM_MISMATCH);
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().status()).isEqualTo(PendingUploadStatus.REJECTED);
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().failureReasonCode()).isEqualTo(CHECKSUM_MISMATCH.name());
        verify(storageAdapter).quarantine(instructions.storageKey(), CHECKSUM_MISMATCH.name());
    }

    @Test
    void rejectsMimeTypesNotAllowedForAssetType() {
        // Arrange
        var instructions = service.createPendingUpload(command(CHORD_CHART, "image/png", 128L));
        when(storageAdapter.metadata(instructions.storageKey())).thenReturn(Optional.of(metadata(
                instructions.storageKey(),
                "image/png",
                128L,
                DIGEST)));

        // Act / Assert
        assertThatThrownBy(() -> service.finalizeUpload(new FinalizeUploadCommand(
                instructions.uploadId(),
                INSTANCE_ID,
                ACTOR,
                instructions.storageKey())))
                .isInstanceOf(AssetUploadException.class)
                .extracting("errorCode")
                .isEqualTo(MIME_TYPE_NOT_ALLOWED);
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().status()).isEqualTo(PendingUploadStatus.REJECTED);
    }

    @Test
    void rejectsExpiredPendingUpload() {
        // Arrange
        storageProperties.setPendingUploadTtl(Duration.ZERO);
        var instructions = service.createPendingUpload(command(PDF, "application/pdf", 128L));
        service = new AssetUploadService(
                assetRepository,
                pendingUploadRepository,
                storageAdapter,
                storageProperties,
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

        // Act / Assert
        assertThatThrownBy(() -> service.finalizeUpload(new FinalizeUploadCommand(
                instructions.uploadId(),
                INSTANCE_ID,
                ACTOR,
                instructions.storageKey())))
                .isInstanceOf(AssetUploadException.class)
                .extracting("errorCode")
                .isEqualTo(EXPIRED_UPLOAD);
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().status()).isEqualTo(PendingUploadStatus.EXPIRED);
    }

    @Test
    void rejectsUnauthorizedFinalizationActor() {
        // Arrange
        var instructions = service.createPendingUpload(command(CHORD_CHART, "application/pdf", 128L));

        // Act / Assert
        assertThatThrownBy(() -> service.finalizeUpload(new FinalizeUploadCommand(
                instructions.uploadId(),
                INSTANCE_ID,
                "other@cadentia.test",
                instructions.storageKey())))
                .isInstanceOf(AssetUploadException.class)
                .extracting("errorCode")
                .isEqualTo(UNAUTHORIZED_ACTOR);
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().status()).isEqualTo(PendingUploadStatus.REJECTED);
    }

    @Test
    void recordsStorageAdapterFailuresAsFailedUploads() {
        // Arrange
        var instructions = service.createPendingUpload(command(CHORD_CHART, "application/pdf", 128L));
        doThrow(new AssetStorageException("metadata unavailable"))
                .when(storageAdapter).metadata(instructions.storageKey());

        // Act / Assert
        assertThatThrownBy(() -> service.finalizeUpload(new FinalizeUploadCommand(
                instructions.uploadId(),
                INSTANCE_ID,
                ACTOR,
                instructions.storageKey())))
                .isInstanceOf(AssetUploadException.class)
                .extracting("errorCode")
                .isEqualTo(STORAGE_FAILURE);
        assertThat(pendingUploadRepository.find(instructions.uploadId()).orElseThrow().status()).isEqualTo(PendingUploadStatus.FAILED);
    }

    @Test
    void selectsOnlyAbandonedFailedOrRejectedPendingUploadsForCleanup() {
        // Arrange
        var abandoned = service.createPendingUpload(command(CHORD_CHART, "application/pdf", 128L));
        var active = service.createPendingUpload(command(CHORD_CHART, "application/pdf", 128L));
        pendingUploadRepository.updateStatus(active.uploadId(), PendingUploadStatus.FINALIZED, null, NOW);
        pendingUploadRepository.updateStatus(abandoned.uploadId(), PendingUploadStatus.EXPIRED, EXPIRED_UPLOAD.name(), NOW);

        // Act
        var cleanupPlan = service.cleanupPlan();

        // Assert
        assertThat(cleanupPlan.candidates())
                .extracting(candidate -> candidate.uploadId())
                .contains(abandoned.uploadId())
                .doesNotContain(active.uploadId());
    }

    private CreatePendingUploadCommand command(AssetModels.AssetTypeCode assetTypeCode, String mimeType, long byteSize) {
        return new CreatePendingUploadCommand(
                INSTANCE_ID,
                assetId,
                assetTypeCode,
                1,
                "rev-a",
                SHA_256,
                DIGEST,
                mimeType,
                byteSize,
                "https://example.invalid/source",
                "Fixture provenance.",
                ACTOR,
                WORSHIP_TEAM,
                new LicenseMetadata(
                        CCLI_COVERED,
                        "Fixture license",
                        "FIXTURE-1",
                        "Fixture restrictions.",
                        "Cadentia Test Church",
                        NOW,
                        null,
                        WORSHIP_TEAM));
    }

    private StoredObjectMetadata metadata(String storageKey, String mimeType, long byteSize, String digest) {
        return new StoredObjectMetadata(
                storageKey,
                byteSize,
                mimeType,
                NOW,
                Map.of(SHA_256, digest));
    }

    private AssetRecord assetRecord(AssetModels.AssetTypeCode assetTypeCode) {
        return new AssetRecord(
                assetId,
                UUID.randomUUID(),
                assetTypeCode,
                "Lead Sheet",
                "Fixture asset",
                ACTOR,
                "Worship Team",
                WORSHIP_TEAM,
                AVAILABLE,
                null,
                ACTOR,
                NOW,
                NOW,
                java.util.List.of());
    }

    private AssetVersionRecord versionRecord(CreateAssetVersionCommand command) {
        return new AssetVersionRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                command.assetId(),
                command.versionNumber(),
                command.revisionCode(),
                command.storageProviderCode(),
                command.storageRegion(),
                command.storageBucketAlias(),
                command.storageKey(),
                command.checksumAlgorithm(),
                command.checksumValue(),
                command.mimeType(),
                command.byteSize(),
                command.sourceUri(),
                command.provenanceSummary(),
                command.createdBy(),
                NOW,
                command.lifecycleStatusCode(),
                command.processingStatusCode(),
                command.accessPolicyCode(),
                command.licenseMetadata());
    }
}
