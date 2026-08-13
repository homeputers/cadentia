package com.cadentia.bot.telegram;

import java.util.regex.Pattern;

final class TelegramCallbackData {
    static final int LIMIT = 64;
    static final String PREFIX = "cad:v1:";
    private static final Pattern VALUE_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{0,32}");

    private TelegramCallbackData() {}

    static String encode(TelegramCallbackAction action) {
        return encode(action, "");
    }

    static String encode(TelegramCallbackAction action, String value) {
        String safeValue = value == null ? "" : value;
        if (!VALUE_PATTERN.matcher(safeValue).matches()) {
            throw new IllegalArgumentException("Invalid Telegram callback value.");
        }
        String payload = PREFIX + action.token() + (safeValue.isBlank() ? "" : ":" + safeValue);
        if (payload.length() > LIMIT) {
            throw new IllegalArgumentException("Telegram callback payload exceeds Telegram limit.");
        }
        return payload;
    }

    static boolean validValue(String value) {
        return value != null && VALUE_PATTERN.matcher(value).matches();
    }
}
