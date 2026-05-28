package com.cadentia.serviceplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.serviceplan.ServicePlanModels.ServicePlanRecord;
import java.time.Instant;
import java.util.List;
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
class JdbcServicePlanRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcServicePlanRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        Flyway.configure().dataSource(dataSource).load().migrate();

        repository = new JdbcServicePlanRepository(jdbcTemplate);
    }

    @Test
    void createPersistsDraftPlan() {
        // Arrange
        Instant serviceDateTime = Instant.parse("2026-06-01T10:00:00Z");

        // Act
        ServicePlanRecord created = repository.create(
                serviceDateTime,
                "Sunday Service",
                "Faithfulness",
                "Psalm 100",
                "Opening service");

        // Assert
        assertThat(created.servicePlanId()).isNotNull();
        assertThat(created.status().name()).isEqualTo("DRAFT");
        assertThat(created.title()).isEqualTo("Sunday Service");
        assertThat(created.serviceDateTime()).isEqualTo(serviceDateTime);
    }

    @Test
    void updateMetadataPersistsChangedFields() {
        // Arrange
        ServicePlanRecord created = repository.create(
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faithfulness",
                "Psalm 100",
                "Opening service");

        // Act
        ServicePlanRecord updated = repository.updateMetadata(
                created.servicePlanId(),
                Instant.parse("2026-06-08T10:00:00Z"),
                "Updated Sunday Service",
                "Praise",
                "Psalm 150",
                "Updated notes");

        // Assert
        assertThat(updated.title()).isEqualTo("Updated Sunday Service");
        assertThat(updated.theme()).isEqualTo("Praise");
        assertThat(updated.scripture()).isEqualTo("Psalm 150");
        assertThat(updated.notes()).isEqualTo("Updated notes");
    }

    @Test
    void attachSetlistVersionPersistsAttachmentReference() {
        // Arrange
        ServicePlanRecord created = repository.create(
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faithfulness",
                "Psalm 100",
                "Opening service");
        UUID setlistId = UUID.randomUUID();
        UUID setlistVersionId = UUID.randomUUID();

        // Act
        ServicePlanRecord attached = repository.attachSetlistVersion(
                created.servicePlanId(),
                setlistId,
                setlistVersionId);

        // Assert
        assertThat(attached.attachments()).hasSize(1);
        assertThat(attached.attachments().get(0).setlistId()).isEqualTo(setlistId);
        assertThat(attached.attachments().get(0).setlistVersionId()).isEqualTo(setlistVersionId);
    }

    @Test
    void publishPersistsPublishedStateAndAuditFields() {
        // Arrange
        ServicePlanRecord created = repository.create(
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faithfulness",
                "Psalm 100",
                "Opening service");

        // Act
        ServicePlanRecord published = repository.publish(created.servicePlanId(), "tester", "ready");

        // Assert
        assertThat(published.status().name()).isEqualTo("PUBLISHED");
        assertThat(published.publishedBy()).isEqualTo("tester");
        assertThat(published.publishedAt()).isNotNull();
    }

    @Test
    void listReturnsPersistedPlansInResultSet() {
        // Arrange
        ServicePlanRecord created = repository.create(
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faithfulness",
                "Psalm 100",
                "Opening service");

        // Act
        List<ServicePlanRecord> listed = repository.list();

        // Assert
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).servicePlanId()).isEqualTo(created.servicePlanId());
    }
}
