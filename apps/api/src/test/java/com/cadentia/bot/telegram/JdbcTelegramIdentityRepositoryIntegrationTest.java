package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
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
class JdbcTelegramIdentityRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTelegramIdentityRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new JdbcTelegramIdentityRepository(jdbcTemplate);
    }

    @Test
    void findByTelegramHashesReturnsLinkedActorWithoutRawTelegramIdentifiers() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        insertLink("telegram", "chat-hash", "user-hash", "church-a", actorId, "LINKED");

        // Act
        TelegramLinkedActor actor = repository.findByTelegramHashes("telegram", "chat-hash", "user-hash").orElseThrow();

        // Assert
        assertThat(actor.actorId()).isEqualTo(actorId);
        assertThat(actor.churchInstanceId()).isEqualTo("church-a");
        assertThat(actor.roles()).containsExactlyInAnyOrderElementsOf(Set.of("ROLE_WORSHIP_LEADER", "ROLE_TEAM_SCHEDULER"));
        assertThat(actor.status()).isEqualTo(TelegramIdentityStatus.LINKED);
        Integer rawIdentifierCount = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM telegram_account_link WHERE chat_hash IN ('42', '99') OR user_hash IN ('42', '99')", Integer.class);
        assertThat(rawIdentifierCount).isZero();
    }

    @Test
    void findByTelegramHashesReturnsEmptyForUnknownHashPair() {
        // Arrange
        insertLink("telegram", "chat-hash", "user-hash", "church-a", UUID.randomUUID(), "REVOKED");

        // Act / Assert
        assertThat(repository.findByTelegramHashes("telegram", "missing-chat", "missing-user")).isEmpty();
    }

    @Test
    void saveLinkCreatesLinkedIdentityAndReactivatesOnConflict() {
        // Act: first save creates the link
        TelegramLinkedActor created = repository.saveLink("telegram", "chat-hash", "user-hash", "church-a", Set.of("ROLE_WORSHIP_LEADER"));

        // Assert
        assertThat(created.status()).isEqualTo(TelegramIdentityStatus.LINKED);
        assertThat(created.roles()).containsExactly("ROLE_WORSHIP_LEADER");
        assertThat(repository.findByTelegramHashes("telegram", "chat-hash", "user-hash"))
                .map(TelegramLinkedActor::actorId)
                .contains(created.actorId());

        // Act: a second save for a revoked link re-activates it with the same actor id
        jdbcTemplate.getJdbcTemplate().update(
                "UPDATE telegram_account_link SET status = 'REVOKED', revoked_at = now() WHERE chat_hash = 'chat-hash'");
        TelegramLinkedActor relinked = repository.saveLink("telegram", "chat-hash", "user-hash", "church-a", Set.of("ROLE_WORSHIP_LEADER"));

        // Assert
        assertThat(relinked.actorId()).isEqualTo(created.actorId());
        assertThat(repository.findByTelegramHashes("telegram", "chat-hash", "user-hash"))
                .map(TelegramLinkedActor::status)
                .contains(TelegramIdentityStatus.LINKED);
    }

    private void insertLink(String channel, String chatHash, String userHash, String churchInstanceId, UUID actorId, String status) {
        jdbcTemplate.update("""
                INSERT INTO telegram_account_link (
                    id, channel, chat_hash, user_hash, church_instance_id, actor_id, roles, status, audit_metadata
                ) VALUES (
                    :id, :channel, :chatHash, :userHash, :churchInstanceId, :actorId,
                    ARRAY['ROLE_WORSHIP_LEADER', 'ROLE_TEAM_SCHEDULER'], :status, '{}'::jsonb
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("channel", channel)
                .addValue("chatHash", chatHash)
                .addValue("userHash", userHash)
                .addValue("churchInstanceId", churchInstanceId)
                .addValue("actorId", actorId)
                .addValue("status", status));
    }
}
