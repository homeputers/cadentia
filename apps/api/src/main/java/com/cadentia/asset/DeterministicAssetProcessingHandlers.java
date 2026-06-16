package com.cadentia.asset;

import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingOutcome;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeterministicAssetProcessingHandlers {

    public List<AssetProcessingHandler> handlers() {
        return List.of(
                handler(AssetProcessingJobType.VIRUS_SCAN, "cadentia-safe-scan", result -> {
                    if (result.checksumValue().toLowerCase().contains("virus") || result.storageKey().contains("unsafe")) {
                        return AssetProcessingOutcome.unsafe("UNSAFE_CONTENT", "Processor reported unsafe content.");
                    }
                    return AssetProcessingOutcome.clean(Map.of("scanEngine", "deterministic-fixture"));
                }),
                handler(AssetProcessingJobType.PREVIEW_GENERATION, "cadentia-preview", result -> AssetProcessingOutcome.available(
                        derivedKey(result, "preview"), "image/png", 0L, Map.of("sourceMimeType", result.mimeType()))),
                handler(AssetProcessingJobType.WAVEFORM_ANALYSIS, "cadentia-waveform", result -> AssetProcessingOutcome.available(
                        derivedKey(result, "waveform"), "application/json", 0L, Map.of("channels", "2"))),
                handler(AssetProcessingJobType.AUDIO_TRANSCODING, "cadentia-audio-transcode", result -> AssetProcessingOutcome.available(
                        derivedKey(result, "transcode.mp3"), "audio/mpeg", 0L, Map.of("profile", "rehearsal-stream"))),
                handler(AssetProcessingJobType.METADATA_EXTRACTION, "cadentia-metadata", result -> AssetProcessingOutcome.available(
                        null, null, null, Map.of("mimeType", result.mimeType(), "byteSize", Long.toString(result.byteSize())))));
    }

    private AssetProcessingHandler handler(
            AssetProcessingJobType jobType,
            String processorType,
            java.util.function.Function<AssetVersionRecord, AssetProcessingOutcome> processor) {
        return new AssetProcessingHandler() {
            @Override
            public AssetProcessingJobType jobType() {
                return jobType;
            }

            @Override
            public String processorType() {
                return processorType;
            }

            @Override
            public String processorVersion() {
                return "1.0.0";
            }

            @Override
            public AssetProcessingOutcome process(AssetVersionRecord version, AssetProcessingJobRecord job) {
                return processor.apply(version);
            }
        };
    }

    private static String derivedKey(AssetVersionRecord version, String suffix) {
        return version.storageKey() + "/derived/" + suffix;
    }
}
