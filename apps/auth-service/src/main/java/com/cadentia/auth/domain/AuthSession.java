package com.cadentia.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(
        UUID sessionId,
        UUID userId,
        String refreshTokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant lastUsedAt,
        String userAgent,
        String ipAddress) {

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
