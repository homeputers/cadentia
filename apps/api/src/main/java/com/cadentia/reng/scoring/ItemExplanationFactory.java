package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemExplanationFactory {

    public List<RecommendationExplanationFact> build(
            RecommendableArrangement candidate,
            ScoringRequest request,
            List<ScoringComponentScore> componentScores) {
        List<RecommendationExplanationFact> facts = new ArrayList<>();
        RecommendationExplanationSubject subject =
                new RecommendationExplanationSubject("arrangement", candidate.arrangementId().toString());

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.ROLE_FIT.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "ROLE_FIT",
                        "info",
                        "item",
                        subject,
                        "item.role_fit",
                        Map.of("score", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "candidate.role_fit", "raw", null)),
                        score.weightedContribution())));

        facts.add(new RecommendationExplanationFact(
                "APPROVAL_ELIGIBLE",
                "info",
                "item",
                subject,
                "item.approval_eligible",
                Map.of("hasProvenance", candidate.currentLyricsDocumentId() != null),
                List.of(
                        new RecommendationExplanationEvidence("approval", "approval_gate_summary", null, 1.0d),
                        new RecommendationExplanationEvidence("provenance", "current_lyrics_document_id", null, 1.0d)),
                null));

        if (!request.themeHints().isEmpty()) {
            List<String> matchedThemes = candidate.matchedTags().stream()
                    .filter(tag -> tag.tagType() == TagType.THEME)
                    .map(RecommendationTag::slug)
                    .toList();
            if (!matchedThemes.isEmpty()) {
                facts.add(new RecommendationExplanationFact(
                        "THEME_MATCH",
                        "info",
                        "item",
                        subject,
                        "item.theme_match",
                        Map.of("themes", String.join(",", matchedThemes)),
                        List.of(new RecommendationExplanationEvidence("catalog", "matched_tags", "theme", 1.0d)),
                        findImpact(componentScores, CandidateFeatureScorer.THEME_MATCH)));
            }
        }

        if (request.verseText() != null && !request.verseText().isBlank()) {
            List<String> scriptures = candidate.matchedTags().stream()
                    .filter(tag -> tag.tagType() == TagType.SCRIPTURE)
                    .map(RecommendationTag::name)
                    .toList();
            if (!scriptures.isEmpty()) {
                facts.add(new RecommendationExplanationFact(
                        "SCRIPTURE_MATCH",
                        "info",
                        "item",
                        subject,
                        "item.scripture_match",
                        Map.of("scripture", String.join(",", scriptures)),
                        List.of(new RecommendationExplanationEvidence("catalog", "matched_tags", "scripture", 1.0d)),
                        findImpact(componentScores, CandidateFeatureScorer.SCRIPTURE_MATCH)));
            }
        }


        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.MUSICAL_FIT.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "SCORE_COMPONENT_MUSICAL_FIT",
                        "info",
                        "item",
                        subject,
                        "item.score_component_musical_fit",
                        Map.of("score", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "candidate.musical_fit", "raw", null)),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.ENERGY_FIT.equals(score.componentCode()))
                .findFirst()
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "SCORE_COMPONENT_ENERGY_FIT",
                        "info",
                        "item",
                        subject,
                        "item.score_component_energy_fit",
                        Map.of("score", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "candidate.energy_fit", "raw", null)),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.METADATA_CONFIDENCE.equals(score.componentCode()))
                .findFirst()
                .filter(score -> score.rawScore() < 1.0d)
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "METADATA_LOW_CONFIDENCE",
                        "warning",
                        "item",
                        subject,
                        "item.metadata_low_confidence",
                        Map.of("confidence", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("catalog", "arrangement", "metadata", score.rawScore())),
                        score.weightedContribution())));

        componentScores.stream()
                .filter(score -> CandidateFeatureScorer.FEEDBACK_TUNING.equals(score.componentCode()))
                .findFirst()
                .filter(score -> score.rawScore() != 0.0d)
                .ifPresent(score -> facts.add(new RecommendationExplanationFact(
                        "FEEDBACK_TUNING",
                        score.rawScore() > 0.0d ? "info" : "warning",
                        "item",
                        subject,
                        "item.feedback_tuning",
                        Map.of("feedbackContribution", score.rawScore()),
                        List.of(new RecommendationExplanationEvidence("score", "feedback.aggregate", "raw", score.rawScore())),
                        score.weightedContribution())));

        return List.copyOf(facts);
    }

    private static Double findImpact(List<ScoringComponentScore> componentScores, String componentCode) {
        return componentScores.stream()
                .filter(score -> componentCode.equals(score.componentCode()))
                .map(ScoringComponentScore::weightedContribution)
                .findFirst()
                .orElse(null);
    }
}
