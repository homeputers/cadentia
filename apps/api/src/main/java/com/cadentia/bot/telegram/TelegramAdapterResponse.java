package com.cadentia.bot.telegram;

public record TelegramAdapterResponse(
        TelegramAdapterResponseStatus status,
        String message,
        TelegramChannelEvent event,
        String guidedField) {}
