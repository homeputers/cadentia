package com.cadentia.bot.telegram;

import java.util.Arrays;
import java.util.Optional;

public enum TelegramCallbackAction {
    SCRIPTURE_THEME("scripture_theme", "verseText"),
    SHAPE_COUNTS("shape_counts", "counts"),
    LANGUAGE("language", "language"),
    KEY_POLICY("key_policy", "keyPolicy"),
    TEMPO_POLICY("tempo_policy", "tempoPolicy"),
    ENERGY_ARC("energy_arc", "energyArc"),
    CONFIRM("confirm", "confirmation"),
    REVISE("revise", "revision"),
    CANCEL("cancel", "cancellation");

    private final String token;
    private final String guidedField;

    TelegramCallbackAction(String token, String guidedField) {
        this.token = token;
        this.guidedField = guidedField;
    }

    public String token() {
        return token;
    }

    public String guidedField() {
        return guidedField;
    }

    public static Optional<TelegramCallbackAction> fromToken(String token) {
        return Arrays.stream(values()).filter(candidate -> candidate.token.equals(token)).findFirst();
    }
}
