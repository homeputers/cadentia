package com.cadentia.catalog.model;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.UUID;

final class CatalogValidation {

    private CatalogValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    static String requireOptionalTextIfPresent(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank when provided");
        }
        return value;
    }

    static UUID requireId(UUID value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required");
    }

    static Integer requirePositiveIfPresent(Integer value, String fieldName) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive when provided");
        }
        return value;
    }

    static Integer requireRangeIfPresent(Integer value, int min, int max, String fieldName) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max);
        }
        return value;
    }

    static int requireAtLeast(int value, int min, String fieldName) {
        if (value < min) {
            throw new IllegalArgumentException(fieldName + " must be at least " + min);
        }
        return value;
    }

    static BigDecimal requireUnitRangeIfPresent(BigDecimal value, String fieldName) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 1");
        }
        return value;
    }

    static <T extends Enum<T>> T requireEnum(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required");
    }

    static void requireExactlyOneEntity(UUID songId, UUID arrangementId, UUID lyricsDocumentId) {
        int present = 0;
        present += songId == null ? 0 : 1;
        present += arrangementId == null ? 0 : 1;
        present += lyricsDocumentId == null ? 0 : 1;
        if (present != 1) {
            throw new IllegalArgumentException(
                    "exactly one of songId, arrangementId, or lyricsDocumentId is required");
        }
    }

    static String requireUriIfPresent(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        requireOptionalTextIfPresent(value, fieldName);
        try {
            new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid URI", exception);
        }
        return value;
    }
}
