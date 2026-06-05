package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TeamSuitabilityModels {

    private TeamSuitabilityModels() {
    }

    public enum AssignmentStatus {
        REQUESTED,
        TENTATIVE,
        ACCEPTED,
        DECLINED,
        UNAVAILABLE,
        SUBSTITUTE
    }

    public enum FactStatus {
        PASS,
        FAIL,
        WARNING
    }

    public record TeamContextReference(UUID servicePlanId, UUID servicePlanBlockId) {
    }

    public record ExplicitTeamConstraints(
            UUID servicePlanId,
            List<TeamAssignment> assignments,
            Map<UUID, ArrangementTeamRequirement> arrangementRequirements,
            boolean incompleteTeam) {

        public ExplicitTeamConstraints {
            assignments = assignments == null ? List.of() : List.copyOf(assignments);
            arrangementRequirements = arrangementRequirements == null ? Map.of() : Map.copyOf(arrangementRequirements);
        }
    }

    public record TeamAssignment(
            UUID musicianId,
            AssignmentStatus status,
            boolean availableForService,
            Set<String> roleCodes,
            Set<String> instrumentCodes,
            Set<String> vocalPartCodes,
            Map<String, Integer> instrumentSkillRanks,
            Map<String, Integer> vocalSkillRanks,
            Integer comfortableLowMidiNote,
            Integer comfortableHighMidiNote) {

        public TeamAssignment {
            roleCodes = normalizeSet(roleCodes);
            instrumentCodes = normalizeSet(instrumentCodes);
            vocalPartCodes = normalizeSet(vocalPartCodes);
            instrumentSkillRanks = normalizeMap(instrumentSkillRanks);
            vocalSkillRanks = normalizeMap(vocalSkillRanks);
            status = status == null ? AssignmentStatus.REQUESTED : status;
        }

        private static Set<String> normalizeSet(Set<String> values) {
            if (values == null) {
                return Set.of();
            }
            return values.stream()
                    .map(value -> value == null ? null : value.trim().toUpperCase(Locale.ROOT))
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }

        private static Map<String, Integer> normalizeMap(Map<String, Integer> values) {
            if (values == null) {
                return Map.of();
            }
            return values.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .collect(Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().trim().toUpperCase(Locale.ROOT),
                            Map.Entry::getValue,
                            Integer::max));
        }

        boolean activeForSuitability() {
            return availableForService
                    && (status == AssignmentStatus.REQUESTED
                            || status == AssignmentStatus.TENTATIVE
                            || status == AssignmentStatus.ACCEPTED
                            || status == AssignmentStatus.SUBSTITUTE);
        }
    }

    public record ArrangementTeamRequirement(
            List<TeamRequirementSlot> requiredSlots,
            List<TeamRequirementSlot> optionalSlots,
            String vocalConfiguration,
            boolean vocalConfigurationRequired,
            Integer leadVocalLowMidiNote,
            Integer leadVocalHighMidiNote,
            int requiredBackingVocalCount) {

        public ArrangementTeamRequirement {
            requiredSlots = requiredSlots == null ? List.of() : List.copyOf(requiredSlots);
            optionalSlots = optionalSlots == null ? List.of() : List.copyOf(optionalSlots);
        }
    }

    public record TeamRequirementSlot(
            String roleCode,
            String instrumentCode,
            String vocalPartCode,
            int minimumSkillRank,
            int minimumCount) {

        public TeamRequirementSlot {
            minimumCount = minimumCount <= 0 ? 1 : minimumCount;
        }
    }

    public record TeamSuitabilityFact(
            TeamConstraintCode code,
            FactStatus status,
            String diagnosticCode,
            int requiredCount,
            int actualCount) {
    }

    public record TeamSuitabilityEvaluation(
            UUID arrangementId,
            List<TeamSuitabilityFact> facts) {

        public TeamSuitabilityEvaluation {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }

        public boolean hasFailureFor(Map<TeamConstraintCode, TeamConstraintMode> modes, TeamConstraintMode mode) {
            return facts.stream()
                    .anyMatch(fact -> fact.status() == FactStatus.FAIL
                            && modes.getOrDefault(fact.code(), TeamConstraintMode.DISABLED) == mode);
        }
    }
}
