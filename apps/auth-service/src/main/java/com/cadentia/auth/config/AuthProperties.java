package com.cadentia.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cadentia.auth")
public record AuthProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        int maxFailedLoginAttempts,
        Duration lockDuration,
        boolean allowRegistration,
        boolean logPasswordResetTokens,
        String privateKeyBase64,
        String publicKeyBase64,
        String allowedOrigins,
        String internalApiKey,
        String bootstrapEmail,
        String bootstrapDisplayName,
        String bootstrapPassword) {
}
