package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.team.PersonnelAuditModels.PersonnelAuditAction;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditTargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PersonnelPrivilegedAuditServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private NamedParameterJdbcTemplate jdbcTemplate;
    private PersonnelPrivilegedAuditService auditService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        Flyway.configure().dataSource(dataSource).load().migrate();

        auditService = new PersonnelPrivilegedAuditService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void recordPersistsRequiredPersonnelAuditFieldsWithoutSensitiveContent() {
        // Arrange
        UUID targetId = UUID.randomUUID();
        PersonnelAuditEvent event = new PersonnelAuditEvent(
                "team-admin",
                Set.of("role.team_scheduler"),
                PersonnelAuditAction.PERSONNEL_AVAILABILITY_CHANGED,
                PersonnelAuditTargetType.AVAILABILITY_WINDOW,
                targetId,
                "availability_update",
                "request-456",
                "db://musician_availability_windows/before",
                "db://musician_availability_windows/after",
                "before-hash",
                "after-hash",
                Map.of("fields", "statusCode,startsAt,endsAt"));

        // Act
        UUID auditId = auditService.record(event);

        // Assert
        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT actor, action, target_type, target_id, request_id, before_state_ref, after_state_ref,
                       before_state_hash, after_state_hash, metadata::text AS metadata_json, occurred_at
                FROM privileged_action_audit_events
                WHERE id = :id
                """,
                Map.of("id", auditId));
        assertThat(row)
                .containsEntry("actor", "team-admin")
                .containsEntry("action", "PERSONNEL_AVAILABILITY_CHANGED")
                .containsEntry("target_type", "AVAILABILITY_WINDOW")
                .containsEntry("target_id", targetId)
                .containsEntry("request_id", "request-456")
                .containsEntry("before_state_hash", "before-hash")
                .containsEntry("after_state_hash", "after-hash");
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(row.get("metadata_json").toString())
                .contains("availability_update")
                .doesNotContain("555", "medical", "pastoral", "email");
    }
}
