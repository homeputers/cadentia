package com.cadentia.reng;

import java.util.List;
import java.util.UUID;

public record RecommendableArrangement(
        UUID arrangementId,
        UUID songId,
        String title,
        String language,
        String musicalKey,
        int bpm,
        String timeSignature,
        int energy,
        List<String> tags) {
}
