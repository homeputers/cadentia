package com.cadentia.bot.telegram;

import java.time.Duration;

public class TelegramClientException extends RuntimeException {
    private final int statusCode;
    private final Duration retryAfter;

    public TelegramClientException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    public TelegramClientException(int statusCode, String message, Duration retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public int statusCode() {
        return statusCode;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
