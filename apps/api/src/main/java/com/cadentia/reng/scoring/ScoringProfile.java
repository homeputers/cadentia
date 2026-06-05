package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Map;

public record ScoringProfile(
        String version,
        Map<String, Double> componentWeights,
        List<String> deterministicTieBreakOrder,
        ScoringProfileLifecycle lifecycle,
        Map<TeamConstraintCode, TeamConstraintMode> teamConstraintModes) {

    public ScoringProfile {
        componentWeights = componentWeights == null ? Map.of() : Map.copyOf(componentWeights);
        deterministicTieBreakOrder = deterministicTieBreakOrder == null ? List.of() : List.copyOf(deterministicTieBreakOrder);
        lifecycle = lifecycle == null ? ScoringProfileLifecycle.active() : lifecycle;
        teamConstraintModes = teamConstraintModes == null ? Map.of() : Map.copyOf(teamConstraintModes);
    }

    public ScoringProfile(
            String version,
            Map<String, Double> componentWeights,
            List<String> deterministicTieBreakOrder,
            ScoringProfileLifecycle lifecycle) {
        this(version, componentWeights, deterministicTieBreakOrder, lifecycle, Map.of());
    }

    public ScoringProfile(String version, Map<String, Double> componentWeights, List<String> deterministicTieBreakOrder) {
        this(version, componentWeights, deterministicTieBreakOrder, ScoringProfileLifecycle.active(), Map.of());
    }

    public TeamConstraintMode teamConstraintMode(TeamConstraintCode code) {
        return teamConstraintModes.getOrDefault(code, TeamConstraintMode.DISABLED);
    }
}
