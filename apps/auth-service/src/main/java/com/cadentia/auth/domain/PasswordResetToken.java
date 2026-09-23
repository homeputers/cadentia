package com.cadentia.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetToken(
        UUID tokenId,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt) {

    public boolean isActive(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
