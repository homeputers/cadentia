package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
class JdbcTelegramBotSessionRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTelegramBotSessionRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcTelegramBotSessionRepository(jdbcTemplate);
    }

    @Test
    void saveAndFindActiveRoundTripRestartSafeSessionFields() {
        // Arrange
        UUID id = UUID.randomUUID();
        TelegramBotSession session = session(id, TelegramSessionState.PENDING_CONFIRMATION, "proposal-123");

        // Act
        repository.save(session);
        TelegramBotSession found = repository.findActive("telegram", "chat-hash", "user-hash").orElseThrow();

        // Assert
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.churchInstanceId()).isEqualTo("church-a");
        assertThat(found.actorId()).isEqualTo(session.actorId());
        assertThat(found.state()).isEqualTo(TelegramSessionState.PENDING_CONFIRMATION);
        assertThat(found.pendingConfirmationRef()).isEqualTo("proposal-123");
        assertThat(found.lastUpdateId()).isEqualTo(501L);
        assertThat(found.lastMessageId()).isEqualTo(77);
        assertThat(found.auditMetadataJson()).contains("telegram").doesNotContain("42", "99", "message text");
    }

    @Test
    void saveUpdatesExistingSessionAndTransitionRemovesItFromActiveLookup() {
        // Arrange
        UUID id = UUID.randomUUID();
        TelegramBotSession initial = session(id, TelegramSessionState.NEW_SETLIST_ACTIVE, null);
        TelegramBotSession pending = new TelegramBotSession(
                initial.id(), initial.channel(), initial.chatHash(), initial.userHash(), initial.churchInstanceId(), initial.actorId(),
                TelegramSessionState.PENDING_CONFIRMATION, "proposal-456", 502L, 78, initial.createdAt(),
                Instant.parse("2026-06-19T12:05:00Z"), Instant.parse("2026-06-19T12:35:00Z"), initial.absoluteExpiration(),
                "{\"source\":\"telegram\",\"action\":\"resume\"}");

        // Act
        repository.save(initial);
        repository.save(pending);
        TelegramBotSession updated = repository.findActive("telegram", "chat-hash", "user-hash").orElseThrow();
        repository.transition(id, TelegramSessionState.CANCELLED);

        // Assert
        assertThat(updated.state()).isEqualTo(TelegramSessionState.PENDING_CONFIRMATION);
        assertThat(updated.pendingConfirmationRef()).isEqualTo("proposal-456");
        assertThat(repository.findActive("telegram", "chat-hash", "user-hash")).isEmpty();
    }

    private TelegramBotSession session(UUID id, TelegramSessionState state, String pendingConfirmationRef) {
        return new TelegramBotSession(
                id,
                "telegram",
                "chat-hash",
                "user-hash",
                "church-a",
                UUID.randomUUID(),
                state,
                pendingConfirmationRef,
                501L,
                77,
                Instant.parse("2026-06-19T12:00:00Z"),
                Instant.parse("2026-06-19T12:00:00Z"),
                Instant.parse("2026-06-19T12:30:00Z"),
                Instant.parse("2026-06-19T16:00:00Z"),
                "{\"source\":\"telegram\"}");
    }
}
