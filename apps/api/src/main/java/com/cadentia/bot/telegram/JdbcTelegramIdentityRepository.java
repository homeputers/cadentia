package com.cadentia.bot.telegram;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTelegramIdentityRepository implements TelegramIdentityRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTelegramIdentityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TelegramLinkedActor> findByTelegramHashes(String channel, String chatHash, String userHash) {
        return jdbcTemplate.query("""
                SELECT actor_id, church_instance_id, roles, status
                FROM telegram_account_link
                WHERE channel = :channel AND chat_hash = :chatHash AND user_hash = :userHash
                ORDER BY updated_at DESC
                LIMIT 1
                """, Map.of("channel", channel, "chatHash", chatHash, "userHash", userHash), this::mapOptional);
    }

    @Override
    public TelegramLinkedActor saveLink(String channel, String chatHash, String userHash, String churchInstanceId, Set<String> roles) {
        UUID actorId = jdbcTemplate.query("""
                INSERT INTO telegram_account_link (
                    id, channel, chat_hash, user_hash, church_instance_id, actor_id, roles, status, link_confirmed_at, audit_metadata
                ) VALUES (
                    gen_random_uuid(), :channel, :chatHash, :userHash, :churchInstanceId, gen_random_uuid(),
                    string_to_array(:roles, ','), 'LINKED', now(), CAST(:auditMetadata AS jsonb)
                )
                ON CONFLICT (channel, chat_hash, user_hash, church_instance_id)
                DO UPDATE SET roles = EXCLUDED.roles, status = 'LINKED', revoked_at = NULL,
                              link_confirmed_at = now(), updated_at = now()
                RETURNING actor_id
                """, Map.of("channel", channel, "chatHash", chatHash, "userHash", userHash,
                "churchInstanceId", churchInstanceId, "roles", String.join(",", roles),
                "auditMetadata", "{\"source\":\"access_request\"}"),
                rs -> rs.next() ? rs.getObject("actor_id", UUID.class) : null);
        if (actorId == null) {
            throw new IllegalStateException("Telegram account link upsert returned no actor id.");
        }
        return new TelegramLinkedActor(actorId, churchInstanceId, roles, TelegramIdentityStatus.LINKED);
    }

    private Optional<TelegramLinkedActor> mapOptional(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }
        String[] roles = (String[]) rs.getArray("roles").getArray();
        Set<String> roleSet = Arrays.stream(roles).collect(Collectors.toUnmodifiableSet());
        return Optional.of(new TelegramLinkedActor(
                rs.getObject("actor_id", UUID.class),
                rs.getString("church_instance_id"),
                roleSet,
                TelegramIdentityStatus.valueOf(rs.getString("status"))));
    }
}
