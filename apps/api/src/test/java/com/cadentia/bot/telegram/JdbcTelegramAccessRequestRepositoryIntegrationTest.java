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
class JdbcTelegramAccessRequestRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTelegramAccessRequestRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcTelegramAccessRequestRepository(jdbcTemplate);
    }

    @Test
    void savedPendingRequestIsFoundByHashesAndInstance() {
        // Arrange
        TelegramAccessRequest request = request("chat-hash", "user-hash", "church-a");

        // Act
        repository.save(request);

        // Assert
        TelegramAccessRequest found = repository
                .findPending("telegram", "chat-hash", "user-hash", "church-a")
                .orElseThrow();
        assertThat(found.id()).isEqualTo(request.id());
        assertThat(found.chatId()).isEqualTo("4242");
        assertThat(found.status()).isEqualTo(TelegramAccessRequestStatus.PENDING);
        assertThat(repository.findPending("telegram", "chat-hash", "user-hash", "church-b")).isEmpty();
    }

    @Test
    void decidePurgesRawChatIdAndBlocksSecondDecision() {
        // Arrange
        TelegramAccessRequest request = repository.save(request("chat-hash", "user-hash", "church-a"));

        // Act
        TelegramAccessRequest decided = repository
                .decide(request.id(), TelegramAccessRequestStatus.APPROVED, "admin-1", "verified", Instant.now())
                .orElseThrow();

        // Assert
        assertThat(decided.status()).isEqualTo(TelegramAccessRequestStatus.APPROVED);
        assertThat(decided.chatId()).isNull();
        assertThat(decided.decidedBy()).isEqualTo("admin-1");
        assertThat(decided.decisionReason()).isEqualTo("verified");
        assertThat(decided.decidedAt()).isNotNull();
        assertThat(repository.findPending("telegram", "chat-hash", "user-hash", "church-a")).isEmpty();
        assertThat(repository.decide(request.id(), TelegramAccessRequestStatus.REJECTED, "admin-2", null, Instant.now()))
                .isEmpty();
    }

    @Test
    void newRequestIsAllowedAfterPreviousRequestWasDecided() {
        // Arrange
        TelegramAccessRequest first = repository.save(request("chat-hash", "user-hash", "church-a"));
        repository.decide(first.id(), TelegramAccessRequestStatus.REJECTED, "admin-1", null, Instant.now());

        // Act
        TelegramAccessRequest second = repository.save(request("chat-hash", "user-hash", "church-a"));

        // Assert
        assertThat(repository.findPending("telegram", "chat-hash", "user-hash", "church-a"))
                .map(TelegramAccessRequest::id)
                .contains(second.id());
    }

    @Test
    void findByInstanceAndStatusReturnsQueueEntriesWithoutOtherStatuses() {
        // Arrange
        TelegramAccessRequest pending = repository.save(request("chat-hash", "user-hash", "church-a"));
        TelegramAccessRequest approved = repository.save(request("chat-hash-2", "user-hash-2", "church-a"));
        repository.decide(approved.id(), TelegramAccessRequestStatus.APPROVED, "admin-1", null, Instant.now());

        // Act / Assert
        assertThat(repository.findByInstanceAndStatus("church-a", TelegramAccessRequestStatus.PENDING))
                .extracting(TelegramAccessRequest::id)
                .containsExactly(pending.id());
        assertThat(repository.findByInstanceAndStatus("church-a", TelegramAccessRequestStatus.APPROVED))
                .extracting(TelegramAccessRequest::id)
                .containsExactly(approved.id());
        assertThat(repository.findByInstanceAndStatus("church-b", TelegramAccessRequestStatus.PENDING)).isEmpty();
    }

    private TelegramAccessRequest request(String chatHash, String userHash, String churchInstanceId) {
        return new TelegramAccessRequest(
                UUID.randomUUID(), "telegram", chatHash, userHash, "4242", churchInstanceId,
                TelegramAccessRequestStatus.PENDING, Instant.now(), null, null, null);
    }
}
