package com.cadentia.reng.scoring;

import java.util.List;
import java.util.UUID;

public record TransitionScore(
        UUID fromArrangementId,
        UUID toArrangementId,
        List<ScoringComponentScore> components,
        double totalScore) {

    public TransitionScore {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
