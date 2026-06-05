package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ControlledVocabularyEntry;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalNoteRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcRehearsalWorkflowRepository implements RehearsalWorkflowRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRehearsalWorkflowRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ControlledVocabularyEntry> listReadinessStates() {
        return listVocabulary("rehearsal_workflow_readiness_states");
    }

    @Override
    public List<ControlledVocabularyEntry> listIssueCategories() {
        return listVocabulary("rehearsal_issue_categories");
    }

    @Override
    public List<ControlledVocabularyEntry> listIssueStatuses() {
        return listVocabulary("rehearsal_issue_statuses");
    }

    @Override
    @Transactional
    public RehearsalSessionRecord createSession(
            UUID servicePlanId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            String createdBy) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_sessions (
                    service_plan_id, session_code, starts_at, ends_at, location, created_by, updated_by
                ) VALUES (
                    :servicePlanId, :sessionCode, :startsAt, :endsAt, :location, :createdBy, :createdBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("sessionCode", sessionCode)
                        .addValue("startsAt", Timestamp.from(startsAt))
                        .addValue("endsAt", Timestamp.from(endsAt))
                        .addValue("location", location)
                        .addValue("createdBy", createdBy),
                UUID.class);
        return getSession(id);
    }

    @Override
    public RehearsalSessionRecord archiveSession(UUID servicePlanId, UUID rehearsalSessionId, String archivedBy) {
        UUID id = jdbcTemplate.queryForObject(
                """
                UPDATE rehearsal_sessions
                SET archived_at = NOW(), archived_by = :archivedBy, updated_by = :archivedBy, updated_at = NOW()
                WHERE service_plan_id = :servicePlanId
                  AND id = :rehearsalSessionId
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("rehearsalSessionId", rehearsalSessionId)
                        .addValue("archivedBy", archivedBy),
                UUID.class);
        return getSession(id);
    }

    @Override
    @Transactional
    public void recordServiceReadiness(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            ReadinessStateCode newStateCode,
            String rationale,
            String changedBy) {
        String previousStateCode = jdbcTemplate.query(
                        "SELECT readiness_state_code FROM service_rehearsal_workflow_states WHERE service_plan_id = :id",
                        Map.of("id", servicePlanId),
                        (rs, rowNum) -> rs.getString("readiness_state_code"))
                .stream()
                .findFirst()
                .orElse(null);
        jdbcTemplate.update(
                """
                INSERT INTO service_rehearsal_workflow_states (
                    service_plan_id, readiness_state_code, rationale, updated_by
                ) VALUES (
                    :servicePlanId, :readinessStateCode, :rationale, :changedBy
                )
                ON CONFLICT (service_plan_id) DO UPDATE SET
                    readiness_state_code = EXCLUDED.readiness_state_code,
                    rationale = EXCLUDED.rationale,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("readinessStateCode", newStateCode.code())
                        .addValue("rationale", rationale)
                        .addValue("changedBy", changedBy));
        jdbcTemplate.update(
                """
                INSERT INTO rehearsal_readiness_history (
                    service_plan_id, rehearsal_session_id, previous_state_code, new_state_code, rationale, changed_by
                ) VALUES (
                    :servicePlanId, :rehearsalSessionId, :previousStateCode, :newStateCode, :rationale, :changedBy
                )
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("rehearsalSessionId", rehearsalSessionId)
                        .addValue("previousStateCode", previousStateCode)
                        .addValue("newStateCode", newStateCode.code())
                        .addValue("rationale", rationale)
                        .addValue("changedBy", changedBy));
    }

    @Override
    public RehearsalNoteRecord addNote(
            UUID servicePlanId,
            RehearsalTarget target,
            String noteBody,
            String visibilityCode,
            String createdBy) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_notes (
                    service_plan_id, rehearsal_session_id, target_type_code, service_plan_block_id,
                    setlist_version_item_id, transition_from_block_id, transition_to_block_id, arrangement_id,
                    team_role_code, service_team_assignment_id, rehearsal_team_assignment_id,
                    song_assignment_override_id, note_body, visibility_code, created_by, updated_by
                ) VALUES (
                    :servicePlanId, :rehearsalSessionId, :targetTypeCode, :servicePlanBlockId,
                    :setlistVersionItemId, :transitionFromBlockId, :transitionToBlockId, :arrangementId,
                    :teamRoleCode, :serviceTeamAssignmentId, :rehearsalTeamAssignmentId,
                    :songAssignmentOverrideId, :noteBody, :visibilityCode, :createdBy, :createdBy
                )
                RETURNING id
                """,
                targetParameters(servicePlanId, target)
                        .addValue("noteBody", noteBody)
                        .addValue("visibilityCode", visibilityCode)
                        .addValue("createdBy", createdBy),
                UUID.class);
        return jdbcTemplate.queryForObject(
                "SELECT * FROM rehearsal_notes WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> mapNote(rs));
    }

    @Override
    public RehearsalIssueRecord createIssue(
            UUID servicePlanId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            IssueStatusCode statusCode,
            String title,
            String detail,
            String detectedBy) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_issues (
                    service_plan_id, rehearsal_session_id, target_type_code, service_plan_block_id,
                    setlist_version_item_id, transition_from_block_id, transition_to_block_id, arrangement_id,
                    team_role_code, service_team_assignment_id, rehearsal_team_assignment_id,
                    song_assignment_override_id, category_code, severity_code, status_code, title, detail, detected_by
                ) VALUES (
                    :servicePlanId, :rehearsalSessionId, :targetTypeCode, :servicePlanBlockId,
                    :setlistVersionItemId, :transitionFromBlockId, :transitionToBlockId, :arrangementId,
                    :teamRoleCode, :serviceTeamAssignmentId, :rehearsalTeamAssignmentId,
                    :songAssignmentOverrideId, :categoryCode, :severityCode, :statusCode, :title, :detail, :detectedBy
                )
                RETURNING id
                """,
                targetParameters(servicePlanId, target)
                        .addValue("categoryCode", categoryCode.code())
                        .addValue("severityCode", severityCode.code())
                        .addValue("statusCode", statusCode.code())
                        .addValue("title", title)
                        .addValue("detail", detail)
                        .addValue("detectedBy", detectedBy),
                UUID.class);
        return jdbcTemplate.queryForObject(
                "SELECT * FROM rehearsal_issues WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> mapIssue(rs));
    }

    @Override
    public RehearsalIssueActionRecord addIssueAction(
            UUID servicePlanId,
            UUID issueId,
            IssueActionStatusCode statusCode,
            String actionSummary,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            String createdBy) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO rehearsal_issue_actions (
                    service_plan_id, rehearsal_issue_id, action_status_code, action_summary, owner_type,
                    owner_actor, owner_team_role_code, owner_service_assignment_id, created_by, updated_by
                ) VALUES (
                    :servicePlanId, :issueId, :statusCode, :actionSummary, :ownerType,
                    :ownerActor, :ownerTeamRoleCode, :ownerServiceAssignmentId, :createdBy, :createdBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("issueId", issueId)
                        .addValue("statusCode", statusCode.code())
                        .addValue("actionSummary", actionSummary)
                        .addValue("ownerType", ownerType.code())
                        .addValue("ownerActor", ownerActor)
                        .addValue("ownerTeamRoleCode", ownerTeamRoleCode)
                        .addValue("ownerServiceAssignmentId", ownerServiceAssignmentId)
                        .addValue("createdBy", createdBy),
                UUID.class);
        return jdbcTemplate.queryForObject(
                "SELECT * FROM rehearsal_issue_actions WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> mapAction(rs));
    }

    @Override
    public ArrangementOverrideRecord createArrangementOverride(ArrangementOverrideRecord overrideRecord) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO service_arrangement_overrides (
                    service_plan_id, service_plan_block_id, setlist_version_item_id, source_arrangement_id,
                    source_arrangement_version_ref, effective_key, effective_mode, effective_tempo_bpm,
                    effective_time_signature, effective_duration_seconds, effective_energy_level,
                    effective_difficulty_level, effective_notes, rationale, provenance_note, created_by, updated_by
                ) VALUES (
                    :servicePlanId, :servicePlanBlockId, :setlistVersionItemId, :sourceArrangementId,
                    :sourceArrangementVersionRef, :effectiveKey, :effectiveMode, :effectiveTempoBpm,
                    :effectiveTimeSignature, :effectiveDurationSeconds, :effectiveEnergyLevel,
                    :effectiveDifficultyLevel, :effectiveNotes, :rationale, :provenanceNote, :createdBy, :updatedBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", overrideRecord.servicePlanId())
                        .addValue("servicePlanBlockId", overrideRecord.servicePlanBlockId())
                        .addValue("setlistVersionItemId", overrideRecord.setlistVersionItemId())
                        .addValue("sourceArrangementId", overrideRecord.sourceArrangementId())
                        .addValue("sourceArrangementVersionRef", overrideRecord.sourceArrangementVersionRef())
                        .addValue("effectiveKey", overrideRecord.effectiveKey())
                        .addValue("effectiveMode", overrideRecord.effectiveMode())
                        .addValue("effectiveTempoBpm", overrideRecord.effectiveTempoBpm())
                        .addValue("effectiveTimeSignature", overrideRecord.effectiveTimeSignature())
                        .addValue("effectiveDurationSeconds", overrideRecord.effectiveDurationSeconds())
                        .addValue("effectiveEnergyLevel", overrideRecord.effectiveEnergyLevel())
                        .addValue("effectiveDifficultyLevel", overrideRecord.effectiveDifficultyLevel())
                        .addValue("effectiveNotes", overrideRecord.effectiveNotes())
                        .addValue("rationale", overrideRecord.rationale())
                        .addValue("provenanceNote", overrideRecord.provenanceNote())
                        .addValue("createdBy", overrideRecord.createdBy())
                        .addValue("updatedBy", overrideRecord.updatedBy()),
                UUID.class);
        return jdbcTemplate.queryForObject(
                "SELECT * FROM service_arrangement_overrides WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> mapArrangementOverride(rs));
    }

    private List<ControlledVocabularyEntry> listVocabulary(String tableName) {
        return jdbcTemplate.query(
                "SELECT code, display_name, sort_order, active, system_default FROM " + tableName + " ORDER BY sort_order",
                (rs, rowNum) -> new ControlledVocabularyEntry(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("active"),
                        rs.getBoolean("system_default")));
    }

    private RehearsalSessionRecord getSession(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM rehearsal_sessions WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> new RehearsalSessionRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("service_plan_id", UUID.class),
                        rs.getString("session_code"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(),
                        rs.getString("location"),
                        ReadinessStateCode.fromCode(rs.getString("readiness_state_code")),
                        instantOrNull(rs, "archived_at")));
    }

    private MapSqlParameterSource targetParameters(UUID servicePlanId, RehearsalTarget target) {
        return new MapSqlParameterSource()
                .addValue("servicePlanId", servicePlanId)
                .addValue("rehearsalSessionId", target.rehearsalSessionId())
                .addValue("targetTypeCode", target.targetTypeCode().code())
                .addValue("servicePlanBlockId", target.servicePlanBlockId())
                .addValue("setlistVersionItemId", target.setlistVersionItemId())
                .addValue("transitionFromBlockId", target.transitionFromBlockId())
                .addValue("transitionToBlockId", target.transitionToBlockId())
                .addValue("arrangementId", target.arrangementId())
                .addValue("teamRoleCode", target.teamRoleCode())
                .addValue("serviceTeamAssignmentId", target.serviceTeamAssignmentId())
                .addValue("rehearsalTeamAssignmentId", target.rehearsalTeamAssignmentId())
                .addValue("songAssignmentOverrideId", target.songAssignmentOverrideId());
    }

    private RehearsalNoteRecord mapNote(ResultSet rs) throws SQLException {
        return new RehearsalNoteRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                mapTarget(rs),
                rs.getString("note_body"),
                rs.getString("visibility_code"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }

    private RehearsalIssueRecord mapIssue(ResultSet rs) throws SQLException {
        return new RehearsalIssueRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                mapTarget(rs),
                IssueCategoryCode.fromCode(rs.getString("category_code")),
                IssueSeverityCode.fromCode(rs.getString("severity_code")),
                IssueStatusCode.fromCode(rs.getString("status_code")),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getString("detected_by"));
    }

    private RehearsalIssueActionRecord mapAction(ResultSet rs) throws SQLException {
        return new RehearsalIssueActionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("rehearsal_issue_id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                IssueActionStatusCode.fromCode(rs.getString("action_status_code")),
                rs.getString("action_summary"),
                IssueOwnerType.valueOf(rs.getString("owner_type").toUpperCase()),
                rs.getString("owner_actor"),
                rs.getString("owner_team_role_code"),
                rs.getObject("owner_service_assignment_id", UUID.class));
    }

    private RehearsalTarget mapTarget(ResultSet rs) throws SQLException {
        return new RehearsalTarget(
                RehearsalWorkflowModels.TargetTypeCode.fromCode(rs.getString("target_type_code")),
                rs.getObject("rehearsal_session_id", UUID.class),
                rs.getObject("service_plan_block_id", UUID.class),
                rs.getObject("setlist_version_item_id", UUID.class),
                rs.getObject("transition_from_block_id", UUID.class),
                rs.getObject("transition_to_block_id", UUID.class),
                rs.getObject("arrangement_id", UUID.class),
                rs.getString("team_role_code"),
                rs.getObject("service_team_assignment_id", UUID.class),
                rs.getObject("rehearsal_team_assignment_id", UUID.class),
                rs.getObject("song_assignment_override_id", UUID.class));
    }

    private ArrangementOverrideRecord mapArrangementOverride(ResultSet rs) throws SQLException {
        return new ArrangementOverrideRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("service_plan_id", UUID.class),
                rs.getObject("service_plan_block_id", UUID.class),
                rs.getObject("setlist_version_item_id", UUID.class),
                rs.getObject("source_arrangement_id", UUID.class),
                rs.getString("source_arrangement_version_ref"),
                rs.getString("effective_key"),
                rs.getString("effective_mode"),
                nullableInteger(rs, "effective_tempo_bpm"),
                rs.getString("effective_time_signature"),
                nullableInteger(rs, "effective_duration_seconds"),
                nullableInteger(rs, "effective_energy_level"),
                nullableInteger(rs, "effective_difficulty_level"),
                rs.getString("effective_notes"),
                rs.getString("rationale"),
                rs.getString("provenance_note"),
                rs.getString("created_by"),
                rs.getString("updated_by"));
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
