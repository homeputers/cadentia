package com.cadentia.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.feedback.FeedbackModels.FeedbackEventRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class JdbcFeedbackRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcFeedbackRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new JdbcFeedbackRepository(new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
    }

    @Test
    void createEventListEventsAndAggregateStateExecuteQueriesDeterministically() {
        UUID setlistId = UUID.randomUUID();
        UUID setlistVersionId = UUID.randomUUID();
        UUID arrangementId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();

        FeedbackEventRecord rejected = repository.createEvent(new FeedbackEventRecord(
                null,
                setlistId,
                setlistVersionId,
                arrangementId,
                "rejected",
                "team",
                scopeId,
                "planner-1",
                "key_mismatch",
                null,
                70,
                Instant.parse("2026-05-27T00:00:00Z")));

        FeedbackEventRecord favorited = repository.createEvent(new FeedbackEventRecord(
                null,
                setlistId,
                setlistVersionId,
                arrangementId,
                "favorited",
                "team",
                scopeId,
                "planner-2",
                null,
                null,
                null,
                Instant.parse("2026-05-27T00:01:00Z")));

        List<FeedbackEventRecord> listed = repository.listEvents("team", scopeId, arrangementId);
        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).feedbackEventId()).isEqualTo(favorited.feedbackEventId());
        assertThat(listed.get(1).feedbackEventId()).isEqualTo(rejected.feedbackEventId());

        FeedbackModels.FeedbackScopeAggregate aggregate = repository.getScopeAggregate("team", scopeId).orElseThrow();
        assertThat(aggregate.acceptedCount()).isZero();
        assertThat(aggregate.rejectedCount()).isEqualTo(1);
        assertThat(aggregate.skippedCount()).isZero();
        assertThat(aggregate.favoritedCount()).isEqualTo(1);
        assertThat(aggregate.replacementReasonCounts()).isEqualTo(Map.of("key_mismatch", 1));
        assertThat(aggregate.lastFeedbackAt()).isNotNull();
    }

    @Test
    void resetScopeRemovesAggregateRow() {
        UUID scopeId = UUID.randomUUID();

        repository.createEvent(new FeedbackEventRecord(
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "accepted",
                "personal",
                scopeId,
                "planner-3",
                null,
                null,
                null,
                Instant.parse("2026-05-27T00:02:00Z")));

        assertThat(repository.getScopeAggregate("personal", scopeId)).isPresent();

        repository.resetScope("personal", scopeId, "admin-1");

        assertThat(repository.getScopeAggregate("personal", scopeId)).isEmpty();
    }
}
