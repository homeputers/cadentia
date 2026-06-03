package com.cadentia.reng.scoring;

import java.util.List;

public record ScoringComponentScore(
        String componentCode,
        double rawScore,
        double weightedContribution,
        Double weight,
        List<String> reasonCodes) {

    public ScoringComponentScore(String componentCode, double rawScore, double weightedContribution) {
        this(componentCode, rawScore, weightedContribution, null, contributionReasonCodes(weightedContribution));
    }

    public ScoringComponentScore {
        reasonCodes = reasonCodes == null ? contributionReasonCodes(weightedContribution) : List.copyOf(reasonCodes);
    }

    public static List<String> contributionReasonCodes(double weightedContribution) {
        if (weightedContribution > 0.000001d) {
            return List.of("CONTRIBUTION_POSITIVE");
        }
        if (weightedContribution < -0.000001d) {
            return List.of("CONTRIBUTION_PENALTY");
        }
        return List.of("CONTRIBUTION_NEUTRAL");
    }
}
