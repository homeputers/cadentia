package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnergyArcEvaluatorTest {

    private final EnergyArcEvaluator evaluator = new EnergyArcEvaluator();

    @Test
    void evaluateRisingArcScoresHigherForIncreasingEnergy() {
        EnergyArcEvaluation evaluation = evaluator.evaluate(
                List.of(candidate(30), candidate(50), candidate(80)),
                request("rising"),
                profile());

        assertThat(evaluation.arc()).isEqualTo(EnergyArc.RISING);
        assertThat(evaluation.totalScore()).isGreaterThan(1.0d);
    }

    @Test
    void evaluateFallingArcAddsTradeoffWhenEnergyRises() {
        EnergyArcEvaluation evaluation = evaluator.evaluate(
                List.of(candidate(25), candidate(40), candidate(75)),
                request("falling"),
                profile());

        assertThat(evaluation.tradeoffCodes()).contains("ENERGY_ARC_SHAPE_MISMATCH");
    }

    @Test
    void evaluateSteadyArcRewardsBalancedShape() {
        EnergyArcEvaluation evaluation = evaluator.evaluate(
                List.of(candidate(60), candidate(58), candidate(62)),
                request("steady"),
                profile());

        assertThat(evaluation.totalScore()).isGreaterThan(1.8d);
    }

    @Test
    void evaluateDefaultsArcWhenUnspecified() {
        EnergyArcEvaluation evaluation = evaluator.evaluate(
                List.of(candidate(50), candidate(50), candidate(50)),
                request(null),
                profile());

        assertThat(evaluation.arc()).isEqualTo(EnergyArc.STEADY);
        assertThat(evaluation.arcVersion()).isEqualTo(EnergyArc.VERSION);
    }

    private static ScoringRequest request(String arc) {
        return new ScoringRequest(
                null,
                List.of(),
                2,
                1,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                arc,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile profile() {
        return new ScoringProfile(
                "v1",
                Map.of(
                        EnergyArcEvaluator.ARC_SHAPE_MATCH, 1.0d,
                        EnergyArcEvaluator.ARC_SECTION_BALANCE, 1.0d),
                List.of("total_score"));
    }

    private static RecommendableArrangement candidate(int energy) {
        return new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song",
                "en",
                "G",
                KeyMode.MAJOR,
                120,
                "4/4",
                energy,
                List.of("praise"),
                List.of(),
                List.of(),
                new ApprovalGateSummary(
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED));
    }
}
