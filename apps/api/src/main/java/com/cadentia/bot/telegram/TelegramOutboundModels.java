package com.cadentia.bot.telegram;

import java.time.Instant;
import java.util.UUID;

public final class TelegramOutboundModels {
    private TelegramOutboundModels() {
    }

    public enum OutboundStatus {
        PENDING,
        SENT,
        RETRY_SCHEDULED,
        DEAD_LETTERED,
        DUPLICATE_SUPPRESSED
    }

    public enum FailureCategory {
        NETWORK,
        TELEGRAM_5XX,
        RATE_LIMIT,
        CHAT_BLOCKED,
        INVALID_CHAT,
        MALFORMED_REQUEST,
        UNAUTHORIZED_BOT,
        DISABLED_CHANNEL,
        UNKNOWN
    }

    public record TelegramOutboundSendRecord(
            UUID id,
            String idempotencyKey,
            String correlationId,
            String chatHash,
            String operation,
            String sanitizedPreview,
            OutboundStatus status,
            int attempts,
            int maxAttempts,
            Instant nextAttemptAt,
            String telegramMessageId,
            FailureCategory failureCategory,
            String sanitizedFailureDetail,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TelegramDeadLetterRecord(
            UUID id,
            UUID outboundId,
            String idempotencyKey,
            String correlationId,
            String chatHash,
            String operation,
            FailureCategory failureCategory,
            String sanitizedFailureDetail,
            String sanitizedPreview,
            int attempts,
            Instant createdAt) {
    }

    public record TelegramSendResult(boolean delivered, String telegramMessageId) {
        public static TelegramSendResult delivered(String telegramMessageId) {
            return new TelegramSendResult(true, telegramMessageId);
        }
    }
}
