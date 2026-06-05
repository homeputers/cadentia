package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ArrangementTeamRequirement;
import com.cadentia.reng.scoring.TeamSuitabilityModels.ExplicitTeamConstraints;
import com.cadentia.reng.scoring.TeamSuitabilityModels.FactStatus;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamAssignment;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamRequirementSlot;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamSuitabilityEvaluation;
import com.cadentia.reng.scoring.TeamSuitabilityModels.TeamSuitabilityFact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TeamSuitabilityEvaluator {

    public TeamSuitabilityEvaluation evaluate(RecommendableArrangement candidate, ScoringRequest request) {
        if (request == null || request.explicitTeamConstraints() == null) {
            return new TeamSuitabilityEvaluation(candidate.arrangementId(), List.of());
        }
        return evaluate(candidate, request.explicitTeamConstraints());
    }

    public TeamSuitabilityEvaluation evaluate(RecommendableArrangement candidate, ExplicitTeamConstraints constraints) {
        ArrangementTeamRequirement requirement = constraints.arrangementRequirements().get(candidate.arrangementId());
        if (requirement == null) {
            return new TeamSuitabilityEvaluation(candidate.arrangementId(), List.of());
        }

        List<TeamAssignment> assignments = constraints.assignments().stream()
                .sorted(Comparator.comparing(assignment -> assignment.musicianId().toString()))
                .toList();
        List<TeamSuitabilityFact> facts = new ArrayList<>();
        addUnavailableFact(assignments, facts);
        addIncompleteTeamFact(constraints, facts);
        addVocalConfigurationFact(requirement, assignments, facts);
        addSlotFacts(requirement.requiredSlots(), assignments, true, facts);
        addSlotFacts(requirement.optionalSlots(), assignments, false, facts);
        addBackingVocalFact(requirement, assignments, facts);
        addLeadVocalRangeFact(requirement, assignments, facts);
        return new TeamSuitabilityEvaluation(candidate.arrangementId(), facts);
    }

    public double scoringRawScore(RecommendableArrangement candidate, ScoringRequest request, ScoringProfile profile) {
        List<TeamSuitabilityFact> scoringFacts = evaluate(candidate, request).facts().stream()
                .filter(fact -> profile.teamConstraintMode(fact.code()) == TeamConstraintMode.SCORING_INPUT)
                .toList();
        if (scoringFacts.isEmpty()) {
            return 1.0d;
        }
        return scoringFacts.stream().mapToDouble(TeamSuitabilityEvaluator::scoreFact).average().orElse(1.0d);
    }

    private static void addUnavailableFact(List<TeamAssignment> assignments, List<TeamSuitabilityFact> facts) {
        long unavailable = assignments.stream().filter(assignment -> !assignment.activeForSuitability()).count();
        facts.add(new TeamSuitabilityFact(
                TeamConstraintCode.UNAVAILABLE_ASSIGNED_MUSICIAN,
                unavailable == 0 ? FactStatus.PASS : FactStatus.FAIL,
                "UNAVAILABLE_ASSIGNED_MUSICIAN",
                0,
                Math.toIntExact(unavailable)));
    }

    private static void addIncompleteTeamFact(ExplicitTeamConstraints constraints, List<TeamSuitabilityFact> facts) {
        facts.add(new TeamSuitabilityFact(
                TeamConstraintCode.INCOMPLETE_TEAM,
                constraints.incompleteTeam() ? FactStatus.WARNING : FactStatus.PASS,
                "INCOMPLETE_TEAM",
                0,
                constraints.incompleteTeam() ? 1 : 0));
    }

    private static void addVocalConfigurationFact(
            ArrangementTeamRequirement requirement,
            List<TeamAssignment> assignments,
            List<TeamSuitabilityFact> facts) {
        if (!requirement.vocalConfigurationRequired()) {
            return;
        }
        boolean hasLead = activeAssignments(assignments).stream()
                .anyMatch(assignment -> hasVocalPart(assignment, "LEAD"));
        facts.add(new TeamSuitabilityFact(
                TeamConstraintCode.MISSING_VOCAL_CONFIGURATION,
                hasLead ? FactStatus.PASS : FactStatus.FAIL,
                "MISSING_VOCAL_CONFIGURATION",
                1,
                hasLead ? 1 : 0));
    }

    private static void addSlotFacts(
            List<TeamRequirementSlot> slots,
            List<TeamAssignment> assignments,
            boolean required,
            List<TeamSuitabilityFact> facts) {
        for (TeamRequirementSlot slot : slots) {
            int actual = countMatches(slot, assignments);
            TeamConstraintCode code = codeForSlot(slot, required);
            FactStatus status = actual >= slot.minimumCount()
                    ? FactStatus.PASS
                    : required ? FactStatus.FAIL : FactStatus.WARNING;
            facts.add(new TeamSuitabilityFact(
                    code,
                    status,
                    diagnosticCode(code, slot),
                    slot.minimumCount(),
                    actual));
        }
    }

    private static void addBackingVocalFact(
            ArrangementTeamRequirement requirement,
            List<TeamAssignment> assignments,
            List<TeamSuitabilityFact> facts) {
        if (requirement.requiredBackingVocalCount() <= 0) {
            return;
        }
        int actual = (int) activeAssignments(assignments).stream()
                .filter(TeamSuitabilityEvaluator::hasBackingVocalPart)
                .count();
        facts.add(new TeamSuitabilityFact(
                TeamConstraintCode.MISSING_VOCAL_CONFIGURATION,
                actual >= requirement.requiredBackingVocalCount() ? FactStatus.PASS : FactStatus.FAIL,
                "BACKING_VOCAL_COVERAGE",
                requirement.requiredBackingVocalCount(),
                actual));
    }

    private static void addLeadVocalRangeFact(
            ArrangementTeamRequirement requirement,
            List<TeamAssignment> assignments,
            List<TeamSuitabilityFact> facts) {
        if (requirement.leadVocalLowMidiNote() == null || requirement.leadVocalHighMidiNote() == null) {
            return;
        }
        List<TeamAssignment> leadAssignments = activeAssignments(assignments).stream()
                .filter(assignment -> hasVocalPart(assignment, "LEAD"))
                .toList();
        boolean anyFit = leadAssignments.stream().anyMatch(assignment -> assignment.comfortableLowMidiNote() != null
                && assignment.comfortableHighMidiNote() != null
                && assignment.comfortableLowMidiNote() <= requirement.leadVocalLowMidiNote()
                && assignment.comfortableHighMidiNote() >= requirement.leadVocalHighMidiNote());
        facts.add(new TeamSuitabilityFact(
                TeamConstraintCode.LEAD_VOCAL_RANGE_MISMATCH,
                anyFit ? FactStatus.PASS : FactStatus.FAIL,
                "LEAD_VOCAL_RANGE_MISMATCH",
                1,
                anyFit ? 1 : 0));
    }

    private static int countMatches(TeamRequirementSlot slot, List<TeamAssignment> assignments) {
        return (int) activeAssignments(assignments).stream().filter(assignment -> matches(slot, assignment)).count();
    }

    private static boolean matches(TeamRequirementSlot slot, TeamAssignment assignment) {
        if (slot.roleCode() != null && !hasRole(assignment, slot.roleCode())) {
            return false;
        }
        if (slot.instrumentCode() != null && !hasInstrument(assignment, slot.instrumentCode())) {
            return false;
        }
        if (slot.vocalPartCode() != null && !hasVocalPart(assignment, slot.vocalPartCode())) {
            return false;
        }
        if (slot.minimumSkillRank() <= 0) {
            return true;
        }
        return skillRank(assignment, slot) >= slot.minimumSkillRank();
    }

    private static int skillRank(TeamAssignment assignment, TeamRequirementSlot slot) {
        if (slot.instrumentCode() != null) {
            return assignment.instrumentSkillRanks().getOrDefault(normalize(slot.instrumentCode()), 0);
        }
        if (slot.vocalPartCode() != null) {
            return assignment.vocalSkillRanks().getOrDefault(normalize(slot.vocalPartCode()), 0);
        }
        return 0;
    }

    private static TeamConstraintCode codeForSlot(TeamRequirementSlot slot, boolean required) {
        if (slot.minimumSkillRank() > 0) {
            return TeamConstraintCode.INSUFFICIENT_SKILL_COVERAGE;
        }
        if (!required) {
            return TeamConstraintCode.OPTIONAL_INSTRUMENT_FIT;
        }
        return slot.vocalPartCode() == null
                ? TeamConstraintCode.MISSING_REQUIRED_INSTRUMENT
                : TeamConstraintCode.MISSING_VOCAL_CONFIGURATION;
    }

    private static String diagnosticCode(TeamConstraintCode code, TeamRequirementSlot slot) {
        String value = slot.instrumentCode() != null ? slot.instrumentCode() : slot.vocalPartCode();
        return value == null ? code.name() : code.name() + "_" + normalize(value);
    }

    private static List<TeamAssignment> activeAssignments(List<TeamAssignment> assignments) {
        return assignments.stream().filter(TeamAssignment::activeForSuitability).toList();
    }

    private static boolean hasRole(TeamAssignment assignment, String roleCode) {
        return assignment.roleCodes().contains(normalize(roleCode));
    }

    private static boolean hasInstrument(TeamAssignment assignment, String instrumentCode) {
        return assignment.instrumentCodes().contains(normalize(instrumentCode));
    }

    private static boolean hasVocalPart(TeamAssignment assignment, String vocalPartCode) {
        return assignment.vocalPartCodes().contains(normalize(vocalPartCode));
    }

    private static boolean hasBackingVocalPart(TeamAssignment assignment) {
        return assignment.vocalPartCodes().stream()
                .anyMatch(part -> List.of("ALTO", "TENOR", "BARITONE", "SOPRANO", "BACKGROUND").contains(part));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static double scoreFact(TeamSuitabilityFact fact) {
        return switch (fact.status()) {
            case PASS -> 1.0d;
            case WARNING -> fact.code() == TeamConstraintCode.OPTIONAL_INSTRUMENT_FIT ? optionalFitScore(fact) : 0.5d;
            case FAIL -> 0.0d;
        };
    }

    private static double optionalFitScore(TeamSuitabilityFact fact) {
        if (fact.requiredCount() <= 0) {
            return 1.0d;
        }
        return Math.min(1.0d, Math.max(0.0d, fact.actualCount() / (double) fact.requiredCount()));
    }
}
