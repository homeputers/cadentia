package com.cadentia.reng.scoring;

import java.util.List;
import java.util.UUID;

public record RecommendationSongExplanation(
        UUID songId,
        UUID arrangementId,
        int position,
        List<RecommendationExplanationFact> facts,
        List<ScoreComponentExplanation> scoreComponents,
        List<RecommendationExplanationEvidence> catalogMetadataReferences,
        List<RecommendationExplanationEvidence> themeEvidence,
        List<RecommendationExplanationEvidence> scriptureEvidence,
        List<RecommendationExplanationEvidence> approvalEvidence,
        List<RecommendationExplanationEvidence> provenanceEvidence,
        List<RecommendationExplanationFact> warnings,
        List<UiDisplayHint> uiDisplayHints) {

    public RecommendationSongExplanation {
        facts = facts == null ? List.of() : List.copyOf(facts);
        scoreComponents = scoreComponents == null ? List.of() : List.copyOf(scoreComponents);
        catalogMetadataReferences = catalogMetadataReferences == null ? List.of() : List.copyOf(catalogMetadataReferences);
        themeEvidence = themeEvidence == null ? List.of() : List.copyOf(themeEvidence);
        scriptureEvidence = scriptureEvidence == null ? List.of() : List.copyOf(scriptureEvidence);
        approvalEvidence = approvalEvidence == null ? List.of() : List.copyOf(approvalEvidence);
        provenanceEvidence = provenanceEvidence == null ? List.of() : List.copyOf(provenanceEvidence);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        uiDisplayHints = uiDisplayHints == null ? List.of() : List.copyOf(uiDisplayHints);
    }

    public RecommendationSongExplanation forAudience(DiagnosticsAudience audience) {
        DiagnosticsAudience effectiveAudience = audience == null ? DiagnosticsAudience.PUBLIC : audience;
        return new RecommendationSongExplanation(
                songId,
                arrangementId,
                position,
                RecommendationExplanationRedactor.filterFacts(facts, effectiveAudience),
                effectiveAudience == DiagnosticsAudience.ADMIN ? scoreComponents : List.of(),
                catalogMetadataReferences,
                themeEvidence,
                scriptureEvidence,
                approvalEvidence,
                RecommendationExplanationRedactor.redactEvidence(provenanceEvidence, effectiveAudience),
                RecommendationExplanationRedactor.filterFacts(warnings, effectiveAudience),
                uiDisplayHints);
    }

    public record UiDisplayHint(String group, String severity, String templateKey, List<String> reasonCodes) {

        public UiDisplayHint {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }
}
