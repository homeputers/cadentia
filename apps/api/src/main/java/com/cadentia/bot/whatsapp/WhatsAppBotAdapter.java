package com.cadentia.bot.whatsapp;

import com.cadentia.bot.BotAdapter;

public class WhatsAppBotAdapter implements BotAdapter {

    @Override
    public void handleMessage(String chatId, String message) {
        throw new UnsupportedOperationException("WhatsApp bot integration has not been configured.");
    }
}
