package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.controller.ConversationSessionRecord.ConversationSessionSourceStamp;
import com.cadentia.generated.model.ConversationRevisionEvent;
import com.cadentia.generated.model.ConversationState;
import com.cadentia.intent.Counts;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.intent.SlotValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcConversationSessionRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcConversationSessionRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcConversationSessionRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void saveAndFindByIdRoundTripsDurableConversationPayloadsAndSafeCorrelation() {
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-13T12:00:00Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-13T12:05:00Z");
        ConversationRevisionEvent revision = new ConversationRevisionEvent(
                UUID.randomUUID(),
                ConversationRevisionEvent.EventTypeEnum.SLOT_UPDATE,
                com.cadentia.generated.model.SlotValueSource.FREE_TEXT,
                updatedAt)
                .summary("Updated verseText from free_text.");
        ConversationSessionRecord record = new ConversationSessionRecord(
                sessionId,
                ConversationState.CONFIRMED,
                slots(),
                Map.of(
                        "verseText", new ConversationSessionSourceStamp(SlotValueSource.FREE_TEXT, updatedAt),
                        "counts", new ConversationSessionSourceStamp(SlotValueSource.DEFAULT, createdAt)),
                List.of(revision),
                createdAt,
                updatedAt,
                updatedAt,
                "telegram",
                "9103",
                "rec-result-123",
                Map.of(
                        "correlationId", "corr-bot-e2e",
                        "setlistId", "setlist-456",
                        "setlistVersionId", "version-789"));

        repository.save(record);
        ConversationSessionRecord found = repository.findById(sessionId).orElseThrow();

        assertThat(found.id()).isEqualTo(sessionId);
        assertThat(found.state()).isEqualTo(ConversationState.CONFIRMED);
        assertThat(found.slots().verseText()).isEqualTo("Psalm 100");
        assertThat(found.slotSources().get("verseText").source()).isEqualTo(SlotValueSource.FREE_TEXT);
        assertThat(found.revisionHistory())
                .extracting(event -> event.getEventType().getValue())
                .containsExactly("slot_update");
        assertThat(found.confirmedAt()).isEqualTo(updatedAt);
        assertThat(found.channel()).isEqualTo("telegram");
        assertThat(found.channelUpdateId()).isEqualTo("9103");
        assertThat(found.recommendationResultId()).isEqualTo("rec-result-123");
        assertThat(found.correlationMetadata())
                .containsEntry("correlationId", "corr-bot-e2e")
                .containsEntry("setlistId", "setlist-456")
                .containsEntry("setlistVersionId", "version-789");

        String rawStoredJson = jdbcTemplate.queryForObject(
                "SELECT correlation_metadata_json::text FROM conversation_sessions WHERE id = :id",
                new MapSqlParameterSource("id", sessionId),
                String.class);
        assertThat(rawStoredJson).doesNotContain("Psalm 100 thanksgiving", "42001", "99001");
    }

    private static GenerateSetlistSlots slots() {
        return new GenerateSetlistSlots(
                "Psalm 100",
                List.of("Psalm 100:1-5"),
                List.of("thanksgiving", "joy"),
                new Counts(2, 1),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                "en",
                "rising",
                List.of(),
                "opening");
    }
}
