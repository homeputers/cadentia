package com.cadentia.bot.telegram;

import java.util.List;

public record TelegramRenderedMessage(
        String chatId,
        String text,
        String parseMode,
        TelegramInlineKeyboard inlineKeyboard,
        String callbackQueryId,
        String callbackAcknowledgement) {

    public static TelegramRenderedMessage message(String chatId, String text, TelegramInlineKeyboard keyboard) {
        return new TelegramRenderedMessage(chatId, text, "HTML", keyboard, null, null);
    }

    public static TelegramRenderedMessage callbackAck(String callbackQueryId, String text) {
        return new TelegramRenderedMessage(null, null, null, null, callbackQueryId, text);
    }

    public boolean callbackOnly() {
        return callbackQueryId != null && text == null;
    }

    public record TelegramInlineKeyboard(List<List<TelegramInlineKeyboardButton>> rows) {
        public TelegramInlineKeyboard {
            rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
        }
    }

    public record TelegramInlineKeyboardButton(String text, String callbackData) {}
}
