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
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new JdbcServicePlanRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void createUpdateAttachAndPublishExecuteDeterministically() {
        ServicePlanRecord created = repository.create(
                Instant.parse("2026-06-01T10:00:00Z"),
                "Sunday Service",
                "Faithfulness",
                "Psalm 100",
                "Opening service");

        assertThat(created.servicePlanId()).isNotNull();
        assertThat(created.status().name()).isEqualTo("DRAFT");

        ServicePlanRecord updated = repository.updateMetadata(
                created.servicePlanId(),
                Instant.parse("2026-06-08T10:00:00Z"),
                "Updated Sunday Service",
                "Praise",
                "Psalm 150",
                "Updated notes");
        assertThat(updated.title()).isEqualTo("Updated Sunday Service");
        assertThat(updated.scripture()).isEqualTo("Psalm 150");

        UUID setlistId = UUID.randomUUID();
        UUID setlistVersionId = UUID.randomUUID();
        ServicePlanRecord attached = repository.attachSetlistVersion(created.servicePlanId(), setlistId, setlistVersionId);
        assertThat(attached.attachments()).hasSize(1);
        assertThat(attached.attachments().get(0).setlistId()).isEqualTo(setlistId);
        assertThat(attached.attachments().get(0).setlistVersionId()).isEqualTo(setlistVersionId);

        ServicePlanRecord published = repository.publish(created.servicePlanId(), "tester", "ready");
        assertThat(published.status().name()).isEqualTo("PUBLISHED");
        assertThat(published.publishedBy()).isEqualTo("tester");
        assertThat(published.publishedAt()).isNotNull();

        List<ServicePlanRecord> listed = repository.list();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).servicePlanId()).isEqualTo(created.servicePlanId());
    }
}
