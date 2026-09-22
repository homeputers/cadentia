package com.cadentia.admin;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAdminUserRepository implements AdminUserRepository {

    private static final String SELECT = """
            SELECT u.user_id, u.church_instance_id, u.external_subject, u.display_name, u.email,
                   u.status, u.version, u.created_at, u.updated_at,
                   COALESCE(array_agg(r.role_code ORDER BY r.role_code) FILTER (WHERE r.role_code IS NOT NULL), ARRAY[]::text[]) AS roles
            FROM admin_users u
            LEFT JOIN admin_user_roles r ON r.user_id = u.user_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAdminUserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AdminUserRecord> findAll(String churchInstanceId, AdminUserStatus status, String search) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE u.church_instance_id = :churchInstanceId ");
        MapSqlParameterSource parameters = new MapSqlParameterSource("churchInstanceId", churchInstanceId);
        if (status != null) {
            sql.append(" AND u.status = :status ");
            parameters.addValue("status", status.name());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (u.display_name ILIKE :search OR u.external_subject ILIKE :search OR u.email ILIKE :search) ");
            parameters.addValue("search", "%" + search.trim() + "%");
        }
        sql.append(" GROUP BY u.user_id ORDER BY u.display_name, u.user_id");
        return jdbcTemplate.query(sql.toString(), parameters, this::mapUser);
    }

    @Override
    public Optional<AdminUserRecord> findById(String churchInstanceId, UUID userId) {
        return queryOne(SELECT + " WHERE u.church_instance_id = :churchInstanceId AND u.user_id = :userId GROUP BY u.user_id",
                new MapSqlParameterSource("churchInstanceId", churchInstanceId).addValue("userId", userId));
    }

    @Override
    public Optional<AdminUserRecord> findByExternalSubject(String churchInstanceId, String externalSubject) {
        return queryOne(SELECT + " WHERE u.church_instance_id = :churchInstanceId AND u.external_subject = :externalSubject GROUP BY u.user_id",
                new MapSqlParameterSource("churchInstanceId", churchInstanceId).addValue("externalSubject", externalSubject));
    }

    @Override
    @Transactional
    public AdminUserRecord create(
            String churchInstanceId,
            String externalSubject,
            String displayName,
            String email,
            List<String> roles) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO admin_users (user_id, church_instance_id, external_subject, display_name, email)
                VALUES (:userId, :churchInstanceId, :externalSubject, :displayName, :email)
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("churchInstanceId", churchInstanceId)
                .addValue("externalSubject", externalSubject)
                .addValue("displayName", displayName)
                .addValue("email", email));
        insertRoles(userId, roles);
        return findById(churchInstanceId, userId).orElseThrow();
    }

    @Override
    @Transactional
    public AdminUserRecord update(
            String churchInstanceId,
            UUID userId,
            String displayName,
            String email,
            AdminUserStatus status,
            long expectedVersion) {
        int updated = jdbcTemplate.update("""
                UPDATE admin_users
                SET display_name = :displayName, email = :email, status = :status,
                    version = version + 1, updated_at = now()
                WHERE church_instance_id = :churchInstanceId AND user_id = :userId AND version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("churchInstanceId", churchInstanceId)
                .addValue("userId", userId)
                .addValue("displayName", displayName)
                .addValue("email", email)
                .addValue("status", status.name())
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Admin user version has changed or user does not exist.");
        }
        return findById(churchInstanceId, userId).orElseThrow();
    }

    @Override
    @Transactional
    public AdminUserRecord replaceRoles(String churchInstanceId, UUID userId, List<String> roles, long expectedVersion) {
        int updated = jdbcTemplate.update("""
                UPDATE admin_users SET version = version + 1, updated_at = now()
                WHERE church_instance_id = :churchInstanceId AND user_id = :userId AND version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("churchInstanceId", churchInstanceId)
                .addValue("userId", userId)
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new OptimisticLockingFailureException("Admin user version has changed or user does not exist.");
        }
        jdbcTemplate.update("DELETE FROM admin_user_roles WHERE user_id = :userId", new MapSqlParameterSource("userId", userId));
        insertRoles(userId, roles);
        return findById(churchInstanceId, userId).orElseThrow();
    }

    @Override
    public long countActiveUsersWithRole(String churchInstanceId, String roleCode) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM admin_users u
                JOIN admin_user_roles r ON r.user_id = u.user_id
                WHERE u.church_instance_id = :churchInstanceId AND u.status = 'ACTIVE' AND r.role_code = :roleCode
                """, new MapSqlParameterSource().addValue("churchInstanceId", churchInstanceId).addValue("roleCode", roleCode), Long.class);
        return count == null ? 0 : count;
    }

    private void insertRoles(UUID userId, List<String> roles) {
        jdbcTemplate.batchUpdate("INSERT INTO admin_user_roles (user_id, role_code) VALUES (:userId, :roleCode)",
                roles.stream().map(role -> new MapSqlParameterSource().addValue("userId", userId).addValue("roleCode", role)).toArray(MapSqlParameterSource[]::new));
    }

    private Optional<AdminUserRecord> queryOne(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate.query(sql, parameters, this::mapUser).stream().findFirst();
    }

    private AdminUserRecord mapUser(ResultSet resultSet, int rowNum) throws SQLException {
        Array sqlRoles = resultSet.getArray("roles");
        String[] roleArray = sqlRoles == null ? new String[0] : (String[]) sqlRoles.getArray();
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new AdminUserRecord(
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("church_instance_id"),
                resultSet.getString("external_subject"),
                resultSet.getString("display_name"),
                resultSet.getString("email"),
                AdminUserStatus.valueOf(resultSet.getString("status")),
                List.of(roleArray),
                resultSet.getLong("version"),
                createdAt.toInstant(),
                updatedAt.toInstant());
    }
}
