package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ArrangementTeamRequirement;
import com.cadentia.reng.scoring.TeamSuitabilityModels.AssignmentStatus;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ExplicitTeamConstraints;
import com.cadentia.reng.scoring.TeamSuitabilityModels.FactStatus;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamAssignment;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamRequirementSlot;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamSuitabilityEvaluation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamSuitabilityEvaluatorTest {

    private final TeamSuitabilityEvaluator evaluator = new TeamSuitabilityEvaluator();

    @Test
    void evaluatesMissingRequiredInstrumentsAsDeterministicFailure() {
        RecommendableArrangement candidate = candidate();
        ExplicitTeamConstraints constraints = constraints(candidate, requirement(
                List.of(new TeamRequirementSlot(null, "DRUMS", null, 0, 1)),
                List.of(),
                false,
                null,
                null,
                0),
                List.of(assignment(Set.of("ACOUSTIC_GUITAR"), Set.of(), Map.of(), Map.of(), true, 48, 72)),
                false);

        TeamSuitabilityEvaluation evaluation = evaluator.evaluate(candidate, constraints);

        assertThat(evaluation.facts())
                .filteredOn(fact -> fact.code() == TeamConstraintCode.MISSING_REQUIRED_INSTRUMENT)
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.status()).isEqualTo(FactStatus.FAIL);
                    assertThat(fact.requiredCount()).isEqualTo(1);
                    assertThat(fact.actualCount()).isZero();
                });
    }

    @Test
    void scoresOptionalInstrumentFitWithoutFilteringCandidate() {
        RecommendableArrangement candidate = candidate();
        ScoringRequest request = request(constraints(candidate, requirement(
                List.of(),
                List.of(new TeamRequirementSlot(null, "ELECTRIC_GUITAR", null, 0, 1)),
                false,
                null,
                null,
                0),
                List.of(assignment(Set.of("ACOUSTIC_GUITAR"), Set.of(), Map.of(), Map.of(), true, 48, 72)),
                false));
        ScoringProfile profile = teamProfile(Map.of(TeamConstraintCode.OPTIONAL_INSTRUMENT_FIT, TeamConstraintMode.SCORING_INPUT));

        double score = evaluator.scoringRawScore(candidate, request, profile);

        assertThat(score).isZero();
    }

    @Test
    void evaluatesInsufficientSkillLevelsSeparatelyFromInstrumentPresence() {
        RecommendableArrangement candidate = candidate();
        ExplicitTeamConstraints constraints = constraints(candidate, requirement(
                List.of(new TeamRequirementSlot(null, "PIANO", null, 3, 1)),
                List.of(),
                false,
                null,
                null,
                0),
                List.of(assignment(Set.of("PIANO"), Set.of(), Map.of("PIANO", 2), Map.of(), true, 48, 72)),
                false);

        TeamSuitabilityEvaluation evaluation = evaluator.evaluate(candidate, constraints);

        assertThat(evaluation.facts())
                .filteredOn(fact -> fact.code() == TeamConstraintCode.INSUFFICIENT_SKILL_COVERAGE)
                .singleElement()
                .extracting(TeamSuitabilityModels.TeamSuitabilityFact::status)
                .isEqualTo(FactStatus.FAIL);
    }

    @Test
    void evaluatesLeadVocalRangeMismatch() {
        RecommendableArrangement candidate = candidate();
        ExplicitTeamConstraints constraints = constraints(candidate, requirement(
                List.of(),
                List.of(),
                true,
                48,
                76,
                0),
                List.of(assignment(Set.of(), Set.of("LEAD"), Map.of(), Map.of("LEAD", 3), true, 52, 72)),
                false);

        TeamSuitabilityEvaluation evaluation = evaluator.evaluate(candidate, constraints);

        assertThat(evaluation.facts())
                .filteredOn(fact -> fact.code() == TeamConstraintCode.LEAD_VOCAL_RANGE_MISMATCH)
                .singleElement()
                .extracting(TeamSuitabilityModels.TeamSuitabilityFact::status)
                .isEqualTo(FactStatus.FAIL);
    }

    @Test
    void evaluatesUnavailableAssignedMusiciansAndIncompleteTeams() {
        RecommendableArrangement candidate = candidate();
        ExplicitTeamConstraints constraints = constraints(candidate, requirement(
                List.of(),
                List.of(),
                false,
                null,
                null,
                0),
                List.of(assignment(Set.of("DRUMS"), Set.of(), Map.of("DRUMS", 3), Map.of(), false, 48, 72)),
                true);

        TeamSuitabilityEvaluation evaluation = evaluator.evaluate(candidate, constraints);

        assertThat(evaluation.facts())
                .filteredOn(fact -> fact.code() == TeamConstraintCode.UNAVAILABLE_ASSIGNED_MUSICIAN)
                .singleElement()
                .extracting(TeamSuitabilityModels.TeamSuitabilityFact::status)
                .isEqualTo(FactStatus.FAIL);
        assertThat(evaluation.facts())
                .filteredOn(fact -> fact.code() == TeamConstraintCode.INCOMPLETE_TEAM)
                .singleElement()
                .extracting(TeamSuitabilityModels.TeamSuitabilityFact::status)
                .isEqualTo(FactStatus.WARNING);
    }

    @Test
    void disabledTeamConstraintsDoNotFilterOrAffectScores() {
        RecommendableArrangement candidate = candidate();
        ScoringRequest request = request(constraints(candidate, requirement(
                List.of(new TeamRequirementSlot(null, "DRUMS", null, 0, 1)),
                List.of(),
                false,
                null,
                null,
                0),
                List.of(),
                false));
        ScoringProfile profile = teamProfile(Map.of(TeamConstraintCode.MISSING_REQUIRED_INSTRUMENT, TeamConstraintMode.DISABLED));

        HardFilterResult filtered = new HardConstraintFilter().filter(List.of(candidate), request, profile);
        double score = evaluator.scoringRawScore(candidate, request, profile);

        assertThat(filtered.eligibleCandidates()).containsExactly(candidate);
        assertThat(score).isEqualTo(1.0d);
    }

    @Test
    void hardFilterModeExcludesFailingTeamCandidatesAfterBaseEligibility() {
        RecommendableArrangement candidate = candidate();
        ScoringRequest request = request(constraints(candidate, requirement(
                List.of(new TeamRequirementSlot(null, "DRUMS", null, 0, 1)),
                List.of(),
                false,
                null,
                null,
                0),
                List.of(),
                false));
        ScoringProfile profile = teamProfile(Map.of(
                TeamConstraintCode.MISSING_REQUIRED_INSTRUMENT,
                TeamConstraintMode.HARD_FILTER));

        HardFilterResult filtered = new HardConstraintFilter().filter(List.of(candidate), request, profile);

        assertThat(filtered.eligibleCandidates()).isEmpty();
        assertThat(filtered.excludedCandidates())
                .singleElement()
                .extracting(HardFilterResult.ExcludedCandidate::reasonCodes)
                .asList()
                .containsExactly(HardFilterReasonCode.TEAM_MISSING_REQUIRED_INSTRUMENT);
    }

    private static ExplicitTeamConstraints constraints(
            RecommendableArrangement candidate,
            ArrangementTeamRequirement requirement,
            List<TeamAssignment> assignments,
            boolean incompleteTeam) {
        return new ExplicitTeamConstraints(
                UUID.randomUUID(),
                assignments,
                Map.of(candidate.arrangementId(), requirement),
                incompleteTeam);
    }

    private static ArrangementTeamRequirement requirement(
            List<TeamRequirementSlot> requiredSlots,
            List<TeamRequirementSlot> optionalSlots,
            boolean vocalConfigurationRequired,
            Integer leadLow,
            Integer leadHigh,
            int backingVocalCount) {
        return new ArrangementTeamRequirement(
                requiredSlots,
                optionalSlots,
                "LEAD_WITH_BACKING",
                vocalConfigurationRequired,
                leadLow,
                leadHigh,
                backingVocalCount);
    }

    private static TeamAssignment assignment(
            Set<String> instruments,
            Set<String> vocalParts,
            Map<String, Integer> instrumentSkills,
            Map<String, Integer> vocalSkills,
            boolean available,
            Integer low,
            Integer high) {
        return new TeamAssignment(
                UUID.randomUUID(),
                available ? AssignmentStatus.ACCEPTED : AssignmentStatus.UNAVAILABLE,
                available,
                Set.of(),
                instruments,
                vocalParts,
                instrumentSkills,
                vocalSkills,
                low,
                high);
    }

    private static ScoringRequest request(ExplicitTeamConstraints constraints) {
        return new ScoringRequest(
                "Psalm 24",
                List.of("holiness"),
                10,
                5,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false),
                null,
                constraints);
    }

    private static ScoringProfile teamProfile(Map<TeamConstraintCode, TeamConstraintMode> modes) {
        return new ScoringProfile(
                "team-v1",
                Map.of(CandidateFeatureScorer.TEAM_SUITABILITY, 1.0d),
                List.of("total_score", "song_id", "arrangement_id"),
                ScoringProfileLifecycle.active(),
                modes);
    }

    private static RecommendableArrangement candidate() {
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
                70,
                List.of("praise"),
                List.of(),
                List.of(),
                approvedSummary());
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED);
    }
}
