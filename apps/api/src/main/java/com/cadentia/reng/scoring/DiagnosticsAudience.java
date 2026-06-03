package com.cadentia.reng.scoring;

import java.util.Locale;

public enum DiagnosticsAudience {
    PUBLIC,
    WORSHIP_LEADER,
    ADMIN;

    public static DiagnosticsAudience fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "public" -> PUBLIC;
            case "worship_leader", "worship-leader", "worshipleader" -> WORSHIP_LEADER;
            case "admin" -> ADMIN;
            default -> throw new IllegalArgumentException("Unknown explanation audience: " + value);
        };
    }

    public String wireValue() {
        return switch (this) {
            case PUBLIC -> "public";
            case WORSHIP_LEADER -> "worship_leader";
            case ADMIN -> "admin";
        };
    }
}
