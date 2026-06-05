package com.cadentia.team;

import com.cadentia.team.ArrangementSuitabilityModels.ArrangementSuitabilityEvaluation;
import com.cadentia.team.ArrangementSuitabilityModels.CoverageRule;
import com.cadentia.team.ArrangementSuitabilityModels.CreateSuitabilityProfileCommand;
import com.cadentia.team.ArrangementSuitabilityModels.CreateSuitabilitySlotCommand;
import com.cadentia.team.ArrangementSuitabilityModels.RequirementType;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityEvaluationFact;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityFactCategory;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityFactStatus;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilityProfileRecord;
import com.cadentia.team.ArrangementSuitabilityModels.SuitabilitySlotRecord;
import com.cadentia.team.ArrangementSuitabilityModels.VocalConfiguration;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcArrangementSuitabilityRepository {

    private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of(
            AssignmentStatusCode.REQUESTED.name(),
            AssignmentStatusCode.TENTATIVE.name(),
            AssignmentStatusCode.ACCEPTED.name(),
            AssignmentStatusCode.SUBSTITUTE.name());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcArrangementSuitabilityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SuitabilityProfileRecord createProfile(CreateSuitabilityProfileCommand command) {
        if (command.current()) {
            jdbcTemplate.update(
                    "UPDATE arrangement_suitability_profiles SET is_current = false WHERE arrangement_id = :arrangementId",
                    Map.of("arrangementId", command.arrangementId()));
        }
        UUID profileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO arrangement_suitability_profiles (
                    arrangement_id, version_number, is_current, vocal_configuration,
                    lead_vocal_low_midi_note, lead_vocal_high_midi_note, required_backing_vocal_count,
                    review_notes, governance_action_ref, created_by
                ) VALUES (
                    :arrangementId, :versionNumber, :current, :vocalConfiguration,
                    :leadVocalLowMidiNote, :leadVocalHighMidiNote, :requiredBackingVocalCount,
                    :reviewNotes, :governanceActionRef, :createdBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("arrangementId", command.arrangementId())
                        .addValue("versionNumber", command.versionNumber())
                        .addValue("current", command.current())
                        .addValue("vocalConfiguration", enumName(command.vocalConfiguration()))
                        .addValue("leadVocalLowMidiNote", command.leadVocalLowMidiNote())
                        .addValue("leadVocalHighMidiNote", command.leadVocalHighMidiNote())
                        .addValue("requiredBackingVocalCount", command.requiredBackingVocalCount())
                        .addValue("reviewNotes", command.reviewNotes())
                        .addValue("governanceActionRef", command.governanceActionRef())
                        .addValue("createdBy", command.createdBy()),
                UUID.class);
        return findProfile(profileId).orElseThrow();
    }

    public SuitabilitySlotRecord addSlot(CreateSuitabilitySlotCommand command) {
        UUID slotId = jdbcTemplate.queryForObject(
                """
                INSERT INTO arrangement_suitability_slots (
                    suitability_profile_id, requirement_type, role_code, instrument_code, vocal_part_code,
                    minimum_skill_level_code, minimum_count, coverage_rule, review_notes, sort_order
                ) VALUES (
                    :suitabilityProfileId, :requirementType, :roleCode, :instrumentCode, :vocalPartCode,
                    :minimumSkillLevelCode, :minimumCount, :coverageRule, :reviewNotes, :sortOrder
                )
                RETURNING id
                """,
                slotParameters(command),
                UUID.class);
        return findSlot(slotId).orElseThrow();
    }

    public Optional<SuitabilityProfileRecord> findProfile(UUID suitabilityProfileId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, arrangement_id, version_number, is_current, vocal_configuration,
                               lead_vocal_low_midi_note, lead_vocal_high_midi_note, required_backing_vocal_count,
                               review_notes, governance_action_ref, created_by, created_at
                        FROM arrangement_suitability_profiles
                        WHERE id = :suitabilityProfileId
                        """,
                        Map.of("suitabilityProfileId", suitabilityProfileId),
                        (rs, rowNum) -> mapProfile(rs))
                .stream()
                .findFirst();
    }

    public List<SuitabilitySlotRecord> listCurrentApprovedSlots(UUID arrangementId) {
        return jdbcTemplate.query(
                """
                SELECT suitability_slot_id AS id, suitability_profile_id, requirement_type, role_code, instrument_code,
                       vocal_part_code, minimum_skill_level_code, minimum_count, coverage_rule, review_notes, sort_order
                FROM v_approved_arrangement_suitability_slots
                WHERE arrangement_id = :arrangementId
                ORDER BY sort_order ASC, suitability_slot_id ASC
                """,
                Map.of("arrangementId", arrangementId),
                (rs, rowNum) -> mapSlot(rs));
    }

    public ArrangementSuitabilityEvaluation evaluateArrangementForService(UUID arrangementId, UUID servicePlanId) {
        Optional<ApprovedProfile> approvedProfile = findApprovedProfile(arrangementId);
        if (approvedProfile.isEmpty()) {
            return new ArrangementSuitabilityEvaluation(arrangementId, servicePlanId, false, false, List.of(
                    new SuitabilityEvaluationFact(
                            SuitabilityFactCategory.APPROVAL_GATE,
                            SuitabilityFactStatus.FAIL,
                            "ARRANGEMENT_NOT_APPROVED_FOR_RECOMMENDATION",
                            "Arrangement suitability is not evaluated until catalog recommendation approval gates pass.",
                            null,
                            null)));
        }

        ApprovedProfile profile = approvedProfile.get();
        List<SuitabilityEvaluationFact> facts = new ArrayList<>();
        facts.add(new SuitabilityEvaluationFact(
                SuitabilityFactCategory.APPROVAL_GATE,
                SuitabilityFactStatus.PASS,
                "ARRANGEMENT_APPROVED_FOR_RECOMMENDATION",
                "Catalog approval gates passed before team suitability was considered.",
                null,
                null));
        for (SuitabilitySlotRecord slot : listCurrentApprovedSlots(arrangementId)) {
            int actualCount = countMatchingAssignments(slot, servicePlanId);
            SuitabilityFactCategory category = categoryFor(slot);
            SuitabilityFactStatus status = statusFor(slot.requirementType(), actualCount, slot.minimumCount());
            facts.add(new SuitabilityEvaluationFact(
                    category,
                    status,
                    codeFor(slot, status),
                    messageFor(slot, actualCount),
                    slot.minimumCount(),
                    actualCount));
        }
        addBackingVocalFact(servicePlanId, profile, facts);
        addLeadRangeFact(servicePlanId, profile, facts);
        boolean suitable = facts.stream().noneMatch(fact -> fact.status() == SuitabilityFactStatus.FAIL);
        return new ArrangementSuitabilityEvaluation(arrangementId, servicePlanId, true, suitable, facts);
    }

    private Optional<ApprovedProfile> findApprovedProfile(UUID arrangementId) {
        return jdbcTemplate.query(
                        """
                        SELECT arrangement_id, suitability_profile_id, vocal_configuration, lead_vocal_low_midi_note,
                               lead_vocal_high_midi_note, required_backing_vocal_count
                        FROM v_approved_arrangement_suitability_profiles
                        WHERE arrangement_id = :arrangementId
                        """,
                        Map.of("arrangementId", arrangementId),
                        (rs, rowNum) -> new ApprovedProfile(
                                rs.getObject("arrangement_id", UUID.class),
                                rs.getObject("suitability_profile_id", UUID.class),
                                VocalConfiguration.valueOf(rs.getString("vocal_configuration")),
                                integerOrNull(rs, "lead_vocal_low_midi_note"),
                                integerOrNull(rs, "lead_vocal_high_midi_note"),
                                rs.getInt("required_backing_vocal_count")))
                .stream()
                .findFirst();
    }

    private Optional<SuitabilitySlotRecord> findSlot(UUID slotId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, suitability_profile_id, requirement_type, role_code, instrument_code, vocal_part_code,
                               minimum_skill_level_code, minimum_count, coverage_rule, review_notes, sort_order
                        FROM arrangement_suitability_slots
                        WHERE id = :slotId
                        """,
                        Map.of("slotId", slotId),
                        (rs, rowNum) -> mapSlot(rs))
                .stream()
                .findFirst();
    }

    private int countMatchingAssignments(SuitabilitySlotRecord slot, UUID servicePlanId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT service_team_assignments.id)
                FROM service_team_assignments
                JOIN musicians ON musicians.id = service_team_assignments.musician_id AND musicians.active
                LEFT JOIN musician_instrument_assignments instrument_skill
                  ON instrument_skill.musician_id = service_team_assignments.musician_id
                 AND instrument_skill.instrument_code = service_team_assignments.instrument_code
                 AND instrument_skill.active
                LEFT JOIN skill_levels assigned_instrument_skill
                  ON assigned_instrument_skill.code = instrument_skill.skill_level_code
                LEFT JOIN musician_vocal_part_assignments vocal_skill
                  ON vocal_skill.musician_id = service_team_assignments.musician_id
                 AND vocal_skill.vocal_part_code = service_team_assignments.vocal_part_code
                 AND vocal_skill.active
                LEFT JOIN skill_levels assigned_vocal_skill ON assigned_vocal_skill.code = vocal_skill.skill_level_code
                LEFT JOIN skill_levels required_skill ON required_skill.code = :minimumSkillLevelCode
                WHERE service_team_assignments.service_plan_id = :servicePlanId
                  AND service_team_assignments.status_code IN (:activeStatuses)
                  AND (:roleCode IS NULL OR service_team_assignments.role_code = :roleCode)
                  AND (:instrumentCode IS NULL OR service_team_assignments.instrument_code = :instrumentCode)
                  AND (:vocalPartCode IS NULL OR service_team_assignments.vocal_part_code = :vocalPartCode)
                  AND (
                      :minimumSkillLevelCode IS NULL
                      OR (
                          service_team_assignments.instrument_code IS NOT NULL
                          AND assigned_instrument_skill.level_rank >= required_skill.level_rank
                      )
                      OR (
                          service_team_assignments.vocal_part_code IS NOT NULL
                          AND assigned_vocal_skill.level_rank >= required_skill.level_rank
                      )
                  )
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("activeStatuses", ACTIVE_ASSIGNMENT_STATUSES)
                        .addValue("roleCode", enumName(slot.roleCode()))
                        .addValue("instrumentCode", enumName(slot.instrumentCode()))
                        .addValue("vocalPartCode", enumName(slot.vocalPartCode()))
                        .addValue("minimumSkillLevelCode", enumName(slot.minimumSkillLevelCode())),
                Integer.class);
        return count == null ? 0 : count;
    }

    private void addBackingVocalFact(UUID servicePlanId, ApprovedProfile profile, List<SuitabilityEvaluationFact> facts) {
        if (profile.requiredBackingVocalCount() == 0) {
            return;
        }
        Integer actualCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT id)
                FROM service_team_assignments
                WHERE service_plan_id = :servicePlanId
                  AND status_code IN (:activeStatuses)
                  AND vocal_part_code IN ('ALTO', 'TENOR', 'BARITONE', 'SOPRANO', 'BACKGROUND')
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("activeStatuses", ACTIVE_ASSIGNMENT_STATUSES),
                Integer.class);
        int actual = actualCount == null ? 0 : actualCount;
        facts.add(new SuitabilityEvaluationFact(
                SuitabilityFactCategory.VOCAL_COVERAGE,
                actual >= profile.requiredBackingVocalCount() ? SuitabilityFactStatus.PASS : SuitabilityFactStatus.FAIL,
                "BACKING_VOCAL_COVERAGE",
                "Backing vocal coverage requires " + profile.requiredBackingVocalCount() + " and found " + actual + ".",
                profile.requiredBackingVocalCount(),
                actual));
    }

    private void addLeadRangeFact(UUID servicePlanId, ApprovedProfile profile, List<SuitabilityEvaluationFact> facts) {
        if (profile.leadVocalLowMidiNote() == null || profile.leadVocalHighMidiNote() == null) {
            return;
        }
        Integer conflicts = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT service_team_assignments.id)
                FROM service_team_assignments
                JOIN musicians ON musicians.id = service_team_assignments.musician_id
                WHERE service_team_assignments.service_plan_id = :servicePlanId
                  AND service_team_assignments.status_code IN (:activeStatuses)
                  AND service_team_assignments.vocal_part_code = 'LEAD'
                  AND (
                      musicians.comfortable_low_midi_note IS NULL
                      OR musicians.comfortable_high_midi_note IS NULL
                      OR musicians.comfortable_low_midi_note > :requiredLow
                      OR musicians.comfortable_high_midi_note < :requiredHigh
                  )
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("activeStatuses", ACTIVE_ASSIGNMENT_STATUSES)
                        .addValue("requiredLow", profile.leadVocalLowMidiNote())
                        .addValue("requiredHigh", profile.leadVocalHighMidiNote()),
                Integer.class);
        int actualConflicts = conflicts == null ? 0 : conflicts;
        facts.add(new SuitabilityEvaluationFact(
                SuitabilityFactCategory.RANGE,
                actualConflicts == 0 ? SuitabilityFactStatus.PASS : SuitabilityFactStatus.FAIL,
                "LEAD_VOCAL_RANGE",
                "Lead vocal range requires MIDI " + profile.leadVocalLowMidiNote() + "-"
                        + profile.leadVocalHighMidiNote() + " and found " + actualConflicts + " conflict(s).",
                0,
                actualConflicts));
    }

    private static SuitabilityFactCategory categoryFor(SuitabilitySlotRecord slot) {
        if (slot.minimumSkillLevelCode() != null) {
            return SuitabilityFactCategory.SKILL_FLOOR;
        }
        if (slot.vocalPartCode() != null) {
            return SuitabilityFactCategory.VOCAL_COVERAGE;
        }
        return SuitabilityFactCategory.INSTRUMENTATION;
    }

    private static SuitabilityFactStatus statusFor(RequirementType requirementType, int actualCount, int requiredCount) {
        if (actualCount >= requiredCount) {
            return SuitabilityFactStatus.PASS;
        }
        return requirementType == RequirementType.REQUIRED ? SuitabilityFactStatus.FAIL : SuitabilityFactStatus.WARNING;
    }

    private static String codeFor(SuitabilitySlotRecord slot, SuitabilityFactStatus status) {
        if (slot.instrumentCode() != null) {
            return status.name() + "_INSTRUMENT_" + slot.instrumentCode().name();
        }
        if (slot.vocalPartCode() != null) {
            return status.name() + "_VOCAL_PART_" + slot.vocalPartCode().name();
        }
        return status.name() + "_ROLE_" + slot.roleCode().name();
    }

    private static String messageFor(SuitabilitySlotRecord slot, int actualCount) {
        return slot.requirementType().name().toLowerCase() + " slot requires " + slot.minimumCount()
                + " matching assignment(s) and found " + actualCount + ".";
    }

    private static SuitabilityProfileRecord mapProfile(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new SuitabilityProfileRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("arrangement_id", UUID.class),
                rs.getInt("version_number"),
                rs.getBoolean("is_current"),
                VocalConfiguration.valueOf(rs.getString("vocal_configuration")),
                integerOrNull(rs, "lead_vocal_low_midi_note"),
                integerOrNull(rs, "lead_vocal_high_midi_note"),
                rs.getInt("required_backing_vocal_count"),
                rs.getString("review_notes"),
                rs.getString("governance_action_ref"),
                rs.getString("created_by"),
                createdAt == null ? null : createdAt.toInstant());
    }

    private static SuitabilitySlotRecord mapSlot(ResultSet rs) throws SQLException {
        return new SuitabilitySlotRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("suitability_profile_id", UUID.class),
                RequirementType.valueOf(rs.getString("requirement_type")),
                enumValue(MusicianRoleCode.class, rs.getString("role_code")),
                enumValue(InstrumentCode.class, rs.getString("instrument_code")),
                enumValue(VocalPartCode.class, rs.getString("vocal_part_code")),
                enumValue(SkillLevelCode.class, rs.getString("minimum_skill_level_code")),
                rs.getInt("minimum_count"),
                CoverageRule.valueOf(rs.getString("coverage_rule")),
                rs.getString("review_notes"),
                rs.getInt("sort_order"));
    }

    private static MapSqlParameterSource slotParameters(CreateSuitabilitySlotCommand command) {
        return new MapSqlParameterSource()
                .addValue("suitabilityProfileId", command.suitabilityProfileId())
                .addValue("requirementType", enumName(command.requirementType()))
                .addValue("roleCode", enumName(command.roleCode()))
                .addValue("instrumentCode", enumName(command.instrumentCode()))
                .addValue("vocalPartCode", enumName(command.vocalPartCode()))
                .addValue("minimumSkillLevelCode", enumName(command.minimumSkillLevelCode()))
                .addValue("minimumCount", command.minimumCount())
                .addValue("coverageRule", enumName(command.coverageRule()))
                .addValue("reviewNotes", command.reviewNotes())
                .addValue("sortOrder", command.sortOrder());
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    private record ApprovedProfile(
            UUID arrangementId,
            UUID suitabilityProfileId,
            VocalConfiguration vocalConfiguration,
            Integer leadVocalLowMidiNote,
            Integer leadVocalHighMidiNote,
            int requiredBackingVocalCount) {
    }
}
