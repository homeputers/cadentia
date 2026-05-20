package com.cadentia.reng.scoring;

import java.util.List;
import java.util.UUID;

public record OrderedSetItem(
        UUID arrangementId,
        UUID songId,
        int position,
        List<ScoringComponentScore> candidateComponentScores,
        double candidateTotalScore,
        TransitionScore transitionFromPrevious) {

    public OrderedSetItem {
        candidateComponentScores = candidateComponentScores == null ? List.of() : List.copyOf(candidateComponentScores);
    }
}
