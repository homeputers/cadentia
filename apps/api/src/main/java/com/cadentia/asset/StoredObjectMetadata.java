package com.cadentia.asset;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record StoredObjectMetadata(
        String storageKey,
        long byteSize,
        String mimeType,
        Instant lastModifiedAt,
        Map<String, String> digests) {

    public StoredObjectMetadata {
        digests = digests == null ? Map.of() : Map.copyOf(digests);
    }

    public Optional<String> digest(String algorithm) {
        if (algorithm == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(digests.get(normalizeAlgorithm(algorithm)));
    }

    public static String normalizeAlgorithm(String algorithm) {
        return algorithm.trim().toUpperCase(Locale.ROOT).replace("_", "-");
    }
}
