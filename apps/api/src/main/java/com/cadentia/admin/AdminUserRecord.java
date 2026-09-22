package com.cadentia.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserRecord(
        UUID userId,
        String churchInstanceId,
        String externalSubject,
        String displayName,
        String email,
        AdminUserStatus status,
        List<String> roles,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public AdminUserRecord {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
