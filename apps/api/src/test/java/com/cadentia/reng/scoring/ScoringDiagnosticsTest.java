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

class ScoringDiagnosticsTest {

    @Test
    void fromAggregatesPhaseCountsScoreRangesTradeoffsAndSummary() {
        RecommendableArrangement eligible = candidate("G", 120, "4/4");
        RecommendableArrangement excluded = new RecommendableArrangement(
                UUID.randomUUID(), UUID.randomUUID(), null, "Missing Prov", "en", "A", KeyMode.MAJOR, 110, "4/4", 60,
                List.of("praise"), List.of(), List.of(), approvedSummary());
        HardFilterResult hardFilterResult = new HardConstraintFilter().filter(
                List.of(eligible, excluded), request());

        CandidateFeatureScorer scorer = new CandidateFeatureScorer();
        List<CandidateFeatureScorer.CandidateFeatureScore> candidateScores =
                scorer.scoreCandidates(hardFilterResult.eligibleCandidates(), request(), scoringProfile());

        TransitionScore transitionScore = new TransitionScore(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ScoringComponentScore(TransitionScorer.KEY_MODULATION, -1.0d, -0.2d)),
                -0.2d);

        OrderedSetResponse selected = OrderedSetResponse.of(scoringProfile(), "snap-v1", List.of(), 1.25d);

        ScoringDiagnostics diagnostics = ScoringDiagnostics.from(
                List.of(eligible, excluded),
                hardFilterResult,
                candidateScores,
                List.of(transitionScore),
                selected,
                true);

        assertThat(diagnostics.enabled()).isTrue();
        assertThat(diagnostics.retrievedCandidateCount()).isEqualTo(2);
        assertThat(diagnostics.eligibleCandidateCount()).isEqualTo(1);
        assertThat(diagnostics.excludedCandidateCount()).isEqualTo(1);
        assertThat(diagnostics.exclusionReasonCounts()).containsEntry(HardFilterReasonCode.MISSING_PROVENANCE, 1);
        assertThat(diagnostics.candidateScoreRange().max()).isGreaterThan(0d);
        assertThat(diagnostics.transitionTradeoffCodes()).containsExactly(TransitionScorer.KEY_MODULATION);
        assertThat(diagnostics.selectedSetSummary().totalScore()).isEqualTo(1.25d);
    }

    @Test
    void disabledDiagnosticsDoNotMutateSelectionOutput() {
        OrderedSetResponse selected = OrderedSetResponse.of(scoringProfile(), "snap-v1", List.of(), 2.0d);

        ScoringDiagnostics diagnostics = ScoringDiagnostics.from(
                List.of(),
                new HardFilterResult(List.of(), List.of(), new HardFilterResult.CountRequirement(10, 5)),
                List.of(),
                List.of(),
                selected,
                false);

        assertThat(diagnostics.enabled()).isFalse();
        assertThat(selected.totalScore()).isEqualTo(2.0d);
        assertThat(selected.scoringProfileVersion()).isEqualTo("v1");
    }

    private static RecommendableArrangement candidate(String key, int bpm, String meter) {
        return new RecommendableArrangement(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Song", "en", key, KeyMode.MAJOR, bpm, meter, 70,
                List.of("praise"), List.of(), List.of(), approvedSummary());
    }

    private static ScoringRequest request() {
        return new ScoringRequest("Psalm 24", List.of("holiness"), 10, 5,
                new ScoringRequest.KeyPolicy(true, true, 2), new ScoringRequest.TempoPolicy(12),
                null, "en", List.of(), new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile scoringProfile() {
        return new ScoringProfile("v1", Map.of(CandidateFeatureScorer.ROLE_FIT, 1.0d), List.of("total_score"));
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED);
    }
}
