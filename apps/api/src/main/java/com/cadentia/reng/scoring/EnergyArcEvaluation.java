package com.cadentia.reng.scoring;

import java.util.List;

public record EnergyArcEvaluation(
        EnergyArc arc,
        String arcVersion,
        List<ScoringComponentScore> setLevelComponents,
        List<String> tradeoffCodes,
        double totalScore) {

    public EnergyArcEvaluation {
        setLevelComponents = setLevelComponents == null ? List.of() : List.copyOf(setLevelComponents);
        tradeoffCodes = tradeoffCodes == null ? List.of() : List.copyOf(tradeoffCodes);
    }
}
