package com.cadentia.catalog.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum LyricsFormat {
    PLAIN_TEXT("plain_text"),
    CHORDPRO("chordpro"),
    ONSONG("onsong"),
    MARKDOWN("markdown");

    private static final String ACCEPTED_VALUES = Arrays.stream(values())
            .map(LyricsFormat::storageValue)
            .collect(Collectors.joining(", "));

    private final String storageValue;

    LyricsFormat(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }

    public static LyricsFormat fromDeclaredValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("format is required; accepted values: " + ACCEPTED_VALUES);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (LyricsFormat format : values()) {
            if (format.storageValue.equals(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException(
                "unsupported lyrics format '%s'; accepted values: %s".formatted(value, ACCEPTED_VALUES));
    }

    public static String acceptedValues() {
        return ACCEPTED_VALUES;
    }
}
