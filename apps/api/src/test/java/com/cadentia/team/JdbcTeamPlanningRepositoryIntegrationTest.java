package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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
class JdbcTeamPlanningRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTeamPlanningRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        Flyway.configure().dataSource(dataSource).load().migrate();

        repository = new JdbcTeamPlanningRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void migrationSeedsStableControlledVocabularyDefaults() {
        // Arrange / Act / Assert
        assertThat(repository.listAssignmentStatuses())
                .extracting("code")
                .containsExactly("REQUESTED", "TENTATIVE", "ACCEPTED", "DECLINED", "UNAVAILABLE", "SUBSTITUTE");
        assertThat(repository.listMusicianRoles())
                .extracting("code")
                .contains("WORSHIP_LEADER", "VOCALIST", "INSTRUMENTALIST", "MUSIC_DIRECTOR");
        assertThat(repository.listInstruments())
                .extracting("code")
                .contains("BRASS", "WINDS", "OTHER");
        assertThat(repository.listInstruments())
                .filteredOn(entry -> entry.code().equals("ACOUSTIC_GUITAR"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.systemDefault()).isTrue();
                    assertThat(entry.localExtension()).isFalse();
                    assertThat(entry.active()).isTrue();
                });
    }

    @Test
    void createMusicianPersistsOptionalAccountAndContactFields() {
        // Arrange
        CreateMusicianCommand command = new CreateMusicianCommand(
                "Jordan Lee",
                null,
                null,
                null,
                VocalRangeCode.MEDIUM,
                48,
                72,
                ServingPreferenceCode.PREFERRED,
                "team-admin");

        // Act
        MusicianRecord musician = repository.createMusician(command);

        // Assert
        assertThat(musician.musicianId()).isNotNull();
        assertThat(musician.displayName()).isEqualTo("Jordan Lee");
        assertThat(musician.accountPrincipal()).isNull();
        assertThat(musician.email()).isNull();
        assertThat(musician.phone()).isNull();
        assertThat(musician.primaryVocalRangeCode()).isEqualTo(VocalRangeCode.MEDIUM);
        assertThat(musician.servingPreferenceCode()).isEqualTo(ServingPreferenceCode.PREFERRED);
    }

    @Test
    void constraintsRejectInvalidVocalRangeAndDuplicateActiveInstrumentAssignment() {
        // Arrange
        MusicianRecord musician = createMusician("Casey Morgan");
        repository.assignInstrument(musician.musicianId(), InstrumentCode.ACOUSTIC_GUITAR, SkillLevelCode.INTERMEDIATE);

        // Act / Assert
        assertThatThrownBy(() -> repository.createMusician(new CreateMusicianCommand(
                        "Invalid Range",
                        null,
                        null,
                        null,
                        VocalRangeCode.UNKNOWN,
                        72,
                        48,
                        null,
                        "team-admin")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.assignInstrument(
                        musician.musicianId(), InstrumentCode.ACOUSTIC_GUITAR, SkillLevelCode.ADVANCED))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void persistsAvailabilityServiceAssignmentAndRehearsalAssignmentWithControlledStatuses() {
        // Arrange
        UUID servicePlanId = insertServicePlan();
        MusicianRecord musician = createMusician("Avery Rivera");

        // Act
        var availability = repository.createAvailabilityWindow(
                musician.musicianId(),
                Instant.parse("2026-06-07T08:00:00Z"),
                Instant.parse("2026-06-07T12:00:00Z"),
                AssignmentStatusCode.TENTATIVE,
                servicePlanId);
        ServiceAssignmentRecord serviceAssignment = repository.createServiceAssignment(
                servicePlanId,
                musician.musicianId(),
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.PIANO,
                null,
                AssignmentStatusCode.ACCEPTED);
        RehearsalEventRecord rehearsal = repository.createRehearsalEvent(
                servicePlanId,
                Instant.parse("2026-06-04T23:00:00Z"),
                Instant.parse("2026-06-05T01:00:00Z"),
                "Sanctuary");
        RehearsalAssignmentRecord rehearsalAssignment = repository.createRehearsalAssignment(
                rehearsal.rehearsalEventId(),
                servicePlanId,
                musician.musicianId(),
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.PIANO,
                null,
                AssignmentStatusCode.REQUESTED);

        // Assert
        assertThat(availability.statusCode()).isEqualTo(AssignmentStatusCode.TENTATIVE);
        assertThat(serviceAssignment.statusCode()).isEqualTo(AssignmentStatusCode.ACCEPTED);
        assertThat(rehearsal.servicePlanId()).isEqualTo(servicePlanId);
        assertThat(rehearsalAssignment.rehearsalEventId()).isEqualTo(rehearsal.rehearsalEventId());
        assertThat(rehearsalAssignment.statusCode()).isEqualTo(AssignmentStatusCode.REQUESTED);
    }

    @Test
    void songAssignmentOverridesMustBelongToSameServiceContextAndSongBlock() {
        // Arrange
        UUID servicePlanId = insertServicePlan();
        UUID otherServicePlanId = insertServicePlan();
        UUID songBlockId = insertServicePlanBlock(servicePlanId, UUID.randomUUID());
        UUID nonSongBlockId = insertServicePlanBlock(servicePlanId, null);
        MusicianRecord musician = createMusician("Riley Chen");
        ServiceAssignmentRecord assignment = repository.createServiceAssignment(
                servicePlanId,
                musician.musicianId(),
                MusicianRoleCode.VOCALIST,
                null,
                VocalPartCode.LEAD,
                AssignmentStatusCode.ACCEPTED);

        // Act
        SongAssignmentOverrideRecord override = repository.createSongAssignmentOverride(
                servicePlanId,
                songBlockId,
                assignment.assignmentId(),
                musician.musicianId(),
                MusicianRoleCode.VOCALIST,
                null,
                VocalPartCode.ALTO,
                AssignmentStatusCode.SUBSTITUTE);

        // Assert
        assertThat(override.servicePlanBlockId()).isEqualTo(songBlockId);
        assertThat(override.statusCode()).isEqualTo(AssignmentStatusCode.SUBSTITUTE);
        assertThatThrownBy(() -> repository.createSongAssignmentOverride(
                        otherServicePlanId,
                        songBlockId,
                        assignment.assignmentId(),
                        musician.musicianId(),
                        MusicianRoleCode.VOCALIST,
                        null,
                        VocalPartCode.BACKGROUND,
                        AssignmentStatusCode.REQUESTED))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.createSongAssignmentOverride(
                        servicePlanId,
                        nonSongBlockId,
                        assignment.assignmentId(),
                        musician.musicianId(),
                        MusicianRoleCode.VOCALIST,
                        null,
                        VocalPartCode.BACKGROUND,
                        AssignmentStatusCode.REQUESTED))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void constraintsRejectOrphanedAndInvalidAvailabilityAssignments() {
        // Arrange
        MusicianRecord musician = createMusician("Taylor Brooks");
        UUID missingServicePlanId = UUID.randomUUID();

        // Act / Assert
        assertThatThrownBy(() -> repository.createAvailabilityWindow(
                        musician.musicianId(),
                        Instant.parse("2026-06-07T12:00:00Z"),
                        Instant.parse("2026-06-07T08:00:00Z"),
                        AssignmentStatusCode.UNAVAILABLE,
                        null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.createServiceAssignment(
                        missingServicePlanId,
                        musician.musicianId(),
                        MusicianRoleCode.INSTRUMENTALIST,
                        InstrumentCode.DRUMS,
                        null,
                        AssignmentStatusCode.DECLINED))
                .isInstanceOf(DataAccessException.class);
    }

    private MusicianRecord createMusician(String displayName) {
        return repository.createMusician(new CreateMusicianCommand(
                displayName,
                null,
                null,
                null,
                VocalRangeCode.UNKNOWN,
                null,
                null,
                null,
                "team-admin"));
    }

    private UUID insertServicePlan() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plans (service_date_time, title, theme, scripture, notes)
                VALUES (:serviceDateTime, 'Sunday Service', 'Faithfulness', 'Psalm 100', '')
                RETURNING id
                """,
                Map.of("serviceDateTime", Timestamp.from(Instant.parse("2026-06-07T10:00:00Z"))),
                UUID.class);
    }

    private UUID insertServicePlanBlock(UUID servicePlanId, UUID arrangementId) {
        Integer positionIndex = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_plan_blocks WHERE service_plan_id = :servicePlanId",
                Map.of("servicePlanId", servicePlanId),
                Integer.class);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO service_plan_blocks (service_plan_id, block_type, position_index, arrangement_id)
                VALUES (:servicePlanId, 'worship', :positionIndex, :arrangementId)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("servicePlanId", servicePlanId)
                        .addValue("positionIndex", positionIndex)
                        .addValue("arrangementId", arrangementId),
                UUID.class);
    }
}
