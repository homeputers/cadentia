package com.cadentia.songimport;

import java.util.Collection;
import java.util.Objects;

final class ImportConnectorValidation {

    private ImportConnectorValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    static String requireOptionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank when provided");
        }
        return value;
    }

    static <T> T requireNonNull(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " is required");
    }

    static <T extends Collection<?>> T requireNonEmpty(T value, String fieldName) {
        requireNonNull(value, fieldName);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
