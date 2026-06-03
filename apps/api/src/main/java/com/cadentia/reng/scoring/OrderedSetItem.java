package com.cadentia.reng.scoring;

import java.util.List;
import java.util.UUID;

public record OrderedSetItem(
        UUID arrangementId,
        UUID songId,
        int position,
        List<RecommendationExplanationFact> explanationFacts,
        List<ScoringComponentScore> candidateComponentScores,
        double candidateTotalScore,
        TransitionScore transitionFromPrevious,
        RecommendationSongExplanation songExplanation) {

    public OrderedSetItem(
            UUID arrangementId,
            UUID songId,
            int position,
            List<RecommendationExplanationFact> explanationFacts,
            List<ScoringComponentScore> candidateComponentScores,
            double candidateTotalScore,
            TransitionScore transitionFromPrevious) {
        this(
                arrangementId,
                songId,
                position,
                explanationFacts,
                candidateComponentScores,
                candidateTotalScore,
                transitionFromPrevious,
                null);
    }

    public OrderedSetItem {
        explanationFacts = explanationFacts == null ? List.of() : List.copyOf(explanationFacts);
        candidateComponentScores = candidateComponentScores == null ? List.of() : List.copyOf(candidateComponentScores);
        if (songExplanation != null
                && (!arrangementId.equals(songExplanation.arrangementId())
                        || !songId.equals(songExplanation.songId())
                        || position != songExplanation.position())) {
            throw new IllegalArgumentException("Song explanation identifiers must match the ordered set item");
        }
    }

    public OrderedSetItem forAudience(DiagnosticsAudience audience) {
        DiagnosticsAudience effectiveAudience = audience == null ? DiagnosticsAudience.PUBLIC : audience;
        return new OrderedSetItem(
                arrangementId,
                songId,
                position,
                RecommendationExplanationRedactor.filterFacts(explanationFacts, effectiveAudience),
                effectiveAudience == DiagnosticsAudience.ADMIN ? candidateComponentScores : List.of(),
                candidateTotalScore,
                transitionFromPrevious,
                songExplanation == null ? null : songExplanation.forAudience(effectiveAudience));
    }
}
