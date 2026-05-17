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
        List<RecommendationTag> controlledTags,
        List<RecommendationTag> matchedTags,
        ApprovalGateSummary approvalGateSummary) {

    public RecommendableArrangement {
        tags = tags == null ? List.of() : List.copyOf(tags);
        controlledTags = controlledTags == null ? List.of() : List.copyOf(controlledTags);
        matchedTags = matchedTags == null ? List.of() : List.copyOf(matchedTags);
    }

    public RecommendableArrangement(
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
        this(
                arrangementId,
                songId,
                currentLyricsDocumentId,
                title,
                language,
                musicalKey,
                keyMode,
                bpm,
                timeSignature,
                energy,
                tags,
                List.of(),
                List.of(),
                approvalGateSummary);
    }

    RecommendableArrangement withRecommendationTags(
            List<RecommendationTag> controlledTags,
            List<RecommendationTag> matchedTags) {
        return new RecommendableArrangement(
                arrangementId,
                songId,
                currentLyricsDocumentId,
                title,
                language,
                musicalKey,
                keyMode,
                bpm,
                timeSignature,
                energy,
                tags,
                controlledTags,
                matchedTags,
                approvalGateSummary);
    }
}
