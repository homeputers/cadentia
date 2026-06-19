package com.cadentia.bot.telegram;

import java.util.Locale;
import java.util.Optional;

public record TelegramChannelEvent(
        long updateId,
        TelegramEventKind kind,
        String chatId,
        String userId,
        Integer messageId,
        String text,
        TelegramCommand command,
        TelegramCallbackAction callbackAction,
        String callbackValue,
        String callbackQueryId,
        Integer callbackMessageId,
        Locale locale,
        String correlationId) {

    public Optional<TelegramCommand> commandOptional() {
        return Optional.ofNullable(command);
    }

    public Optional<TelegramCallbackAction> callbackActionOptional() {
        return Optional.ofNullable(callbackAction);
    }
}
