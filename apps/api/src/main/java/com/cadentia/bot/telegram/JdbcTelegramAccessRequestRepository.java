package com.cadentia.bot.telegram;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTelegramAccessRequestRepository implements TelegramAccessRequestRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTelegramAccessRequestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TelegramAccessRequest save(TelegramAccessRequest request) {
        jdbcTemplate.update("""
                INSERT INTO telegram_access_request (
                    id, channel, chat_hash, user_hash, chat_id, church_instance_id, status,
                    requested_at, audit_metadata
                ) VALUES (
                    :id, :channel, :chatHash, :userHash, :chatId, :churchInstanceId, :status,
                    :requestedAt, CAST(:auditMetadata AS jsonb)
                )
                """, new MapSqlParameterSource()
                .addValue("id", request.id())
                .addValue("channel", request.channel())
                .addValue("chatHash", request.chatHash())
                .addValue("userHash", request.userHash())
                .addValue("chatId", request.chatId())
                .addValue("churchInstanceId", request.churchInstanceId())
                .addValue("status", request.status().name())
                .addValue("requestedAt", Timestamp.from(request.requestedAt()))
                .addValue("auditMetadata", "{\"source\":\"telegram_self_service\"}"));
        return request;
    }

    @Override
    public Optional<TelegramAccessRequest> findPending(String channel, String chatHash, String userHash, String churchInstanceId) {
        return jdbcTemplate.query("""
                SELECT id, channel, chat_hash, user_hash, chat_id, church_instance_id, status,
                       requested_at, decided_at, decided_by, decision_reason
                FROM telegram_access_request
                WHERE channel = :channel AND chat_hash = :chatHash AND user_hash = :userHash
                      AND church_instance_id = :churchInstanceId AND status = 'PENDING'
                ORDER BY requested_at DESC
                LIMIT 1
                """, Map.of("channel", channel, "chatHash", chatHash, "userHash", userHash,
                "churchInstanceId", churchInstanceId), this::mapOptional);
    }

    @Override
    public Optional<TelegramAccessRequest> findById(UUID id) {
        return jdbcTemplate.query("""
                SELECT id, channel, chat_hash, user_hash, chat_id, church_instance_id, status,
                       requested_at, decided_at, decided_by, decision_reason
                FROM telegram_access_request
                WHERE id = :id
                """, Map.of("id", id), this::mapOptional);
    }

    @Override
    public List<TelegramAccessRequest> findByInstanceAndStatus(String churchInstanceId, TelegramAccessRequestStatus status) {
        return jdbcTemplate.query("""
                SELECT id, channel, chat_hash, user_hash, chat_id, church_instance_id, status,
                       requested_at, decided_at, decided_by, decision_reason
                FROM telegram_access_request
                WHERE church_instance_id = :churchInstanceId AND status = :status
                ORDER BY requested_at DESC
                """, Map.of("churchInstanceId", churchInstanceId, "status", status.name()), this::mapRow);
    }

    @Override
    public Optional<TelegramAccessRequest> decide(
            UUID id, TelegramAccessRequestStatus decision, String decidedBy, String decisionReason, Instant decidedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE telegram_access_request
                SET status = :status,
                    decided_at = :decidedAt,
                    decided_by = :decidedBy,
                    decision_reason = :decisionReason,
                    chat_id = NULL,
                    updated_at = :decidedAt
                WHERE id = :id AND status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", decision.name())
                .addValue("decidedAt", Timestamp.from(decidedAt))
                .addValue("decidedBy", decidedBy)
                .addValue("decisionReason", decisionReason));
        return updated == 1 ? findById(id) : Optional.empty();
    }

    private Optional<TelegramAccessRequest> mapOptional(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }
        return Optional.of(mapRow(rs, 0));
    }

    private TelegramAccessRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TelegramAccessRequest(
                rs.getObject("id", UUID.class),
                rs.getString("channel"),
                rs.getString("chat_hash"),
                rs.getString("user_hash"),
                rs.getString("chat_id"),
                rs.getString("church_instance_id"),
                TelegramAccessRequestStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("requested_at").toInstant(),
                Optional.ofNullable(rs.getTimestamp("decided_at")).map(ts -> ts.toInstant()).orElse(null),
                rs.getString("decided_by"),
                rs.getString("decision_reason"));
    }
}
