package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class TeamAssignmentFixtureRegressionTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        Flyway.configure().dataSource(dataSource).load().migrate();

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/fixtures/reset_team_assignment_fixture.sql"),
                new ClassPathResource("db/fixtures/team_assignment_fixture.sql"));
        populator.execute(dataSource);
    }

    @Test
    void fixtureCoversRepresentativeTeamScenariosWithoutPrivatePersonnelData() {
        // Arrange / Act
        Integer musicianCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM musicians WHERE display_name LIKE '[TEST FIXTURE]%'",
                Map.of(),
                Integer.class);
        Integer serviceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_plans WHERE title LIKE '[TEST FIXTURE]%'",
                Map.of(),
                Integer.class);
        Integer privateContactCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM musicians
                        WHERE display_name LIKE '[TEST FIXTURE]%'
                          AND (email IS NOT NULL OR phone IS NOT NULL OR notes IS NOT NULL)
                        """,
                Map.of(),
                Integer.class);

        // Assert
        assertThat(musicianCount).isEqualTo(7);
        assertThat(serviceCount).isEqualTo(4);
        assertThat(privateContactCount).isZero();
    }

    @Test
    void approvedSuitabilityViewsExcludeUnapprovedArrangementsRegardlessOfTeamReadiness() {
        // Arrange / Act
        Integer approvedProfiles = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM v_approved_arrangement_suitability_profiles
                        WHERE arrangement_id::text LIKE '23700000-0000-0000-0000-0000000000%'
                        """,
                Map.of(),
                Integer.class);
        Integer unapprovedProfiles = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM v_approved_arrangement_suitability_profiles
                        WHERE arrangement_id = '23700000-0000-0000-0000-000000000004'
                        """,
                Map.of(),
                Integer.class);
        String incompleteReadiness = jdbcTemplate.queryForObject(
                """
                        SELECT readiness_status_code
                        FROM v_service_plan_readiness_summary
                        WHERE service_plan_id = '23000000-0000-0000-0000-000000000004'
                        """,
                Map.of(),
                String.class);

        // Assert
        assertThat(approvedProfiles).isEqualTo(3);
        assertThat(unapprovedProfiles).isZero();
        assertThat(incompleteReadiness).isEqualTo("BLOCKED");
    }

    @Test
    void fixturePreservesAssignmentLifecycleAndDiagnosticEvidenceBoundaries() throws IOException {
        // Arrange / Act
        Integer substituteCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM service_team_assignments
                        WHERE status_code = 'SUBSTITUTE'
                          AND substitute_for_assignment_id IS NOT NULL
                        """,
                Map.of(),
                Integer.class);
        Integer privateReadinessNoteCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM readiness_notes
                        WHERE service_plan_id::text LIKE '23000000-0000-0000-0000-0000000000%'
                          AND human_note IS NOT NULL
                        """,
                Map.of(),
                Integer.class);
        String fixtureSql = new ClassPathResource("db/fixtures/team_assignment_fixture.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // Assert
        assertThat(substituteCount).isEqualTo(1);
        assertThat(privateReadinessNoteCount).isZero();
        assertThat(fixtureSql)
                .contains("arrangement_suitability")
                .contains("service_assignment")
                .contains("LLMs parse intent only")
                .doesNotContain("@example")
                .doesNotContain("555-");
    }
}
