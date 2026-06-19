package com.cadentia.bot.telegram;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTelegramBotSessionRepository implements TelegramBotSessionRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTelegramBotSessionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TelegramBotSession> findActive(String channel, String chatHash, String userHash) {
        return jdbcTemplate.query("""
                SELECT * FROM telegram_bot_session
                WHERE channel = :channel AND chat_hash = :chatHash AND user_hash = :userHash
                  AND state NOT IN ('CANCELLED', 'COMPLETED', 'EXPIRED')
                ORDER BY updated_at DESC
                LIMIT 1
                """, Map.of("channel", channel, "chatHash", chatHash, "userHash", userHash), this::mapOptional);
    }

    @Override
    public TelegramBotSession save(TelegramBotSession session) {
        jdbcTemplate.update("""
                INSERT INTO telegram_bot_session (
                    id, channel, chat_hash, user_hash, church_instance_id, actor_id, state,
                    pending_confirmation_ref, last_update_id, last_message_id, created_at, updated_at,
                    inactivity_deadline, absolute_expiration, audit_metadata
                ) VALUES (
                    :id, :channel, :chatHash, :userHash, :churchInstanceId, :actorId, :state,
                    :pendingConfirmationRef, :lastUpdateId, :lastMessageId, :createdAt, :updatedAt,
                    :inactivityDeadline, :absoluteExpiration, CAST(:auditMetadata AS jsonb)
                )
                ON CONFLICT (id) DO UPDATE SET
                    state = EXCLUDED.state,
                    pending_confirmation_ref = EXCLUDED.pending_confirmation_ref,
                    last_update_id = EXCLUDED.last_update_id,
                    last_message_id = EXCLUDED.last_message_id,
                    updated_at = EXCLUDED.updated_at,
                    inactivity_deadline = EXCLUDED.inactivity_deadline,
                    absolute_expiration = EXCLUDED.absolute_expiration,
                    audit_metadata = EXCLUDED.audit_metadata
                """, params(session));
        return session;
    }

    @Override
    public void transition(UUID sessionId, TelegramSessionState state) {
        jdbcTemplate.update("UPDATE telegram_bot_session SET state = :state, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource().addValue("id", sessionId, Types.OTHER).addValue("state", state.name()));
    }

    private MapSqlParameterSource params(TelegramBotSession s) {
        return new MapSqlParameterSource()
                .addValue("id", s.id(), Types.OTHER)
                .addValue("channel", s.channel())
                .addValue("chatHash", s.chatHash())
                .addValue("userHash", s.userHash())
                .addValue("churchInstanceId", s.churchInstanceId())
                .addValue("actorId", s.actorId(), Types.OTHER)
                .addValue("state", s.state().name())
                .addValue("pendingConfirmationRef", s.pendingConfirmationRef())
                .addValue("lastUpdateId", s.lastUpdateId())
                .addValue("lastMessageId", s.lastMessageId())
                .addValue("createdAt", Timestamp.from(s.createdAt()))
                .addValue("updatedAt", Timestamp.from(s.updatedAt()))
                .addValue("inactivityDeadline", Timestamp.from(s.inactivityDeadline()))
                .addValue("absoluteExpiration", Timestamp.from(s.absoluteExpiration()))
                .addValue("auditMetadata", s.auditMetadataJson() == null ? "{}" : s.auditMetadataJson());
    }

    private Optional<TelegramBotSession> mapOptional(ResultSet rs) throws SQLException {
        return rs.next() ? Optional.of(map(rs)) : Optional.empty();
    }

    private TelegramBotSession map(ResultSet rs) throws SQLException {
        return new TelegramBotSession(
                rs.getObject("id", UUID.class), rs.getString("channel"), rs.getString("chat_hash"), rs.getString("user_hash"),
                rs.getString("church_instance_id"), rs.getObject("actor_id", UUID.class), TelegramSessionState.valueOf(rs.getString("state")),
                rs.getString("pending_confirmation_ref"), (Long) rs.getObject("last_update_id"), (Integer) rs.getObject("last_message_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("inactivity_deadline").toInstant(), rs.getTimestamp("absolute_expiration").toInstant(),
                rs.getString("audit_metadata"));
    }
}
