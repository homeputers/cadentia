package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ConstraintContribution;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ConstraintContributionType;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.PluginContributionMetadata;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.PluginContributionRequest;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.PluginContributionSet;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ScoringAdjustment;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationPluginContributionServiceTest {
    private final RecommendationPluginContributionService service = new RecommendationPluginContributionService(List.of(), List.of(), false);

    @Test
    void rejectsContributionsForUnapprovedCandidatesAndInvalidScores() {
        RecommendableArrangement approved = candidate("00000000-0000-0000-0000-000000000101");
        UUID outsideApprovedSet = UUID.fromString("00000000-0000-0000-0000-000000000999");

        var validated = service.validated(
                contributionRequest(List.of(approved)),
                new PluginContributionSet(
                        List.of(constraint(outsideApprovedSet, ConstraintContributionType.HARD_REJECT, 0.0d)),
                        List.of(scoring(approved.arrangementId(), Double.POSITIVE_INFINITY), scoring(outsideApprovedSet, 0.1d)),
                        List.of()));

        assertThat(validated.constraintsByArrangement()).isEmpty();
        assertThat(validated.scoringDeltaByArrangement()).isEmpty();
        assertThat(validated.safeErrors()).containsOnly("PLUGIN_CONSTRAINT_INVALID", "PLUGIN_SCORING_INVALID", "PLUGIN_SCORING_INVALID");
    }

    @Test
    void appliesBoundedPluginScoreBeforeStableCoreTieBreaking() {
        RecommendableArrangement first = candidate("00000000-0000-0000-0000-000000000001");
        RecommendableArrangement second = candidate("00000000-0000-0000-0000-000000000002");
        var validated = service.validated(
                contributionRequest(List.of(first, second)),
                new PluginContributionSet(
                        List.of(),
                        List.of(scoring(second.arrangementId(), 0.2d), scoring(second.arrangementId(), 0.2d)),
                        List.of()));

        List<CandidateFeatureScorer.CandidateFeatureScore> scores = new CandidateFeatureScorer()
                .scoreCandidates(List.of(first, second), request(), scoringProfile(), validated);

        assertThat(validated.scoringDeltaByArrangement().get(second.arrangementId())).isEqualTo(0.2d);
        assertThat(scores).extracting(score -> score.candidate().arrangementId()).containsExactly(second.arrangementId(), first.arrangementId());
        assertThat(scores.get(0).componentScores())
                .filteredOn(component -> component.componentCode().equals(RecommendationPluginContributionModels.PLUGIN_COMPONENT_CODE))
                .singleElement()
                .satisfies(component -> assertThat(component.reasonCodes()).containsExactly(
                        "PLUGIN:cadentia.test:v1:cfg-1:LOCAL_POLICY_MATCH"));
    }

    @Test
    void hardRejectConstraintRemovesCandidateAfterCoreEligibility() {
        RecommendableArrangement candidate = candidate("00000000-0000-0000-0000-000000000301");
        var validated = service.validated(
                contributionRequest(List.of(candidate)),
                new PluginContributionSet(
                        List.of(constraint(candidate.arrangementId(), ConstraintContributionType.HARD_REJECT, 0.0d)),
                        List.of(),
                        List.of()));

        HardFilterResult result = new HardConstraintFilter().filter(List.of(candidate), request(), scoringProfile(), validated);

        assertThat(result.eligibleCandidates()).isEmpty();
        assertThat(result.excludedCandidates())
                .singleElement()
                .extracting(HardFilterResult.ExcludedCandidate::reasonCodes)
                .asList()
                .containsExactly(HardFilterReasonCode.PLUGIN_HARD_REJECT);
    }

    @Test
    void pluginFailuresAreSafeErrorsAndDoNotRemoveRecommendationsWhenFailOpen() {
        RecommendableArrangement candidate = candidate("00000000-0000-0000-0000-000000000401");
        RecommendationPluginContributionService failOpen = new RecommendationPluginContributionService(
                List.of(request -> { throw new IllegalStateException("boom"); }),
                List.of(),
                false);

        PluginContributionSet contributions = failOpen.collect(contributionRequest(List.of(candidate)));
        HardFilterResult result = new HardConstraintFilter().filter(List.of(candidate), request(), scoringProfile(), failOpen.validated(
                contributionRequest(List.of(candidate)), contributions));

        assertThat(contributions.safeErrors()).containsExactly("PLUGIN_CONSTRAINT_FAILED");
        assertThat(result.eligibleCandidates()).containsExactly(candidate);
    }

    private static ConstraintContribution constraint(UUID arrangementId, ConstraintContributionType type, double delta) {
        return new ConstraintContribution(arrangementId, type, delta, "LOCAL_POLICY_MATCH", metadata(), List.of());
    }

    private static ScoringAdjustment scoring(UUID arrangementId, double delta) {
        return new ScoringAdjustment(arrangementId, delta, "local_policy", "LOCAL_POLICY_MATCH", metadata(), List.of());
    }

    private static PluginContributionRequest contributionRequest(List<RecommendableArrangement> candidates) {
        return new PluginContributionRequest(request(), scoringProfile(), "local", "policy-v1", candidates);
    }

    private static PluginContributionMetadata metadata() {
        return new PluginContributionMetadata("cadentia.test", "v1", "cfg-1");
    }

    private static ScoringRequest request() {
        return new ScoringRequest("Psalm 100", List.of("praise"), 10, 5,
                new ScoringRequest.KeyPolicy(true, true, 2), new ScoringRequest.TempoPolicy(12), null, "en",
                List.of(), false, new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile scoringProfile() {
        return new ScoringProfile("v1", Map.of(CandidateFeatureScorer.ROLE_FIT, 1.0d), List.of("total_score", "song_id", "arrangement_id"));
    }

    private static RecommendableArrangement candidate(String id) {
        UUID uuid = UUID.fromString(id);
        return new RecommendableArrangement(uuid, uuid, UUID.randomUUID(), "Song", "en", "G", KeyMode.MAJOR, 120, "4/4", 70,
                List.of("praise"), approvedSummary());
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED, ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED);
    }
}
