package com.cadentia.bot.telegram;

import java.time.Instant;
import java.util.UUID;

/**
 * Self-service Telegram access request. The raw {@code chatId} is retained only while
 * {@link TelegramAccessRequestStatus#PENDING} so the decision can be pushed to the requester;
 * it is purged when the request is approved or rejected.
 */
public record TelegramAccessRequest(
        UUID id,
        String channel,
        String chatHash,
        String userHash,
        String chatId,
        String churchInstanceId,
        TelegramAccessRequestStatus status,
        Instant requestedAt,
        Instant decidedAt,
        String decidedBy,
        String decisionReason) {

    /** Pseudonymous reference safe for admin UI display; never exposes raw Telegram identifiers. */
    public String maskedReference() {
        if (chatHash == null || chatHash.isBlank()) {
            return "unknown";
        }
        return chatHash.substring(0, Math.min(12, chatHash.length()));
    }
}
