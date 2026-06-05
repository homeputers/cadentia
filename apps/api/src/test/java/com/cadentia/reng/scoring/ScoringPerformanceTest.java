package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringPerformanceTest {

    @ParameterizedTest
    @CsvSource({
        "small,400,180,280",
        "medium,1500,400,550",
        "large,3000,700,900"
    })
    void scoringPipelineMeetsLatencySloByCatalogSize(String catalogSize, int candidateCount, long p50Ms, long p95Ms) {
        List<Long> samples = benchmarkMatrixRun(candidateCount, requestBaselineDefaults());
        long measuredP50 = percentile(samples, 0.50d);
        long measuredP95 = percentile(samples, 0.95d);

        assertThat(samples).as("matrix " + catalogSize + " should have samples").hasSize(7);
        assertThat(measuredP50).as("p50 slo for " + catalogSize).isLessThanOrEqualTo(p50Ms);
        assertThat(measuredP95).as("p95 slo for " + catalogSize).isLessThanOrEqualTo(p95Ms);
    }

    @Test
    void teamSuitabilityScoringDoesNotRegressRepresentativeCandidateRetrieval() {
        int candidateCount = 1500;
        List<RecommendableArrangement> candidates = buildCandidates(candidateCount);
        ScoringRequest request = teamSuitabilityRequest(candidates);
        HardConstraintFilter filter = new HardConstraintFilter();
        CandidateFeatureScorer scorer = new CandidateFeatureScorer();

        long started = System.nanoTime();
        HardFilterResult filtered = filter.filter(candidates, request, teamSuitabilityProfile());
        List<CandidateFeatureScorer.CandidateFeatureScore> scores =
                scorer.scoreCandidates(filtered.eligibleCandidates(), request, teamSuitabilityProfile());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(scores).hasSize(candidateCount);
        assertThat(elapsedMs).isLessThanOrEqualTo(650);
    }

    @Test
    void scoringPipelineMeetsComplexityProfilesAtMediumCatalogSize() {
        int candidateCount = 1500;
        assertThat(percentile(benchmarkMatrixRun(candidateCount, requestBaselineDefaults()), 0.95d)).isLessThanOrEqualTo(550);
        assertThat(percentile(benchmarkMatrixRun(candidateCount, requestThemeDense()), 0.95d)).isLessThanOrEqualTo(600);
        assertThat(percentile(benchmarkMatrixRun(candidateCount, requestConstraintHeavy()), 0.95d)).isLessThanOrEqualTo(650);
    }

    private static ScoringRequest requestBaselineDefaults() {
        return new ScoringRequest("Psalm 24", List.of("holiness"), 10, 5,
                new ScoringRequest.KeyPolicy(true, true, 2), new ScoringRequest.TempoPolicy(12),
                null, "en", List.of(), false, new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringRequest requestThemeDense() {
        return new ScoringRequest("Psalm 24", List.of("holiness", "majesty", "kingdom", "praise", "worship"), 10, 5,
                new ScoringRequest.KeyPolicy(true, true, 2), new ScoringRequest.TempoPolicy(12),
                null, "en", List.of(), false, new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringRequest requestConstraintHeavy() {
        return new ScoringRequest("Psalm 24", List.of("holiness"), 10, 5,
                new ScoringRequest.KeyPolicy(true, false, 1), new ScoringRequest.TempoPolicy(6),
                null, "en", List.of(), false, new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static List<Long> benchmarkMatrixRun(int candidateCount, ScoringRequest request) {
        List<RecommendableArrangement> candidates = buildCandidates(candidateCount);
        HardConstraintFilter filter = new HardConstraintFilter();
        CandidateFeatureScorer scorer = new CandidateFeatureScorer();
        List<Long> elapsedMs = new ArrayList<>();
        for (int run = 0; run < 7; run++) {
            long started = System.nanoTime();
            HardFilterResult filtered = filter.filter(candidates, request);
            List<CandidateFeatureScorer.CandidateFeatureScore> scores =
                    scorer.scoreCandidates(filtered.eligibleCandidates(), request, profile());
            long elapsed = (System.nanoTime() - started) / 1_000_000;
            assertThat(scores).hasSize(candidateCount);
            elapsedMs.add(elapsed);
        }
        return elapsedMs;
    }

    private static ScoringRequest teamSuitabilityRequest(List<RecommendableArrangement> candidates) {
        Map<UUID, TeamSuitabilityModels.ArrangementTeamRequirement> requirements = candidates.stream()
                .collect(Collectors.toUnmodifiableMap(
                        RecommendableArrangement::arrangementId,
                        candidate -> new TeamSuitabilityModels.ArrangementTeamRequirement(
                                List.of(new TeamSuitabilityModels.TeamRequirementSlot(null, "DRUMS", null, 2, 1)),
                                List.of(new TeamSuitabilityModels.TeamRequirementSlot(null, "ELECTRIC_GUITAR", null, 0, 1)),
                                "LEAD_WITH_BACKING",
                                true,
                                48,
                                72,
                                1)));
        TeamSuitabilityModels.ExplicitTeamConstraints constraints = new TeamSuitabilityModels.ExplicitTeamConstraints(
                UUID.randomUUID(),
                List.of(
                        new TeamSuitabilityModels.TeamAssignment(
                                UUID.randomUUID(),
                                TeamSuitabilityModels.AssignmentStatus.ACCEPTED,
                                true,
                                Set.of("INSTRUMENTALIST"),
                                Set.of("DRUMS"),
                                Set.of(),
                                Map.of("DRUMS", 3),
                                Map.of(),
                                null,
                                null),
                        new TeamSuitabilityModels.TeamAssignment(
                                UUID.randomUUID(),
                                TeamSuitabilityModels.AssignmentStatus.ACCEPTED,
                                true,
                                Set.of("VOCALIST", "INSTRUMENTALIST"),
                                Set.of("ELECTRIC_GUITAR"),
                                Set.of("LEAD", "BACKGROUND"),
                                Map.of("ELECTRIC_GUITAR", 2),
                                Map.of("LEAD", 3, "BACKGROUND", 2),
                                45,
                                76)),
                requirements,
                false);
        return new ScoringRequest("Psalm 24", List.of("holiness"), 10, 5,
                new ScoringRequest.KeyPolicy(true, true, 2), new ScoringRequest.TempoPolicy(12),
                null, "en", List.of(), false, new ScoringRequest.DefaultsApplied(false, false, false, false),
                null, constraints);
    }

    private static ScoringProfile teamSuitabilityProfile() {
        return new ScoringProfile("team-v1", Map.of(
                CandidateFeatureScorer.THEME_MATCH, 0.3d,
                CandidateFeatureScorer.SCRIPTURE_MATCH, 0.2d,
                CandidateFeatureScorer.ROLE_FIT, 0.2d,
                CandidateFeatureScorer.MUSICAL_FIT, 0.1d,
                CandidateFeatureScorer.ENERGY_FIT, 0.1d,
                CandidateFeatureScorer.METADATA_CONFIDENCE, 0.1d,
                CandidateFeatureScorer.TEAM_SUITABILITY, 0.2d), List.of("total_score"),
                ScoringProfileLifecycle.active(),
                Map.of(
                        TeamConstraintCode.OPTIONAL_INSTRUMENT_FIT, TeamConstraintMode.SCORING_INPUT,
                        TeamConstraintCode.MISSING_REQUIRED_INSTRUMENT, TeamConstraintMode.WARNING_ONLY,
                        TeamConstraintCode.MISSING_VOCAL_CONFIGURATION, TeamConstraintMode.WARNING_ONLY,
                        TeamConstraintCode.LEAD_VOCAL_RANGE_MISMATCH, TeamConstraintMode.WARNING_ONLY,
                        TeamConstraintCode.INSUFFICIENT_SKILL_COVERAGE, TeamConstraintMode.WARNING_ONLY));
    }

    private static List<RecommendableArrangement> buildCandidates(int count) {
        List<RecommendableArrangement> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candidates.add(new RecommendableArrangement(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Song-" + i, "en", i % 2 == 0 ? "G" : "D",
                    KeyMode.MAJOR, 90 + (i % 40), "4/4", 50 + (i % 40), List.of(i % 3 == 0 ? "praise" : "worship"),
                    List.of(), List.of(), approvedSummary()));
        }
        return candidates;
    }

    private static long percentile(List<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
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
