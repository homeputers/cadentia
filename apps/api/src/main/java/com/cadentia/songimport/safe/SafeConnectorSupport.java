package com.cadentia.songimport.safe;

import com.cadentia.catalog.model.LicenseType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class SafeConnectorSupport {

    private SafeConnectorSupport() {}

    static String sha256(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static LicenseType parseLicense(String value) {
        if (value == null || value.isBlank()) {
            return LicenseType.UNKNOWN;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "PUBLIC_DOMAIN" -> LicenseType.PUBLIC_DOMAIN;
            case "CCLI" -> LicenseType.CCLI;
            case "DIRECT_PERMISSION" -> LicenseType.DIRECT_PERMISSION;
            default -> LicenseType.UNKNOWN;
        };
    }
}
