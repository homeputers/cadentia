package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EnergyArcEvaluator {

    public static final String ARC_SHAPE_MATCH = "energy_arc_shape_match";
    public static final String ARC_SECTION_BALANCE = "energy_arc_section_balance";

    public EnergyArcEvaluation evaluate(List<RecommendableArrangement> orderedSet, ScoringRequest request, ScoringProfile profile) {
        EnergyArc arc = EnergyArc.fromNullable(request.energyArc());
        double shapeScore = scoreArcShape(orderedSet, arc);
        double sectionBalance = scoreSectionBalance(orderedSet, request.praiseCount());

        List<ScoringComponentScore> components = List.of(
                component(ARC_SHAPE_MATCH, shapeScore, profile.componentWeights()),
                component(ARC_SECTION_BALANCE, sectionBalance, profile.componentWeights()));
        double total = components.stream().mapToDouble(ScoringComponentScore::weightedContribution).sum();

        return new EnergyArcEvaluation(arc, EnergyArc.VERSION, components, tradeoffs(shapeScore, sectionBalance), total);
    }

    private static double scoreArcShape(List<RecommendableArrangement> orderedSet, EnergyArc arc) {
        if (orderedSet == null || orderedSet.size() < 2) {
            return 0.5d;
        }
        int first = clampEnergy(orderedSet.get(0).energy());
        int last = clampEnergy(orderedSet.get(orderedSet.size() - 1).energy());
        double slope = (last - first) / 100.0d;
        return switch (arc) {
            case RISING, LOW_TO_HIGH -> normalizeSignedScore(slope);
            case FALLING, HIGH_TO_LOW -> normalizeSignedScore(-slope);
            case STEADY -> 1.0d - Math.min(1.0d, Math.abs(slope));
        };
    }

    private static double scoreSectionBalance(List<RecommendableArrangement> orderedSet, int praiseCount) {
        if (orderedSet == null || orderedSet.isEmpty() || praiseCount <= 0 || praiseCount >= orderedSet.size()) {
            return 0.5d;
        }
        double praiseAverage = orderedSet.subList(0, praiseCount).stream().mapToInt(RecommendableArrangement::energy).average().orElse(50.0d);
        double worshipAverage = orderedSet.subList(praiseCount, orderedSet.size()).stream().mapToInt(RecommendableArrangement::energy).average().orElse(50.0d);
        if (praiseAverage >= worshipAverage) {
            return 1.0d;
        }
        double diff = Math.min(100.0d, worshipAverage - praiseAverage);
        return Math.max(0.0d, 1.0d - (diff / 100.0d));
    }

    private static List<String> tradeoffs(double shapeScore, double sectionBalance) {
        List<String> tradeoffs = new ArrayList<>();
        if (shapeScore < 0.5d) {
            tradeoffs.add("ENERGY_ARC_SHAPE_MISMATCH");
        }
        if (sectionBalance < 0.5d) {
            tradeoffs.add("ENERGY_ARC_PRAISE_WORSHIP_BALANCE_WEAK");
        }
        return tradeoffs;
    }

    private static int clampEnergy(int energy) {return Math.max(0, Math.min(energy, 100));}
    private static double normalizeSignedScore(double signed) {return Math.max(0.0d, Math.min(1.0d, (signed + 1.0d) / 2.0d));}

    private static ScoringComponentScore component(String code, double raw, Map<String, Double> weights) {
        double weight = weights.getOrDefault(code, 0.0d);
        return new ScoringComponentScore(code, raw, raw * weight);
    }
}
