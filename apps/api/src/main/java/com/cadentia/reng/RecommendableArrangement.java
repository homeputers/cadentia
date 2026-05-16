package com.cadentia.reng;

import com.cadentia.catalog.model.KeyMode;
import java.util.List;
import java.util.UUID;

public record RecommendableArrangement(
        UUID arrangementId,
        UUID songId,
        UUID currentLyricsDocumentId,
        String title,
        String language,
        String musicalKey,
        KeyMode keyMode,
        int bpm,
        String timeSignature,
        int energy,
        List<String> tags,
        ApprovalGateSummary approvalGateSummary) {
}
