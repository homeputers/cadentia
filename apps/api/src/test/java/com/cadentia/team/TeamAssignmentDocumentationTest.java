package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class TeamAssignmentDocumentationTest {

    @Test
    void fixtureDocumentsRepresentativeScenariosAndPrivacySafeDiagnostics() throws IOException {
        // Arrange
        String fixtureSql = readResource("db/fixtures/team_assignment_fixture.sql");
        String resetSql = readResource("db/fixtures/reset_team_assignment_fixture.sql");

        // Act / Assert
        assertThat(fixtureSql)
                .contains("Sparse acoustic service")
                .contains("Full band service")
                .contains("Vocal-led service")
                .contains("Incomplete-team service")
                .contains("INSERT INTO musicians")
                .contains("INSERT INTO arrangement_suitability_profiles")
                .contains("INSERT INTO arrangement_suitability_slots")
                .contains("INSERT INTO service_team_assignments")
                .contains("INSERT INTO rehearsal_events")
                .contains("INSERT INTO rehearsal_team_assignments")
                .contains("INSERT INTO readiness_notes")
                .contains("arrangement_suitability")
                .contains("service_assignment")
                .contains("unapproved arrangement remains excluded")
                .doesNotContain("@example")
                .doesNotContain("555-");
        assertThat(resetSql)
                .contains("DELETE FROM readiness_notes")
                .contains("DELETE FROM service_team_assignments")
                .contains("DELETE FROM arrangement_suitability_profiles")
                .contains("DELETE FROM songs");
    }

    @Test
    void operationalDocsCoverRequiredMaintenanceWorkflowsAndLlmBoundary() throws IOException {
        // Arrange
        String runbook = readRepoFile("docs/runbooks/adr-023-team-assignment-operations.md");
        String architecture = readRepoFile("docs/ARCHITECTURE.md");
        String seedData = readRepoFile("docs/seed-data.md");
        String planIndex = readRepoFile("docs/implementation-plans/README.md");

        // Act / Assert
        assertThat(runbook)
                .contains("Roster setup workflow")
                .contains("Controlled-vocabulary maintenance")
                .contains("Availability collection")
                .contains("Service and rehearsal assignment workflow")
                .contains("Substitutions")
                .contains("Team-aware recommendation profiles")
                .contains("Diagnostics and evidence")
                .contains("Readiness rules")
                .contains("Missing instruments")
                .contains("Vocal range conflicts")
                .contains("Unavailable musicians")
                .contains("Incomplete teams")
                .contains("Stale vocabulary values")
                .contains("Authorization denials")
                .contains("LLMs parse request intent and structured slots only")
                .contains("Do not use manual database edits")
                .contains("as the primary workflow")
                .doesNotContain("UPDATE musicians SET");
        assertThat(architecture).contains("ADR-023 Team and Musician Assignment Architecture");
        assertThat(seedData).contains("ADR-023 Team Assignment Fixture Data");
        assertThat(planIndex).contains("ADR-023 Team Assignment Operations Runbook");
    }

    private static String readResource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private static String readRepoFile(String path) throws IOException {
        Path candidate = Path.of(path);
        if (Files.exists(candidate)) {
            return Files.readString(candidate);
        }
        return Files.readString(Path.of("../..", path));
    }
}
