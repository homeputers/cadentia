package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.bot.telegram.TelegramOutboundModels.FailureCategory;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
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
class JdbcTelegramOutboundRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTelegramOutboundRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcTelegramOutboundRepository(jdbcTemplate);
    }

    @Test
    void persistsIdempotentOutboundRecordAcrossRepositoryInstances() {
        // Arrange
        TelegramOutboundSendRecord record = record("idem-1", "safe proposal preview");
        JdbcTelegramOutboundRepository secondRepository = new JdbcTelegramOutboundRepository(jdbcTemplate);

        // Act
        TelegramOutboundSendRecord created = repository.createIfAbsent(record);
        TelegramOutboundSendRecord duplicate = secondRepository.createIfAbsent(record("idem-1", "different preview"));
        TelegramOutboundSendRecord sent = secondRepository.markSent("idem-1", "telegram-message-1", Instant.parse("2026-06-20T00:00:01Z"));

        // Assert
        assertThat(created.idempotencyKey()).isEqualTo("idem-1");
        assertThat(duplicate.sanitizedPreview()).isEqualTo("safe proposal preview");
        assertThat(sent.status()).isEqualTo(OutboundStatus.SENT);
        assertThat(sent.telegramMessageId()).isEqualTo("telegram-message-1");
    }

    @Test
    void storesDeadLetterInspectionMetadataWithoutRawChatIdentifiers() {
        // Arrange
        TelegramOutboundSendRecord created = repository.createIfAbsent(record("idem-2", "safe status preview"));

        // Act
        repository.deadLetter(created, FailureCategory.CHAT_BLOCKED.name(), "blocked by user", Instant.parse("2026-06-20T00:00:03Z"));

        // Assert
        assertThat(repository.deadLetters()).hasSize(1);
        assertThat(repository.deadLetters().get(0).chatHash()).isEqualTo("chat-hash");
        assertThat(repository.deadLetters().get(0).failureCategory()).isEqualTo(FailureCategory.CHAT_BLOCKED);
        assertThat(repository.deadLetters().get(0).sanitizedPreview()).isEqualTo("safe status preview");
    }

    @Test
    void databaseRejectsUnsanitizedOutboundInspectionFields() {
        // Arrange
        TelegramOutboundSendRecord unsafe = record("idem-unsafe", "token=secret");

        // Act / Assert
        assertThatThrownBy(() -> repository.createIfAbsent(unsafe))
                .hasMessageContaining("telegram_outbound_send_record_no_plaintext_secrets");
    }

    private static TelegramOutboundSendRecord record(String idempotencyKey, String sanitizedPreview) {
        Instant now = Instant.parse("2026-06-20T00:00:00Z");
        return new TelegramOutboundSendRecord(
                UUID.randomUUID(),
                idempotencyKey,
                "corr-1",
                "chat-hash",
                "proposal",
                sanitizedPreview,
                OutboundStatus.PENDING,
                0,
                4,
                now,
                null,
                null,
                null,
                now,
                now);
    }
}
