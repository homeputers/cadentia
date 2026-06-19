package com.cadentia.bot.telegram;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTelegramWebhookIdempotencyStore implements TelegramWebhookIdempotencyStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public JdbcTelegramWebhookIdempotencyStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    JdbcTelegramWebhookIdempotencyStore(NamedParameterJdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public IdempotencyResult record(String botId, String channelId, long updateId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO telegram_webhook_update_acceptance (
                        bot_id, channel_id, update_id, accepted_at
                    ) VALUES (
                        :botId, :channelId, :updateId, :acceptedAt
                    )
                    """, new MapSqlParameterSource()
                    .addValue("botId", botId)
                    .addValue("channelId", channelId)
                    .addValue("updateId", updateId)
                    .addValue("acceptedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
            return IdempotencyResult.ACCEPTED;
        } catch (DuplicateKeyException ex) {
            return IdempotencyResult.DUPLICATE_ACCEPTED;
        }
    }
}
