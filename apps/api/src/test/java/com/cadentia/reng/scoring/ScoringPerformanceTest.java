package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoringPerformanceTest {

    @Test
    void scoringPipelineCompletesWithinLatencyBudgetForRepresentativeCatalog() {
        List<RecommendableArrangement> candidates = new ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            candidates.add(new RecommendableArrangement(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Song-" + i, "en", i % 2 == 0 ? "G" : "D",
                    KeyMode.MAJOR, 90 + (i % 40), "4/4", 50 + (i % 40), List.of(i % 3 == 0 ? "praise" : "worship"),
                    List.of(), List.of(), approvedSummary()));
        }

        HardConstraintFilter filter = new HardConstraintFilter();
        CandidateFeatureScorer scorer = new CandidateFeatureScorer();
        long started = System.nanoTime();

        HardFilterResult filtered = filter.filter(candidates, request());
        List<CandidateFeatureScorer.CandidateFeatureScore> scores =
                scorer.scoreCandidates(filtered.eligibleCandidates(), request(), profile());

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(filtered.eligibleCandidates()).hasSize(1500);
        assertThat(scores).hasSize(1500);
        assertThat(elapsedMs).isLessThan(400L);
    }

    private static ScoringRequest request() {
        return new ScoringRequest("Psalm 24", List.of("holiness"), 10, 5,
                new ScoringRequest.KeyPolicy(true, true, 2), new ScoringRequest.TempoPolicy(12),
                null, "en", List.of(), false, new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile profile() {
        return new ScoringProfile("v1", Map.of(
                CandidateFeatureScorer.THEME_MATCH, 0.3d,
                CandidateFeatureScorer.SCRIPTURE_MATCH, 0.2d,
                CandidateFeatureScorer.ROLE_FIT, 0.2d,
                CandidateFeatureScorer.MUSICAL_FIT, 0.1d,
                CandidateFeatureScorer.ENERGY_FIT, 0.1d,
                CandidateFeatureScorer.METADATA_CONFIDENCE, 0.1d), List.of("total_score"));
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED);
    }
}
