package com.cadentia.reng.scoring;

import java.util.List;

public record ScoreComponentExplanation(
        String componentCode,
        double rawScore,
        double weightedContribution,
        Double weight,
        List<String> reasonCodes) {

    public ScoreComponentExplanation {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public static ScoreComponentExplanation from(ScoringComponentScore score) {
        return new ScoreComponentExplanation(
                score.componentCode(),
                score.rawScore(),
                score.weightedContribution(),
                score.weight(),
                score.reasonCodes());
    }
}
