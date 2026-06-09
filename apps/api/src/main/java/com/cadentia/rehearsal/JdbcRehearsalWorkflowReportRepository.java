package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalCompletedServiceHistoryRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportActionRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportIssueRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportServiceRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionArchiveResult;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionConfiguration;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.TargetTypeCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRehearsalWorkflowReportRepository implements RehearsalWorkflowReportRepository {

    private static final String SERVICE_REPORT_SELECT = """
            WITH issue_counts AS (
                SELECT service_plan_id,
                       COUNT(*) FILTER (WHERE status_code IN ('open', 'in_progress')
                           AND (severity_code = 'blocking' OR category_code = 'blocker')) AS open_blocking_issue_count,
                       COUNT(*) FILTER (WHERE status_code IN ('open', 'in_progress') AND category_code = 'blocker')
                           AS open_blocker_count,
                       COUNT(*) FILTER (WHERE status_code IN ('open', 'in_progress')
                           AND category_code = 'unresolved_transition') AS unresolved_transition_issue_count,
                       COUNT(*) FILTER (WHERE status_code IN ('open', 'in_progress')
                           AND category_code = 'difficult_song') AS difficult_song_issue_count
                FROM rehearsal_issues
                WHERE archived_at IS NULL
                GROUP BY service_plan_id
            ), action_counts AS (
                SELECT service_plan_id,
                       COUNT(*) FILTER (WHERE action_status_code IN ('todo', 'in_progress')) AS open_required_action_count,
                       COUNT(*) FILTER (WHERE action_status_code IN ('todo', 'in_progress')
                           AND due_at IS NOT NULL AND due_at < :asOf) AS overdue_owner_action_count
                FROM rehearsal_issue_actions
                GROUP BY service_plan_id
            ), override_counts AS (
                SELECT service_plan_id, COUNT(*) AS active_override_count
                FROM service_arrangement_overrides
                WHERE archived_at IS NULL
                GROUP BY service_plan_id
            )
            SELECT service_plans.id AS service_plan_id,
                   COALESCE(states.readiness_state_code, 'draft') AS explicit_state_code,
                   CASE WHEN COALESCE(states.readiness_state_code, 'draft') IN ('ready', 'completed')
                         AND (COALESCE(issue_counts.open_blocking_issue_count, 0) > 0
                              OR COALESCE(action_counts.open_required_action_count, 0) > 0)
                        THEN 'issues_open'
                        ELSE COALESCE(states.readiness_state_code, 'draft')
                   END AS derived_state_code,
                   service_plans.service_date_time,
                   COALESCE(issue_counts.open_blocking_issue_count, 0) AS open_blocking_issue_count,
                   COALESCE(action_counts.open_required_action_count, 0) AS open_required_action_count,
                   COALESCE(issue_counts.open_blocker_count, 0) AS open_blocker_count,
                   COALESCE(issue_counts.unresolved_transition_issue_count, 0) AS unresolved_transition_issue_count,
                   COALESCE(issue_counts.difficult_song_issue_count, 0) AS difficult_song_issue_count,
                   COALESCE(action_counts.overdue_owner_action_count, 0) AS overdue_owner_action_count,
                   COALESCE(override_counts.active_override_count, 0) AS active_override_count
            FROM service_plans
            LEFT JOIN service_rehearsal_workflow_states states ON states.service_plan_id = service_plans.id
            LEFT JOIN issue_counts ON issue_counts.service_plan_id = service_plans.id
            LEFT JOIN action_counts ON action_counts.service_plan_id = service_plans.id
            LEFT JOIN override_counts ON override_counts.service_plan_id = service_plans.id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRehearsalWorkflowReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RehearsalReportServiceRow> listServicesBlockedFromReadiness(Instant asOf) {
        return jdbcTemplate.query(
                SERVICE_REPORT_SELECT + """
                        WHERE COALESCE(issue_counts.open_blocking_issue_count, 0) > 0
                           OR COALESCE(action_counts.open_required_action_count, 0) > 0
                        ORDER BY service_plans.service_date_time
                        """,
                Map.of("asOf", Timestamp.from(asOf)),
                (rs, rowNum) -> mapServiceRow(rs));
    }

    @Override
    public List<RehearsalReportIssueRow> listOpenBlockersByService(UUID servicePlanId) {
        return issueReport("""
                WHERE service_plan_id = :servicePlanId
                  AND archived_at IS NULL
                  AND status_code IN ('open', 'in_progress')
                  AND (severity_code = 'blocking' OR category_code = 'blocker')
                ORDER BY created_at
                """, servicePlanId);
    }

    @Override
    public List<RehearsalReportIssueRow> listUnresolvedTransitionIssues(UUID servicePlanId) {
        return issueReport("""
                WHERE (:servicePlanId IS NULL OR service_plan_id = :servicePlanId)
                  AND archived_at IS NULL
                  AND status_code IN ('open', 'in_progress')
                  AND category_code = 'unresolved_transition'
                ORDER BY created_at
                """, servicePlanId);
    }

    @Override
    public List<RehearsalReportIssueRow> listDifficultSongs(UUID servicePlanId) {
        return issueReport("""
                WHERE (:servicePlanId IS NULL OR service_plan_id = :servicePlanId)
                  AND archived_at IS NULL
                  AND status_code IN ('open', 'in_progress')
                  AND category_code = 'difficult_song'
                ORDER BY created_at
                """, servicePlanId);
    }

    @Override
    public List<RehearsalReportActionRow> listOverdueOwnerActions(Instant asOf, UUID servicePlanId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM rehearsal_issue_actions
                WHERE (:servicePlanId IS NULL OR service_plan_id = :servicePlanId)
                  AND action_status_code IN ('todo', 'in_progress')
                  AND due_at IS NOT NULL
                  AND due_at < :asOf
                ORDER BY due_at
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("asOf", Timestamp.from(asOf)),
                (rs, rowNum) -> new RehearsalReportActionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("rehearsal_issue_id", UUID.class),
                        rs.getObject("service_plan_id", UUID.class),
                        IssueActionStatusCode.fromCode(rs.getString("action_status_code")),
                        IssueOwnerType.fromCode(rs.getString("owner_type")),
                        rs.getString("owner_team_role_code"),
                        rs.getObject("owner_service_assignment_id", UUID.class),
                        instantOrNull(rs, "due_at"),
                        instantOrNull(rs, "completed_at")));
    }

    @Override
    public List<RehearsalReportServiceRow> listServicesWithActiveArrangementOverrides() {
        return jdbcTemplate.query(
                SERVICE_REPORT_SELECT + """
                        WHERE COALESCE(override_counts.active_override_count, 0) > 0
                        ORDER BY service_plans.service_date_time
                        """,
                Map.of("asOf", Timestamp.from(Instant.now())),
                (rs, rowNum) -> mapServiceRow(rs));
    }

    @Override
    public List<RehearsalCompletedServiceHistoryRow> listCompletedServiceHistory(
            Instant completedSince,
            Instant completedBefore) {
        return jdbcTemplate.query(
                """
                WITH completed AS (
                    SELECT service_plans.id AS service_plan_id,
                           service_plans.service_date_time,
                           MAX(history.changed_at) FILTER (WHERE history.new_state_code = 'completed') AS completed_at
                    FROM service_plans
                    JOIN service_rehearsal_workflow_states states
                      ON states.service_plan_id = service_plans.id
                     AND states.readiness_state_code = 'completed'
                    LEFT JOIN rehearsal_readiness_history history
                      ON history.service_plan_id = service_plans.id
                    GROUP BY service_plans.id, service_plans.service_date_time
                )
                SELECT completed.service_plan_id,
                       completed.service_date_time,
                       completed.completed_at,
                       COUNT(DISTINCT sessions.id) AS session_count,
                       COUNT(DISTINCT sessions.id) FILTER (WHERE sessions.archived_at IS NOT NULL) AS archived_session_count,
                       COUNT(DISTINCT issues.id) AS issue_count,
                       COUNT(DISTINCT issues.id) FILTER (WHERE issues.status_code = 'resolved') AS resolved_issue_count,
                       COUNT(DISTINCT overrides.id) AS override_count,
                       COUNT(DISTINCT audit.id) AS audit_event_count
                FROM completed
                LEFT JOIN rehearsal_sessions sessions ON sessions.service_plan_id = completed.service_plan_id
                LEFT JOIN rehearsal_issues issues ON issues.service_plan_id = completed.service_plan_id
                LEFT JOIN service_arrangement_overrides overrides ON overrides.service_plan_id = completed.service_plan_id
                LEFT JOIN privileged_action_audit_events audit
                  ON audit.metadata ->> 'servicePlanId' = completed.service_plan_id::text
                WHERE (:completedSince IS NULL OR completed.completed_at >= :completedSince)
                  AND (:completedBefore IS NULL OR completed.completed_at < :completedBefore)
                GROUP BY completed.service_plan_id, completed.service_date_time, completed.completed_at
                ORDER BY completed.service_date_time DESC
                """,
                new MapSqlParameterSource()
                        .addValue("completedSince", timestampOrNull(completedSince))
                        .addValue("completedBefore", timestampOrNull(completedBefore)),
                (rs, rowNum) -> new RehearsalCompletedServiceHistoryRow(
                        rs.getObject("service_plan_id", UUID.class),
                        rs.getTimestamp("service_date_time").toInstant(),
                        instantOrNull(rs, "completed_at"),
                        rs.getInt("session_count"),
                        rs.getInt("archived_session_count"),
                        rs.getInt("issue_count"),
                        rs.getInt("resolved_issue_count"),
                        rs.getInt("override_count"),
                        rs.getInt("audit_event_count")));
    }

    @Override
    public RehearsalRetentionArchiveResult archiveCompletedRehearsalData(
            RehearsalRetentionConfiguration retentionConfiguration,
            Instant asOf,
            String archivedBy) {
        RehearsalRetentionConfiguration valid = validate(retentionConfiguration);
        int sessions = archive("rehearsal_sessions", "archived_by = :archivedBy, updated_by = :archivedBy, updated_at = NOW(),",
                valid.completedSessionsRetainDays(), asOf, archivedBy);
        int notes = archive("rehearsal_notes", "updated_by = :archivedBy, updated_at = NOW(),", valid.notesRetainDays(), asOf, archivedBy);
        int issues = archive("rehearsal_issues", "", valid.issuesRetainDays(), asOf, archivedBy);
        int overrides = archive("service_arrangement_overrides", "updated_by = :archivedBy, updated_at = NOW(),",
                valid.overridesRetainDays(), asOf, archivedBy);
        Instant auditCutoff = asOf.minus(valid.auditRetainDays(), ChronoUnit.DAYS);
        Integer retainedAudits = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM privileged_action_audit_events
                WHERE action LIKE 'REHEARSAL_%'
                  AND retention_until >= :auditCutoff
                """,
                Map.of("auditCutoff", Timestamp.from(auditCutoff)),
                Integer.class);
        return new RehearsalRetentionArchiveResult(
                asOf, sessions, notes, issues, overrides, retainedAudits == null ? 0 : retainedAudits);
    }

    private int archive(String tableName, String additionalSetClause, int retainDays, Instant asOf, String archivedBy) {
        Instant cutoff = asOf.minus(retainDays, ChronoUnit.DAYS);
        return jdbcTemplate.update(
                """
                UPDATE %s target
                SET %s archived_at = :asOf
                WHERE target.archived_at IS NULL
                  AND target.service_plan_id IN (
                      SELECT states.service_plan_id
                      FROM service_rehearsal_workflow_states states
                      JOIN service_plans ON service_plans.id = states.service_plan_id
                      WHERE states.readiness_state_code = 'completed'
                        AND service_plans.service_date_time < :cutoff
                  )
                """.formatted(tableName, additionalSetClause),
                new MapSqlParameterSource()
                        .addValue("asOf", Timestamp.from(asOf))
                        .addValue("cutoff", Timestamp.from(cutoff))
                        .addValue("archivedBy", archivedBy));
    }

    private RehearsalRetentionConfiguration validate(RehearsalRetentionConfiguration configuration) {
        RehearsalRetentionConfiguration candidate = configuration == null
                ? RehearsalRetentionConfiguration.defaults()
                : configuration;
        if (candidate.completedSessionsRetainDays() < candidate.minCompletedSessionsRetainDays()
                || candidate.notesRetainDays() < candidate.minNotesRetainDays()
                || candidate.issuesRetainDays() < candidate.minIssuesRetainDays()
                || candidate.overridesRetainDays() < candidate.minOverridesRetainDays()
                || candidate.auditRetainDays() < candidate.minAuditRetainDays()) {
            throw new RehearsalWorkflowException("Rehearsal retention configuration is below minimum accountable history limits.");
        }
        return candidate;
    }

    private List<RehearsalReportIssueRow> issueReport(String whereClause, UUID servicePlanId) {
        return jdbcTemplate.query(
                "SELECT * FROM rehearsal_issues " + whereClause,
                new MapSqlParameterSource().addValue("servicePlanId", servicePlanId),
                (rs, rowNum) -> new RehearsalReportIssueRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("service_plan_id", UUID.class),
                        mapTarget(rs),
                        IssueCategoryCode.fromCode(rs.getString("category_code")),
                        IssueSeverityCode.fromCode(rs.getString("severity_code")),
                        IssueStatusCode.fromCode(rs.getString("status_code")),
                        rs.getTimestamp("created_at").toInstant(),
                        instantOrNull(rs, "resolved_at")));
    }

    private RehearsalReportServiceRow mapServiceRow(ResultSet rs) throws SQLException {
        return new RehearsalReportServiceRow(
                rs.getObject("service_plan_id", UUID.class),
                ReadinessStateCode.fromCode(rs.getString("explicit_state_code")),
                ReadinessStateCode.fromCode(rs.getString("derived_state_code")),
                rs.getTimestamp("service_date_time").toInstant(),
                rs.getInt("open_blocking_issue_count"),
                rs.getInt("open_required_action_count"),
                rs.getInt("open_blocker_count"),
                rs.getInt("unresolved_transition_issue_count"),
                rs.getInt("difficult_song_issue_count"),
                rs.getInt("overdue_owner_action_count"),
                rs.getInt("active_override_count"));
    }

    private RehearsalTarget mapTarget(ResultSet rs) throws SQLException {
        return new RehearsalTarget(
                TargetTypeCode.fromCode(rs.getString("target_type_code")),
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

    private Instant instantOrNull(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
