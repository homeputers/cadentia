package com.cadentia.bot.telegram;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTelegramWebhookIdempotencyStore implements TelegramWebhookIdempotencyStore {

    private final Set<String> acceptedKeys = ConcurrentHashMap.newKeySet();

    @Override
    public IdempotencyResult record(String botId, String channelId, long updateId) {
        String key = botId + ":" + channelId + ":" + updateId;
        return acceptedKeys.add(key) ? IdempotencyResult.ACCEPTED : IdempotencyResult.DUPLICATE_ACCEPTED;
    }
}
