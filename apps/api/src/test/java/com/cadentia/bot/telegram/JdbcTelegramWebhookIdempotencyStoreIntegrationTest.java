package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.bot.telegram.TelegramWebhookIdempotencyStore.IdempotencyResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcTelegramWebhookIdempotencyStoreIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTelegramWebhookIdempotencyStore store;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        store = new JdbcTelegramWebhookIdempotencyStore(jdbcTemplate);
    }

    @Test
    void recordPersistsAcceptedUpdateAndReturnsDuplicateForSameBotChannelUpdate() {
        IdempotencyResult first = store.record("bot-a", "42", 1001L);
        IdempotencyResult duplicate = store.record("bot-a", "42", 1001L);
        IdempotencyResult differentChannel = store.record("bot-a", "43", 1001L);

        assertThat(first).isEqualTo(IdempotencyResult.ACCEPTED);
        assertThat(duplicate).isEqualTo(IdempotencyResult.DUPLICATE_ACCEPTED);
        assertThat(differentChannel).isEqualTo(IdempotencyResult.ACCEPTED);
        Integer count = jdbcTemplate.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM telegram_webhook_update_acceptance", Integer.class);
        assertThat(count).isEqualTo(2);
    }
}
