package com.cadentia.team;

import com.cadentia.serviceplan.ServicePlanModels.ReadinessStatus;
import com.cadentia.team.ReadinessModels.ReadinessNoteRecord;
import com.cadentia.team.ReadinessModels.ReadinessPrivacyClassification;
import com.cadentia.team.ReadinessModels.ReadinessScopeType;
import com.cadentia.team.ReadinessModels.RecordReadinessCommand;
import com.cadentia.team.ReadinessModels.RehearsalResponseState;
import com.cadentia.team.TeamPlanningModels.AssignmentChangeHistoryRecord;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AssignmentType;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.ControlledVocabularyEntry;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTeamPlanningRepository implements TeamPlanningRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTeamPlanningRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ControlledVocabularyEntry> listAssignmentStatuses() {
        return listVocabulary("assignment_statuses");
    }

    @Override
    public List<ControlledVocabularyEntry> listInstruments() {
        return listVocabulary("instruments");
    }

    @Override
    public List<ControlledVocabularyEntry> listMusicianRoles() {
        return listVocabulary("musician_roles");
    }

    @Override
    @Transactional
    public MusicianRecord createMusician(CreateMusicianCommand command) {
        UUID musicianId = jdbcTemplate.queryForObject(
                """
                INSERT INTO musicians (
                    display_name, account_principal, email, phone, primary_vocal_range_code,
                    comfortable_low_midi_note, comfortable_high_midi_note, serving_preference_code,
                    created_by, updated_by
                ) VALUES (
                    :displayName, :accountPrincipal, :email, :phone, :primaryVocalRangeCode,
                    :comfortableLowMidiNote, :comfortableHighMidiNote, :servingPreferenceCode,
                    :createdBy, :createdBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("displayName", command.displayName())
                        .addValue("accountPrincipal", command.accountPrincipal())
                        .addValue("email", command.email())
                        .addValue("phone", command.phone())
                        .addValue("primaryVocalRangeCode", enumName(command.primaryVocalRangeCode()))
                        .addValue("comfortableLowMidiNote", command.comfortableLowMidiNote())
                        .addValue("comfortableHighMidiNote", command.comfortableHighMidiNote())
                        .addValue("servingPreferenceCode", enumName(command.servingPreferenceCode()))
                        .addValue("createdBy", command.createdBy()),
                UUID.class);
        return findMusician(musicianId).orElseThrow();
    }

    @Override
    public Optional<MusicianRecord> findMusician(UUID musicianId) {
        List<MusicianRecord> rows = jdbcTemplate.query(
                "SELECT * FROM musicians WHERE id = :id",
                Map.of("id", musicianId),
                (rs, rowNum) -> mapMusician(rs));
        return rows.stream().findFirst();
    }

    @Override
    public UUID assignRole(UUID musicianId, MusicianRoleCode roleCode, SkillLevelCode skillLevelCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_role_assignments (musician_id, role_code, skill_level_code)
                VALUES (:musicianId, :roleCode, :skillLevelCode)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("roleCode", enumName(roleCode))
                        .addValue("skillLevelCode", enumName(skillLevelCode)),
                UUID.class);
    }

    @Override
    public UUID assignInstrument(UUID musicianId, InstrumentCode instrumentCode, SkillLevelCode skillLevelCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_instrument_assignments (musician_id, instrument_code, skill_level_code)
                VALUES (:musicianId, :instrumentCode, :skillLevelCode)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("instrumentCode", enumName(instrumentCode))
                        .addValue("skillLevelCode", enumName(skillLevelCode)),
                UUID.class);
    }

    @Override
    public UUID assignVocalPart(UUID musicianId, VocalPartCode vocalPartCode, SkillLevelCode skillLevelCode) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_vocal_part_assignments (musician_id, vocal_part_code, skill_level_code)
                VALUES (:musicianId, :vocalPartCode, :skillLevelCode)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("vocalPartCode", enumName(vocalPartCode))
                        .addValue("skillLevelCode", enumName(skillLevelCode)),
                UUID.class);
    }

    @Override
    public AvailabilityWindowRecord createAvailabilityWindow(
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO musician_availability_windows (musician_id, starts_at, ends_at, status_code, service_plan_id)
                VALUES (:musicianId, :startsAt, :endsAt, :statusCode, :servicePlanId)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("startsAt", Timestamp.from(startsAt))
                        .addValue("endsAt", Timestamp.from(endsAt))
                        .addValue("statusCode", enumName(statusCode))
                        .addValue("servicePlanId", servicePlanId),
                UUID.class);
        return new AvailabilityWindowRecord(id, musicianId, startsAt, endsAt, statusCode, servicePlanId);
    }

    @Override
    public boolean isActiveMusician(UUID musicianId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM musicians WHERE id = :musicianId AND active",
                Map.of("musicianId", musicianId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean isActiveVocabularyValue(String tableName, String code) {
        if (code == null) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE code = :code AND active",
                Map.of("code", code),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean hasUnavailableWindow(UUID musicianId, UUID servicePlanId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM musician_availability_windows windows
                JOIN service_plans plans ON plans.id = :servicePlanId
                WHERE windows.musician_id = :musicianId
                  AND windows.status_code IN ('DECLINED', 'UNAVAILABLE')
                  AND (windows.service_plan_id = :servicePlanId
                       OR (windows.starts_at <= plans.service_date_time AND windows.ends_at > plans.service_date_time))
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("servicePlanId", servicePlanId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean hasDuplicateServicePosition(
            UUID servicePlanId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            UUID excludingAssignmentId) {
        if (roleCode == null || (instrumentCode == null && vocalPartCode == null)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM service_team_assignments
                WHERE service_plan_id = :servicePlanId
                  AND role_code = :roleCode
                  AND COALESCE(instrument_code, '') = COALESCE(:instrumentCode, '')
                  AND COALESCE(vocal_part_code, '') = COALESCE(:vocalPartCode, '')
                  AND status_code IN ('REQUESTED', 'TENTATIVE', 'ACCEPTED', 'SUBSTITUTE')
                  AND (:excludingAssignmentId IS NULL OR id <> :excludingAssignmentId)
                """,
                assignmentParameters(servicePlanId, null, roleCode, instrumentCode, vocalPartCode, null)
                        .addValue("excludingAssignmentId", excludingAssignmentId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    public ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            int assignmentOrder,
            UUID substituteForAssignmentId) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_team_assignments (
                    service_plan_id, musician_id, role_code, instrument_code, vocal_part_code, status_code,
                    assignment_order, substitute_for_assignment_id
                ) VALUES (
                    :servicePlanId, :musicianId, :roleCode, :instrumentCode, :vocalPartCode, :statusCode,
                    :assignmentOrder, :substituteForAssignmentId
                )
                RETURNING id
                """,
                assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                        .addValue("assignmentOrder", assignmentOrder)
                        .addValue("substituteForAssignmentId", substituteForAssignmentId),
                UUID.class);
        return new ServiceAssignmentRecord(id, servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode,
                statusCode, assignmentOrder, substituteForAssignmentId);
    }

    @Override
    public Optional<ServiceAssignmentRecord> findServiceAssignment(UUID assignmentId) {
        List<ServiceAssignmentRecord> rows = jdbcTemplate.query(
                serviceAssignmentSelect() + " WHERE id = :assignmentId",
                Map.of("assignmentId", assignmentId),
                (rs, rowNum) -> mapServiceAssignment(rs));
        return rows.stream().findFirst();
    }

    @Override
    public List<ServiceAssignmentRecord> listServiceRoster(UUID servicePlanId) {
        return jdbcTemplate.query(
                serviceAssignmentSelect()
                        + " WHERE service_plan_id = :servicePlanId ORDER BY assignment_order ASC, created_at ASC",
                Map.of("servicePlanId", servicePlanId),
                (rs, rowNum) -> mapServiceAssignment(rs));
    }

    @Override
    public List<ServiceAssignmentRecord> listUpcomingServiceAssignmentsForMusician(UUID musicianId, Instant fromInclusive) {
        return jdbcTemplate.query(
                """
                SELECT service_team_assignments.id, service_team_assignments.service_plan_id,
                       service_team_assignments.musician_id, service_team_assignments.role_code,
                       service_team_assignments.instrument_code, service_team_assignments.vocal_part_code,
                       service_team_assignments.status_code, service_team_assignments.assignment_order,
                       service_team_assignments.substitute_for_assignment_id, service_team_assignments.created_at
                FROM service_team_assignments
                JOIN service_plans ON service_plans.id = service_team_assignments.service_plan_id
                WHERE service_team_assignments.musician_id = :musicianId
                  AND service_plans.service_date_time >= :fromInclusive
                ORDER BY service_plans.service_date_time ASC, service_team_assignments.assignment_order ASC
                """,
                new MapSqlParameterSource()
                        .addValue("musicianId", musicianId)
                        .addValue("fromInclusive", Timestamp.from(fromInclusive)),
                (rs, rowNum) -> mapServiceAssignment(rs));
    }

    @Override
    public Optional<ServiceAssignmentRecord> updateServiceAssignmentStatus(UUID assignmentId, AssignmentStatusCode statusCode) {
        List<ServiceAssignmentRecord> rows = jdbcTemplate.query(
                """
                UPDATE service_team_assignments
                SET status_code = :statusCode, updated_at = now()
                WHERE id = :assignmentId
                RETURNING id, service_plan_id, musician_id, role_code, instrument_code, vocal_part_code, status_code,
                          assignment_order, substitute_for_assignment_id, created_at
                """,
                new MapSqlParameterSource()
                        .addValue("assignmentId", assignmentId)
                        .addValue("statusCode", enumName(statusCode)),
                (rs, rowNum) -> mapServiceAssignment(rs));
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ServiceAssignmentRecord> updateServiceAssignment(
            UUID assignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            int assignmentOrder) {
        List<ServiceAssignmentRecord> rows = jdbcTemplate.query(
                """
                UPDATE service_team_assignments
                SET musician_id = :musicianId,
                    role_code = :roleCode,
                    instrument_code = :instrumentCode,
                    vocal_part_code = :vocalPartCode,
                    status_code = :statusCode,
                    assignment_order = :assignmentOrder,
                    updated_at = now()
                WHERE id = :assignmentId
                RETURNING id, service_plan_id, musician_id, role_code, instrument_code, vocal_part_code, status_code,
                          assignment_order, substitute_for_assignment_id, created_at
                """,
                assignmentParameters(null, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                        .addValue("assignmentId", assignmentId)
                        .addValue("assignmentOrder", assignmentOrder),
                (rs, rowNum) -> mapServiceAssignment(rs));
        return rows.stream().findFirst();
    }

    @Override
    public boolean removeServiceAssignment(UUID assignmentId) {
        return jdbcTemplate.update(
                """
                UPDATE service_team_assignments
                SET status_code = 'DECLINED', updated_at = now()
                WHERE id = :assignmentId
                """,
                Map.of("assignmentId", assignmentId)) > 0;
    }

    @Override
    public void reorderServiceAssignments(UUID servicePlanId, List<UUID> orderedAssignmentIds) {
        int order = 0;
        for (UUID assignmentId : orderedAssignmentIds) {
            jdbcTemplate.update(
                    """
                    UPDATE service_team_assignments
                    SET assignment_order = :assignmentOrder, updated_at = now()
                    WHERE service_plan_id = :servicePlanId AND id = :assignmentId
                    """,
                    new MapSqlParameterSource()
                            .addValue("assignmentOrder", order++)
                            .addValue("servicePlanId", servicePlanId)
                            .addValue("assignmentId", assignmentId));
        }
    }

    @Override
    public RehearsalEventRecord createRehearsalEvent(
            UUID servicePlanId,
            Instant startsAt,
            Instant endsAt,
            String location) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_events (service_plan_id, starts_at, ends_at, location)
                VALUES (:servicePlanId, :startsAt, :endsAt, :location)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("startsAt", Timestamp.from(startsAt))
                        .addValue("endsAt", Timestamp.from(endsAt))
                        .addValue("location", location),
                UUID.class);
        return new RehearsalEventRecord(id, servicePlanId, startsAt, endsAt, location);
    }

    @Override
    public RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID serviceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            UUID substituteForAssignmentId) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_team_assignments (
                    rehearsal_event_id, service_plan_id, service_assignment_id, musician_id, role_code,
                    instrument_code, vocal_part_code, status_code, substitute_for_assignment_id
                ) VALUES (
                    :rehearsalEventId, :servicePlanId, :serviceAssignmentId, :musicianId, :roleCode,
                    :instrumentCode, :vocalPartCode, :statusCode, :substituteForAssignmentId
                )
                RETURNING id
                """,
                assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                        .addValue("rehearsalEventId", rehearsalEventId)
                        .addValue("serviceAssignmentId", serviceAssignmentId)
                        .addValue("substituteForAssignmentId", substituteForAssignmentId),
                UUID.class);
        return new RehearsalAssignmentRecord(id, rehearsalEventId, servicePlanId, musicianId, roleCode, instrumentCode,
                vocalPartCode, statusCode, serviceAssignmentId, substituteForAssignmentId);
    }

    @Override
    public SongAssignmentOverrideRecord createSongAssignmentOverride(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_song_assignment_overrides (
                    service_plan_id, service_plan_block_id, base_service_assignment_id,
                    musician_id, role_code, instrument_code, vocal_part_code, status_code
                ) VALUES (
                    :servicePlanId, :servicePlanBlockId, :baseServiceAssignmentId,
                    :musicianId, :roleCode, :instrumentCode, :vocalPartCode, :statusCode
                )
                RETURNING id
                """,
                assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                        .addValue("servicePlanBlockId", servicePlanBlockId)
                        .addValue("baseServiceAssignmentId", baseServiceAssignmentId),
                UUID.class);
        return new SongAssignmentOverrideRecord(id, servicePlanId, servicePlanBlockId, baseServiceAssignmentId,
                musicianId, roleCode, instrumentCode, vocalPartCode, statusCode);
    }

    @Override
    public void recordAssignmentHistory(
            AssignmentType assignmentType,
            UUID assignmentId,
            UUID servicePlanId,
            UUID rehearsalEventId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            Integer assignmentOrder,
            UUID substituteForAssignmentId,
            UUID serviceAssignmentId,
            String changeAction,
            String changedBy,
            String reasonCode,
            String reference) {
        jdbcTemplate.update(
                """
                INSERT INTO team_assignment_history (
                    assignment_type, assignment_id, service_plan_id, rehearsal_event_id, musician_id,
                    role_code, instrument_code, vocal_part_code, status_code, assignment_order,
                    substitute_for_assignment_id, service_assignment_id, change_action, changed_by, reason_code, reference
                ) VALUES (
                    :assignmentType, :assignmentId, :servicePlanId, :rehearsalEventId, :musicianId,
                    :roleCode, :instrumentCode, :vocalPartCode, :statusCode, :assignmentOrder,
                    :substituteForAssignmentId, :serviceAssignmentId, :changeAction, :changedBy, :reasonCode, :reference
                )
                """,
                assignmentHistoryParameters(assignmentType, assignmentId, servicePlanId, rehearsalEventId, musicianId,
                        roleCode, instrumentCode, vocalPartCode, statusCode, assignmentOrder, substituteForAssignmentId,
                        serviceAssignmentId, changeAction, changedBy, reasonCode, reference));
    }

    @Override
    public List<AssignmentChangeHistoryRecord> listAssignmentHistory(UUID servicePlanId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM team_assignment_history
                WHERE service_plan_id = :servicePlanId
                ORDER BY changed_at DESC, id DESC
                """,
                Map.of("servicePlanId", servicePlanId),
                (rs, rowNum) -> mapAssignmentHistory(rs));
    }

    private String serviceAssignmentSelect() {
        return """
                SELECT id, service_plan_id, musician_id, role_code, instrument_code, vocal_part_code, status_code,
                       assignment_order, substitute_for_assignment_id, created_at
                FROM service_team_assignments
                """;
    }

    private ServiceAssignmentRecord mapServiceAssignment(ResultSet rs) throws SQLException {
        return new ServiceAssignmentRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                rs.getObject("musician_id", UUID.class),
                enumValue(MusicianRoleCode.class, rs.getString("role_code")),
                enumValue(InstrumentCode.class, rs.getString("instrument_code")),
                enumValue(VocalPartCode.class, rs.getString("vocal_part_code")),
                enumValue(AssignmentStatusCode.class, rs.getString("status_code")),
                rs.getInt("assignment_order"),
                rs.getObject("substitute_for_assignment_id", UUID.class));
    }

    private AssignmentChangeHistoryRecord mapAssignmentHistory(ResultSet rs) throws SQLException {
        return new AssignmentChangeHistoryRecord(
                rs.getObject("id", UUID.class),
                enumValue(AssignmentType.class, rs.getString("assignment_type")),
                rs.getObject("assignment_id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                rs.getObject("rehearsal_event_id", UUID.class),
                rs.getObject("musician_id", UUID.class),
                enumValue(MusicianRoleCode.class, rs.getString("role_code")),
                enumValue(InstrumentCode.class, rs.getString("instrument_code")),
                enumValue(VocalPartCode.class, rs.getString("vocal_part_code")),
                enumValue(AssignmentStatusCode.class, rs.getString("status_code")),
                nullableInteger(rs, "assignment_order"),
                rs.getObject("substitute_for_assignment_id", UUID.class),
                rs.getObject("service_assignment_id", UUID.class),
                rs.getString("change_action"),
                rs.getString("changed_by"),
                rs.getString("reason_code"),
                rs.getString("reference"),
                rs.getTimestamp("changed_at").toInstant());
    }


    @Override
    @Transactional
    public ReadinessNoteRecord recordReadiness(RecordReadinessCommand command) {
        UUID noteId = jdbcTemplate.queryForObject(
                """
                INSERT INTO readiness_notes (
                    scope_type, scope_id, service_plan_id, rehearsal_event_id, service_assignment_id,
                    song_assignment_override_id, service_plan_block_id, arrangement_id, readiness_status_code,
                    objective_blockers, missing_people, unresolved_arrangement_conflicts, rehearsal_response_state,
                    human_note, privacy_classification, override_action, updated_by
                )
                VALUES (
                    :scopeType, :scopeId, :servicePlanId, :rehearsalEventId, :serviceAssignmentId,
                    :songAssignmentOverrideId, :servicePlanBlockId, :arrangementId, :readinessStatusCode,
                    CAST(:objectiveBlockers AS jsonb), CAST(:missingPeople AS jsonb),
                    CAST(:unresolvedArrangementConflicts AS jsonb), :rehearsalResponseState, :humanNote,
                    :privacyClassification, :overrideAction, :updatedBy
                )
                ON CONFLICT (scope_type, scope_id) DO UPDATE SET
                    readiness_status_code = EXCLUDED.readiness_status_code,
                    objective_blockers = EXCLUDED.objective_blockers,
                    missing_people = EXCLUDED.missing_people,
                    unresolved_arrangement_conflicts = EXCLUDED.unresolved_arrangement_conflicts,
                    rehearsal_response_state = EXCLUDED.rehearsal_response_state,
                    human_note = EXCLUDED.human_note,
                    privacy_classification = EXCLUDED.privacy_classification,
                    override_action = EXCLUDED.override_action,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                RETURNING id
                """,
                readinessParameters(command),
                UUID.class);
        return findReadinessNote(noteId).orElseThrow();
    }

    @Override
    public List<ReadinessNoteRecord> listReadinessNotes(UUID servicePlanId) {
        return jdbcTemplate.query(
                "SELECT * FROM readiness_notes WHERE service_plan_id = :servicePlanId ORDER BY updated_at DESC",
                Map.of("servicePlanId", servicePlanId),
                (rs, rowNum) -> mapReadinessNote(rs));
    }

    private Optional<ReadinessNoteRecord> findReadinessNote(UUID readinessNoteId) {
        return jdbcTemplate.query(
                "SELECT * FROM readiness_notes WHERE id = :id",
                Map.of("id", readinessNoteId),
                (rs, rowNum) -> mapReadinessNote(rs)).stream().findFirst();
    }

    private List<ControlledVocabularyEntry> listVocabulary(String tableName) {
        return jdbcTemplate.query(
                "SELECT code, display_name, active, sort_order, system_default, local_extension FROM "
                        + tableName + " ORDER BY sort_order, code",
                (rs, rowNum) -> new ControlledVocabularyEntry(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        rs.getBoolean("active"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("system_default"),
                        rs.getBoolean("local_extension")));
    }

    private MapSqlParameterSource assignmentParameters(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        return new MapSqlParameterSource()
                .addValue("servicePlanId", servicePlanId)
                .addValue("musicianId", musicianId)
                .addValue("roleCode", enumName(roleCode))
                .addValue("instrumentCode", enumName(instrumentCode))
                .addValue("vocalPartCode", enumName(vocalPartCode))
                .addValue("statusCode", enumName(statusCode));
    }

    private MapSqlParameterSource assignmentHistoryParameters(
            AssignmentType assignmentType,
            UUID assignmentId,
            UUID servicePlanId,
            UUID rehearsalEventId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            Integer assignmentOrder,
            UUID substituteForAssignmentId,
            UUID serviceAssignmentId,
            String changeAction,
            String changedBy,
            String reasonCode,
            String reference) {
        return assignmentParameters(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode)
                .addValue("assignmentType", enumName(assignmentType))
                .addValue("assignmentId", assignmentId)
                .addValue("rehearsalEventId", rehearsalEventId)
                .addValue("assignmentOrder", assignmentOrder)
                .addValue("substituteForAssignmentId", substituteForAssignmentId)
                .addValue("serviceAssignmentId", serviceAssignmentId)
                .addValue("changeAction", changeAction)
                .addValue("changedBy", changedBy)
                .addValue("reasonCode", reasonCode)
                .addValue("reference", reference);
    }


    private MapSqlParameterSource readinessParameters(RecordReadinessCommand command) {
        return new MapSqlParameterSource()
                .addValue("scopeType", enumName(command.scopeType()))
                .addValue("scopeId", command.scopeId())
                .addValue("servicePlanId", command.servicePlanId())
                .addValue("rehearsalEventId", command.rehearsalEventId())
                .addValue("serviceAssignmentId", command.serviceAssignmentId())
                .addValue("songAssignmentOverrideId", command.songAssignmentOverrideId())
                .addValue("servicePlanBlockId", command.servicePlanBlockId())
                .addValue("arrangementId", command.arrangementId())
                .addValue("readinessStatusCode", enumName(command.readinessStatus()))
                .addValue("objectiveBlockers", toJson(command.objectiveBlockers()))
                .addValue("missingPeople", toJson(command.missingPeople()))
                .addValue("unresolvedArrangementConflicts", toJson(command.unresolvedArrangementConflicts()))
                .addValue("rehearsalResponseState", enumName(command.rehearsalResponseState()))
                .addValue("humanNote", command.humanNote())
                .addValue("privacyClassification", enumName(command.privacyClassification()))
                .addValue("overrideAction", command.overrideAction())
                .addValue("updatedBy", command.updatedBy());
    }

    private ReadinessNoteRecord mapReadinessNote(ResultSet rs) throws SQLException {
        return new ReadinessNoteRecord(
                rs.getObject("id", UUID.class),
                enumValue(ReadinessScopeType.class, rs.getString("scope_type")),
                rs.getObject("scope_id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                enumValue(ReadinessStatus.class, rs.getString("readiness_status_code")),
                readStringList(rs.getString("objective_blockers")),
                readStringList(rs.getString("missing_people")),
                readStringList(rs.getString("unresolved_arrangement_conflicts")),
                enumValue(RehearsalResponseState.class, rs.getString("rehearsal_response_state")),
                rs.getString("human_note"),
                enumValue(ReadinessPrivacyClassification.class, rs.getString("privacy_classification")),
                rs.getBoolean("override_action"),
                rs.getString("updated_by"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private String toJson(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize readiness values.", exception);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to read readiness values.", exception);
        }
    }

    private MusicianRecord mapMusician(ResultSet rs) throws SQLException {
        return new MusicianRecord(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                rs.getString("account_principal"),
                rs.getString("email"),
                rs.getString("phone"),
                enumValue(VocalRangeCode.class, rs.getString("primary_vocal_range_code")),
                nullableInteger(rs, "comfortable_low_midi_note"),
                nullableInteger(rs, "comfortable_high_midi_note"),
                enumValue(ServingPreferenceCode.class, rs.getString("serving_preference_code")),
                rs.getBoolean("active"));
    }

    private Integer nullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String enumName(Enum<?> enumValue) {
        return enumValue == null ? null : enumValue.name();
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumClass, String value) {
        return value == null ? null : Enum.valueOf(enumClass, value);
    }
}
