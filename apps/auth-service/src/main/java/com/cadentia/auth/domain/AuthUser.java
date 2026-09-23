package com.cadentia.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthUser(
        UUID userId,
        String email,
        String displayName,
        String passwordHash,
        UserStatus status,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt) {
}
