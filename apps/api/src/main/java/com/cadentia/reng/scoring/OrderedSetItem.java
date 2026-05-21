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
        TransitionScore transitionFromPrevious) {

    public OrderedSetItem {
        explanationFacts = explanationFacts == null ? List.of() : List.copyOf(explanationFacts);
        candidateComponentScores = candidateComponentScores == null ? List.of() : List.copyOf(candidateComponentScores);
    }
}
