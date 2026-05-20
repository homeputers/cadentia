package com.cadentia.reng.scoring;

import java.util.Locale;

public enum EnergyArc {
    STEADY,
    RISING,
    FALLING,
    LOW_TO_HIGH,
    HIGH_TO_LOW;

    public static final String VERSION = "energy-arc-v1";
    public static final EnergyArc DEFAULT = STEADY;

    public static EnergyArc fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "steady" -> STEADY;
            case "rising" -> RISING;
            case "falling" -> FALLING;
            case "low_to_high" -> LOW_TO_HIGH;
            case "high_to_low" -> HIGH_TO_LOW;
            default -> DEFAULT;
        };
    }
}
