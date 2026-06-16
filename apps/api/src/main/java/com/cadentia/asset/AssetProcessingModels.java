package com.cadentia.asset;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AssetProcessingModels {

    private AssetProcessingModels() {
    }

    public enum AssetProcessingJobType {
        VIRUS_SCAN,
        PREVIEW_GENERATION,
        WAVEFORM_ANALYSIS,
        AUDIO_TRANSCODING,
        METADATA_EXTRACTION
    }

    public enum AssetProcessingJobStatus {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        DEAD_LETTERED
    }

    public enum AssetProcessingResultStatus {
        CLEAN,
        UNSAFE,
        AVAILABLE,
        FAILED
    }

    public record AssetProcessingJobRecord(
            UUID id,
            UUID assetVersionId,
            AssetProcessingJobType jobType,
            String processorType,
            String processorVersion,
            String inputChecksum,
            AssetProcessingJobStatus status,
            int attempts,
            int maxAttempts,
            Instant createdAt,
            Instant availableAt,
            Instant startedAt,
            Instant completedAt,
            String errorCode,
            String sanitizedErrorDetail,
            UUID outputResultId) {
    }

    public record AssetProcessingResultRecord(
            UUID id,
            UUID jobId,
            UUID assetVersionId,
            AssetProcessingJobType resultType,
            String processorType,
            String processorVersion,
            String inputChecksum,
            AssetProcessingResultStatus status,
            String outputStorageKey,
            String outputMimeType,
            Long outputByteSize,
            Map<String, String> resultMetadata,
            Instant createdAt) {
        public AssetProcessingResultRecord {
            resultMetadata = resultMetadata == null ? Map.of() : Map.copyOf(resultMetadata);
        }
    }

    public record AssetProcessingOutcome(
            AssetProcessingResultStatus status,
            String outputStorageKey,
            String outputMimeType,
            Long outputByteSize,
            Map<String, String> resultMetadata,
            String errorCode,
            String sanitizedErrorDetail) {
        public static AssetProcessingOutcome clean(Map<String, String> metadata) {
            return new AssetProcessingOutcome(AssetProcessingResultStatus.CLEAN, null, null, null, metadata, null, null);
        }

        public static AssetProcessingOutcome unsafe(String errorCode, String detail) {
            return new AssetProcessingOutcome(AssetProcessingResultStatus.UNSAFE, null, null, null, Map.of(), errorCode, detail);
        }

        public static AssetProcessingOutcome available(
                String storageKey,
                String mimeType,
                Long byteSize,
                Map<String, String> metadata) {
            return new AssetProcessingOutcome(
                    AssetProcessingResultStatus.AVAILABLE, storageKey, mimeType, byteSize, metadata, null, null);
        }

        public static AssetProcessingOutcome failure(String errorCode, String detail) {
            return new AssetProcessingOutcome(AssetProcessingResultStatus.FAILED, null, null, null, Map.of(), errorCode, detail);
        }
    }
}
