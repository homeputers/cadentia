package com.cadentia.rehearsal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRehearsalWorkflowRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcRehearsalWorkflowRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        Flyway.configure().dataSource(dataSource).load().migrate();

        repository = new JdbcRehearsalWorkflowRepository(jdbcTemplate);
    }

    @Test
    void controlledVocabularySeedsUseStableCodesForReadinessAndStructuredIssues() {
        // Arrange / Act
        List<String> readinessCodes = repository.listReadinessStates().stream()
                .map(RehearsalWorkflowModels.ControlledVocabularyEntry::code)
                .toList();
        List<String> issueCategoryCodes = repository.listIssueCategories().stream()
                .map(RehearsalWorkflowModels.ControlledVocabularyEntry::code)
                .toList();
        List<String> issueStatusCodes = repository.listIssueStatuses().stream()
                .map(RehearsalWorkflowModels.ControlledVocabularyEntry::code)
                .toList();

        // Assert
        assertThat(readinessCodes).containsExactly("draft", "planned", "rehearsing", "issues_open", "ready", "completed");
        assertThat(issueCategoryCodes).containsExactly(
                "unresolved_transition",
                "difficult_song",
                "blocker",
                "arrangement_concern",
                "team_role_concern",
                "general_follow_up");
        assertThat(issueStatusCodes).containsExactly("open", "in_progress", "resolved", "deferred", "cancelled");
    }

    @Test
    void persistsSessionsReadinessHistoryNotesIssuesAndOwnedActionsWithinServiceScope() {
        // Arrange
        UUID servicePlanId = insertServicePlan();
        UUID arrangementId = insertArrangement();
        UUID blockId = insertServicePlanBlock(servicePlanId, arrangementId, 0);
        UUID nextBlockId = insertServicePlanBlock(servicePlanId, arrangementId, 1);

        // Act
        RehearsalSessionRecord session = repository.createSession(
                servicePlanId,
                "midweek",
                Instant.parse("2026-06-04T23:00:00Z"),
                Instant.parse("2026-06-05T01:00:00Z"),
                "Sanctuary",
                "planner");
        repository.recordServiceReadiness(
                servicePlanId,
                session.rehearsalSessionId(),
                ReadinessStateCode.REHEARSING,
                "Band is walking through transitions.",
                "planner");
        RehearsalNoteRecord note = repository.addNote(
                servicePlanId,
                RehearsalTarget.transition(blockId, nextBlockId),
                "Hold pad between songs until count-in is clear.",
                "team_private",
                "music-director");
        RehearsalIssueRecord issue = repository.createIssue(
                servicePlanId,
                RehearsalTarget.transition(blockId, nextBlockId),
                IssueCategoryCode.UNRESOLVED_TRANSITION,
                IssueSeverityCode.HIGH,
                IssueStatusCode.OPEN,
                "Transition needs count-in",
                "Drums and acoustic are entering on different beats.",
                "music-director");
        RehearsalIssueActionRecord action = repository.addIssueAction(
                servicePlanId,
                issue.issueId(),
                IssueActionStatusCode.TODO,
                "Publish a one-bar count-in cue.",
                IssueOwnerType.TEAM_ROLE,
                null,
                "MUSIC_DIRECTOR",
                null,
                "music-director");

        // Assert
        assertThat(session.readinessStateCode()).isEqualTo(ReadinessStateCode.DRAFT);
        assertThat(note.target().targetTypeCode()).isEqualTo(RehearsalWorkflowModels.TargetTypeCode.TRANSITION);
        assertThat(issue.categoryCode()).isEqualTo(IssueCategoryCode.UNRESOLVED_TRANSITION);
        assertThat(action.ownerType()).isEqualTo(IssueOwnerType.TEAM_ROLE);
        assertThat(countRows("rehearsal_readiness_history", servicePlanId)).isEqualTo(1);
        assertThat(currentReadiness(servicePlanId)).isEqualTo("rehearsing");
    }

    @Test
    void rejectsCrossServiceTargetsAndCascadesWorkflowRowsWhenServicePlanIsDeleted() {
        // Arrange
        UUID servicePlanId = insertServicePlan();
        UUID otherServicePlanId = insertServicePlan();
        UUID arrangementId = insertArrangement();
        UUID foreignBlockId = insertServicePlanBlock(otherServicePlanId, arrangementId, 0);
        RehearsalSessionRecord session = repository.createSession(
                servicePlanId,
                "primary",
                Instant.parse("2026-06-04T23:00:00Z"),
                Instant.parse("2026-06-05T01:00:00Z"),
                "Sanctuary",
                "planner");
        repository.addNote(servicePlanId, RehearsalTarget.session(session.rehearsalSessionId()), "Warmups at 6:45.",
                "team_private", "planner");

        // Act / Assert
        RehearsalSessionRecord archived = repository.archiveSession(
                servicePlanId, session.rehearsalSessionId(), "planner");

        assertThat(archived.archivedAt()).isNotNull();
        assertThatThrownBy(() -> repository.addNote(
                        servicePlanId,
                        RehearsalTarget.setlistItem(foreignBlockId, null),
                        "This target belongs to a different service.",
                        "team_private",
                        "planner"))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("DELETE FROM service_plans WHERE id = :id", Map.of("id", servicePlanId));
        assertThat(countRows("rehearsal_sessions", servicePlanId)).isZero();
        assertThat(countRows("rehearsal_notes", servicePlanId)).isZero();
    }

    @Test
    void arrangementOverridesStoreEffectiveServiceValuesWithoutMutatingCanonicalArrangement() {
        // Arrange
        UUID servicePlanId = insertServicePlan();
        UUID arrangementId = insertArrangement();
        UUID blockId = insertServicePlanBlock(servicePlanId, arrangementId, 0);
        String canonicalKeyBefore = arrangementKey(arrangementId);

        // Act
        ArrangementOverrideRecord override = repository.createArrangementOverride(new ArrangementOverrideRecord(
                null,
                servicePlanId,
                blockId,
                null,
                arrangementId,
                "arrangement:baseline",
                "G",
                "MAJOR",
                74,
                "4/4",
                260,
                3,
                2,
                "Acoustic intro only for this service.",
                "Vocal range is better in G for the assigned leader.",
                "Copied from canonical arrangement and adjusted for this service.",
                "planner",
                "planner"));

        // Assert
        assertThat(override.arrangementOverrideId()).isNotNull();
        assertThat(override.effectiveKey()).isEqualTo("G");
        assertThat(override.sourceArrangementId()).isEqualTo(arrangementId);
        assertThat(arrangementKey(arrangementId)).isEqualTo(canonicalKeyBefore);
    }

    private UUID insertServicePlan() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plans (service_date_time, title, theme, scripture, notes)
                VALUES (NOW(), 'Sunday Service', 'Faithfulness', 'Psalm 100', '')
                RETURNING id
                """,
                Map.of(),
                UUID.class);
    }

    private UUID insertArrangement() {
        UUID songId = jdbcTemplate.queryForObject(
                """
                INSERT INTO songs (title, normalized_title, artist, source_reference, ccli_number, created_by)
                VALUES (:title, :normalizedTitle, 'Cadentia Test', 'fixture', :ccliNumber, 'test')
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("title", "Test Song " + UUID.randomUUID())
                        .addValue("normalizedTitle", "test-song-" + UUID.randomUUID())
                        .addValue("ccliNumber", UUID.randomUUID().toString()),
                UUID.class);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO arrangements (
                    song_id, name, normalized_name, source_type, language, musical_key, key_mode, tempo_bpm
                ) VALUES (
                    :songId, 'Default', :normalizedName, 'CUSTOM', 'en', 'D', 'MAJOR', 72
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("songId", songId)
                        .addValue("normalizedName", "default-" + UUID.randomUUID()),
                UUID.class);
    }

    private UUID insertServicePlanBlock(UUID servicePlanId, UUID arrangementId, int positionIndex) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plan_blocks (service_plan_id, block_type, position_index, arrangement_id)
                VALUES (:servicePlanId, 'praise', :positionIndex, :arrangementId)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("positionIndex", positionIndex)
                        .addValue("arrangementId", arrangementId),
                UUID.class);
    }

    private int countRows(String tableName, UUID servicePlanId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE service_plan_id = :servicePlanId",
                Map.of("servicePlanId", servicePlanId),
                Integer.class);
        return count == null ? 0 : count;
    }

    private String currentReadiness(UUID servicePlanId) {
        return jdbcTemplate.queryForObject(
                "SELECT readiness_state_code FROM service_rehearsal_workflow_states WHERE service_plan_id = :servicePlanId",
                Map.of("servicePlanId", servicePlanId),
                String.class);
    }

    private String arrangementKey(UUID arrangementId) {
        return jdbcTemplate.queryForObject(
                "SELECT musical_key FROM arrangements WHERE id = :arrangementId",
                Map.of("arrangementId", arrangementId),
                String.class);
    }
}
