package com.cadentia.bot.telegram;

import com.cadentia.bot.telegram.TelegramOutboundModels.FailureCategory;
import com.cadentia.bot.telegram.TelegramOutboundModels.OutboundStatus;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramDeadLetterRecord;
import com.cadentia.bot.telegram.TelegramOutboundModels.TelegramOutboundSendRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTelegramOutboundRepository implements TelegramOutboundRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTelegramOutboundRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TelegramOutboundSendRecord createIfAbsent(TelegramOutboundSendRecord record) {
        jdbcTemplate.update("""
                INSERT INTO telegram_outbound_send_record (
                    id, idempotency_key, correlation_id, chat_hash, operation, sanitized_preview,
                    status, attempts, max_attempts, next_attempt_at, telegram_message_id,
                    failure_category, sanitized_failure_detail, created_at, updated_at
                ) VALUES (
                    :id, :idempotencyKey, :correlationId, :chatHash, :operation, :sanitizedPreview,
                    :status, :attempts, :maxAttempts, :nextAttemptAt, :telegramMessageId,
                    :failureCategory, :sanitizedFailureDetail, :createdAt, :updatedAt
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """, params(record));
        return findByIdempotencyKey(record.idempotencyKey()).orElseThrow();
    }

    @Override
    public Optional<TelegramOutboundSendRecord> findByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT * FROM telegram_outbound_send_record
                WHERE idempotency_key = :idempotencyKey
                """, Map.of("idempotencyKey", idempotencyKey), this::mapOptionalSendRecord);
    }

    @Override
    public TelegramOutboundSendRecord markSent(String idempotencyKey, String telegramMessageId, Instant now) {
        jdbcTemplate.update("""
                UPDATE telegram_outbound_send_record
                SET status = 'SENT', attempts = attempts + 1, next_attempt_at = NULL,
                    telegram_message_id = :telegramMessageId, failure_category = NULL,
                    sanitized_failure_detail = NULL, updated_at = :updatedAt
                WHERE idempotency_key = :idempotencyKey
                """, new MapSqlParameterSource()
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("telegramMessageId", telegramMessageId)
                .addValue("updatedAt", Timestamp.from(now)));
        return findByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    @Override
    public TelegramOutboundSendRecord markRetry(
            TelegramOutboundSendRecord record,
            String category,
            String sanitizedDetail,
            Instant retryAt,
            Instant now) {
        jdbcTemplate.update("""
                UPDATE telegram_outbound_send_record
                SET status = 'RETRY_SCHEDULED', attempts = attempts + 1, next_attempt_at = :retryAt,
                    telegram_message_id = NULL, failure_category = :failureCategory,
                    sanitized_failure_detail = :sanitizedFailureDetail, updated_at = :updatedAt
                WHERE idempotency_key = :idempotencyKey
                """, new MapSqlParameterSource()
                .addValue("idempotencyKey", record.idempotencyKey())
                .addValue("retryAt", Timestamp.from(retryAt))
                .addValue("failureCategory", category)
                .addValue("sanitizedFailureDetail", sanitizedDetail)
                .addValue("updatedAt", Timestamp.from(now)));
        return findByIdempotencyKey(record.idempotencyKey()).orElseThrow();
    }

    @Override
    public TelegramDeadLetterRecord deadLetter(
            TelegramOutboundSendRecord record,
            String category,
            String sanitizedDetail,
            Instant now) {
        TelegramOutboundSendRecord updated = markDeadLettered(record, category, sanitizedDetail, now);
        UUID deadLetterId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO telegram_outbound_dead_letter (
                    id, outbound_id, idempotency_key, correlation_id, chat_hash, operation,
                    failure_category, sanitized_failure_detail, sanitized_preview, attempts, created_at
                ) VALUES (
                    :id, :outboundId, :idempotencyKey, :correlationId, :chatHash, :operation,
                    :failureCategory, :sanitizedFailureDetail, :sanitizedPreview, :attempts, :createdAt
                )
                """, new MapSqlParameterSource()
                .addValue("id", deadLetterId, Types.OTHER)
                .addValue("outboundId", updated.id(), Types.OTHER)
                .addValue("idempotencyKey", updated.idempotencyKey())
                .addValue("correlationId", updated.correlationId())
                .addValue("chatHash", updated.chatHash())
                .addValue("operation", updated.operation())
                .addValue("failureCategory", category)
                .addValue("sanitizedFailureDetail", sanitizedDetail)
                .addValue("sanitizedPreview", updated.sanitizedPreview())
                .addValue("attempts", updated.attempts())
                .addValue("createdAt", Timestamp.from(now)));
        return new TelegramDeadLetterRecord(
                deadLetterId,
                updated.id(),
                updated.idempotencyKey(),
                updated.correlationId(),
                updated.chatHash(),
                updated.operation(),
                FailureCategory.valueOf(category),
                sanitizedDetail,
                updated.sanitizedPreview(),
                updated.attempts(),
                now);
    }

    @Override
    public List<TelegramDeadLetterRecord> deadLetters() {
        return jdbcTemplate.query("""
                SELECT * FROM telegram_outbound_dead_letter
                ORDER BY created_at DESC
                """, this::mapDeadLetterRecord);
    }

    private TelegramOutboundSendRecord markDeadLettered(
            TelegramOutboundSendRecord record,
            String category,
            String sanitizedDetail,
            Instant now) {
        jdbcTemplate.update("""
                UPDATE telegram_outbound_send_record
                SET status = 'DEAD_LETTERED', attempts = attempts + 1, next_attempt_at = NULL,
                    telegram_message_id = NULL, failure_category = :failureCategory,
                    sanitized_failure_detail = :sanitizedFailureDetail, updated_at = :updatedAt
                WHERE idempotency_key = :idempotencyKey
                """, new MapSqlParameterSource()
                .addValue("idempotencyKey", record.idempotencyKey())
                .addValue("failureCategory", category)
                .addValue("sanitizedFailureDetail", sanitizedDetail)
                .addValue("updatedAt", Timestamp.from(now)));
        return findByIdempotencyKey(record.idempotencyKey()).orElseThrow();
    }

    private MapSqlParameterSource params(TelegramOutboundSendRecord record) {
        return new MapSqlParameterSource()
                .addValue("id", record.id(), Types.OTHER)
                .addValue("idempotencyKey", record.idempotencyKey())
                .addValue("correlationId", record.correlationId())
                .addValue("chatHash", record.chatHash())
                .addValue("operation", record.operation())
                .addValue("sanitizedPreview", record.sanitizedPreview())
                .addValue("status", record.status().name())
                .addValue("attempts", record.attempts())
                .addValue("maxAttempts", record.maxAttempts())
                .addValue("nextAttemptAt", timestamp(record.nextAttemptAt()))
                .addValue("telegramMessageId", record.telegramMessageId())
                .addValue("failureCategory", enumName(record.failureCategory()))
                .addValue("sanitizedFailureDetail", record.sanitizedFailureDetail())
                .addValue("createdAt", Timestamp.from(record.createdAt()))
                .addValue("updatedAt", Timestamp.from(record.updatedAt()));
    }

    private Optional<TelegramOutboundSendRecord> mapOptionalSendRecord(ResultSet rs) throws SQLException {
        return rs.next() ? Optional.of(mapSendRecord(rs)) : Optional.empty();
    }

    private TelegramOutboundSendRecord mapSendRecord(ResultSet rs) throws SQLException {
        return new TelegramOutboundSendRecord(
                rs.getObject("id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("correlation_id"),
                rs.getString("chat_hash"),
                rs.getString("operation"),
                rs.getString("sanitized_preview"),
                OutboundStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                instant(rs, "next_attempt_at"),
                rs.getString("telegram_message_id"),
                enumValue(FailureCategory.class, rs.getString("failure_category")),
                rs.getString("sanitized_failure_detail"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private TelegramDeadLetterRecord mapDeadLetterRecord(ResultSet rs, int rowNum) throws SQLException {
        return new TelegramDeadLetterRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("outbound_id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("correlation_id"),
                rs.getString("chat_hash"),
                rs.getString("operation"),
                FailureCategory.valueOf(rs.getString("failure_category")),
                rs.getString("sanitized_failure_detail"),
                rs.getString("sanitized_preview"),
                rs.getInt("attempts"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
