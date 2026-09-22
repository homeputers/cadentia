package com.cadentia.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository {

    List<AdminUserRecord> findAll(String churchInstanceId, AdminUserStatus status, String search);

    Optional<AdminUserRecord> findById(String churchInstanceId, UUID userId);

    Optional<AdminUserRecord> findByExternalSubject(String churchInstanceId, String externalSubject);

    AdminUserRecord create(
            String churchInstanceId,
            String externalSubject,
            String displayName,
            String email,
            List<String> roles);

    AdminUserRecord update(
            String churchInstanceId,
            UUID userId,
            String displayName,
            String email,
            AdminUserStatus status,
            long expectedVersion);

    AdminUserRecord replaceRoles(String churchInstanceId, UUID userId, List<String> roles, long expectedVersion);

    long countActiveUsersWithRole(String churchInstanceId, String roleCode);
}
