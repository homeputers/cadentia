package com.cadentia.auth.repository;

import com.cadentia.auth.domain.AuthSession;
import com.cadentia.auth.domain.AuthUser;
import com.cadentia.auth.domain.PasswordResetToken;
import com.cadentia.auth.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuthRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthUser> findUserByEmail(String email) {
        return queryOne("SELECT * FROM auth_users WHERE email = :email", params("email", email), this::mapUser);
    }

    public Optional<AuthUser> findUserById(UUID userId) {
        return queryOne("SELECT * FROM auth_users WHERE user_id = :userId", params("userId", userId), this::mapUser);
    }

    @Transactional
    public AuthUser createUser(UUID userId, String email, String displayName, String passwordHash) {
        jdbcTemplate.update("""
                INSERT INTO auth_users (user_id, email, display_name, password_hash)
                VALUES (:userId, :email, :displayName, :passwordHash)
                """, params("userId", userId)
                .addValue("email", email)
                .addValue("displayName", displayName)
                .addValue("passwordHash", passwordHash));
        return findUserById(userId).orElseThrow();
    }

    public void recordFailedLogin(UUID userId, int failedAttempts, Instant lockedUntil) {
        jdbcTemplate.update("""
                UPDATE auth_users
                SET failed_login_attempts = :failedAttempts, locked_until = :lockedUntil, updated_at = now()
                WHERE user_id = :userId
                """, params("userId", userId)
                .addValue("failedAttempts", failedAttempts)
                .addValue("lockedUntil", lockedUntil == null ? null : Timestamp.from(lockedUntil)));
    }

    public void recordSuccessfulLogin(UUID userId) {
        jdbcTemplate.update("""
                UPDATE auth_users
                SET failed_login_attempts = 0, locked_until = NULL, updated_at = now()
                WHERE user_id = :userId
                """, params("userId", userId));
    }

    public void updatePassword(UUID userId, String passwordHash) {
        jdbcTemplate.update("""
                UPDATE auth_users
                SET password_hash = :passwordHash, password_changed_at = now(), updated_at = now()
                WHERE user_id = :userId
                """, params("userId", userId).addValue("passwordHash", passwordHash));
    }

    @Transactional
    public void createSession(
            UUID sessionId,
            UUID userId,
            String refreshTokenHash,
            Instant expiresAt,
            String userAgent,
            String ipAddress) {
        jdbcTemplate.update("""
                INSERT INTO auth_sessions (session_id, user_id, refresh_token_hash, expires_at, user_agent, ip_address)
                VALUES (:sessionId, :userId, :refreshTokenHash, :expiresAt, :userAgent, CAST(:ipAddress AS inet))
                """, params("sessionId", sessionId)
                .addValue("userId", userId)
                .addValue("refreshTokenHash", refreshTokenHash)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("userAgent", userAgent)
                .addValue("ipAddress", ipAddress));
    }

    public Optional<AuthSession> findActiveSessionByRefreshHash(String refreshTokenHash) {
        return queryOne("""
                SELECT * FROM auth_sessions
                WHERE refresh_token_hash = :refreshTokenHash
                  AND revoked_at IS NULL
                  AND expires_at > now()
                FOR UPDATE
                """, params("refreshTokenHash", refreshTokenHash), this::mapSession);
    }

    public void revokeSession(UUID sessionId) {
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = now() WHERE session_id = :sessionId AND revoked_at IS NULL",
                params("sessionId", sessionId));
    }

    public void revokeAllSessions(UUID userId) {
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = now() WHERE user_id = :userId AND revoked_at IS NULL",
                params("userId", userId));
    }

    public void markSessionUsed(UUID sessionId) {
        jdbcTemplate.update("UPDATE auth_sessions SET last_used_at = now() WHERE session_id = :sessionId",
                params("sessionId", sessionId));
    }

    public void createPasswordResetToken(UUID tokenId, UUID userId, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_password_reset_tokens (token_id, user_id, token_hash, expires_at)
                VALUES (:tokenId, :userId, :tokenHash, :expiresAt)
                """, params("tokenId", tokenId)
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("expiresAt", Timestamp.from(expiresAt)));
    }

    public Optional<PasswordResetToken> findActivePasswordResetToken(String tokenHash) {
        return queryOne("""
                SELECT * FROM auth_password_reset_tokens
                WHERE token_hash = :tokenHash AND used_at IS NULL AND expires_at > now()
                FOR UPDATE
                """, params("tokenHash", tokenHash), this::mapPasswordResetToken);
    }

    public void markPasswordResetTokenUsed(UUID tokenId) {
        jdbcTemplate.update("UPDATE auth_password_reset_tokens SET used_at = now() WHERE token_id = :tokenId",
                params("tokenId", tokenId));
    }

    private <T> Optional<T> queryOne(String sql, MapSqlParameterSource parameters, RowMapper<T> mapper) {
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNum) -> mapper.map(resultSet)).stream().findFirst();
    }

    private AuthUser mapUser(ResultSet resultSet) throws SQLException {
        return new AuthUser(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getString("password_hash"),
                UserStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("failed_login_attempts"),
                instant(resultSet, "locked_until"),
                instant(resultSet, "password_changed_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private AuthSession mapSession(ResultSet resultSet) throws SQLException {
        return new AuthSession(
                resultSet.getObject("session_id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("refresh_token_hash"),
                instant(resultSet, "expires_at"),
                instant(resultSet, "revoked_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "last_used_at"),
                resultSet.getString("user_agent"),
                resultSet.getString("ip_address"));
    }

    private PasswordResetToken mapPasswordResetToken(ResultSet resultSet) throws SQLException {
        return new PasswordResetToken(
                resultSet.getObject("token_id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("token_hash"),
                instant(resultSet, "expires_at"),
                instant(resultSet, "used_at"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static MapSqlParameterSource params(String name, Object value) {
        return new MapSqlParameterSource(name, value);
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
