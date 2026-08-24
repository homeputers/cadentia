package com.cadentia.bot.telegram;

import java.util.Arrays;
import java.util.Optional;

public enum TelegramCommand {
    START("start"),
    HELP("help"),
    NEW_SETLIST("newsetlist"),
    STATUS("status"),
    CANCEL("cancel"),
    SETTINGS("settings"),
    REQUEST_ACCESS("requestaccess");

    private final String token;

    TelegramCommand(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static Optional<TelegramCommand> fromText(String text) {
        if (text == null || !text.startsWith("/")) {
            return Optional.empty();
        }
        String command = text.substring(1).split("\\s+", 2)[0].split("@", 2)[0].toLowerCase();
        return Arrays.stream(values()).filter(candidate -> candidate.token.equals(command)).findFirst();
    }
}
