package com.cadentia.bot.telegram;

public interface TelegramWebhookIdempotencyStore {

    IdempotencyResult record(String botId, String channelId, long updateId);

    enum IdempotencyResult {
        ACCEPTED,
        DUPLICATE_ACCEPTED
    }
}
