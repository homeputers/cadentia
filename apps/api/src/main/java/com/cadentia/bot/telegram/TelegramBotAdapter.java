package com.cadentia.bot.telegram;

import com.cadentia.bot.BotAdapter;

public class TelegramBotAdapter implements BotAdapter {

    @Override
    public void handleMessage(String chatId, String message) {
        throw new UnsupportedOperationException("Telegram bot integration has not been configured.");
    }
}
