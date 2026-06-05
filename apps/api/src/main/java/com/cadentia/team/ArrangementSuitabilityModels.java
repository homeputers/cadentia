package com.cadentia.team;

import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ArrangementSuitabilityModels {

    private ArrangementSuitabilityModels() {
    }

    public enum VocalConfiguration {
        UNSPECIFIED,
        INSTRUMENTAL,
        SOLO_LEAD,
        LEAD_WITH_BACKING,
        CHOIR,
        CONGREGATIONAL
    }

    public enum RequirementType {
        REQUIRED,
        OPTIONAL
    }

    public enum CoverageRule {
        AT_LEAST,
        EXACTLY,
        ANY_OF
    }

    public enum SuitabilityFactStatus {
        PASS,
        FAIL,
        WARNING
    }

    public enum SuitabilityFactCategory {
        APPROVAL_GATE,
        INSTRUMENTATION,
        VOCAL_COVERAGE,
        SKILL_FLOOR,
        RANGE
    }

    public record CreateSuitabilityProfileCommand(
            UUID arrangementId,
            int versionNumber,
            boolean current,
            VocalConfiguration vocalConfiguration,
            Integer leadVocalLowMidiNote,
            Integer leadVocalHighMidiNote,
            int requiredBackingVocalCount,
            String reviewNotes,
            String governanceActionRef,
            String createdBy) {
    }

    public record CreateSuitabilitySlotCommand(
            UUID suitabilityProfileId,
            RequirementType requirementType,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            SkillLevelCode minimumSkillLevelCode,
            int minimumCount,
            CoverageRule coverageRule,
            String reviewNotes,
            int sortOrder) {
    }

    public record SuitabilityProfileRecord(
            UUID suitabilityProfileId,
            UUID arrangementId,
            int versionNumber,
            boolean current,
            VocalConfiguration vocalConfiguration,
            Integer leadVocalLowMidiNote,
            Integer leadVocalHighMidiNote,
            int requiredBackingVocalCount,
            String reviewNotes,
            String governanceActionRef,
            String createdBy,
            Instant createdAt) {
    }

    public record SuitabilitySlotRecord(
            UUID suitabilitySlotId,
            UUID suitabilityProfileId,
            RequirementType requirementType,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            SkillLevelCode minimumSkillLevelCode,
            int minimumCount,
            CoverageRule coverageRule,
            String reviewNotes,
            int sortOrder) {
    }

    public record SuitabilityEvaluationFact(
            SuitabilityFactCategory category,
            SuitabilityFactStatus status,
            String code,
            String message,
            Integer requiredCount,
            Integer actualCount) {
    }

    public record ArrangementSuitabilityEvaluation(
            UUID arrangementId,
            UUID servicePlanId,
            boolean approvalEligible,
            boolean suitable,
            List<SuitabilityEvaluationFact> facts) {

        public ArrangementSuitabilityEvaluation {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }
}
