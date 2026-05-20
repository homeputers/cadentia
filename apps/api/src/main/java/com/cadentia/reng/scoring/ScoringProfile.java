package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Map;

public record ScoringProfile(
        String version,
        Map<String, Double> componentWeights,
        List<String> deterministicTieBreakOrder) {

    public ScoringProfile {
        componentWeights = componentWeights == null ? Map.of() : Map.copyOf(componentWeights);
        deterministicTieBreakOrder = deterministicTieBreakOrder == null ? List.of() : List.copyOf(deterministicTieBreakOrder);
    }
}
