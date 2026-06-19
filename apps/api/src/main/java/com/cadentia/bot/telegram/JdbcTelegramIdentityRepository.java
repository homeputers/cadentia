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
