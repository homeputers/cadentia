package com.cadentia.bot.telegram;

import java.time.Instant;
import java.util.UUID;

public record TelegramBotSession(
        UUID id,
        String channel,
        String chatHash,
        String userHash,
        String churchInstanceId,
        UUID actorId,
        TelegramSessionState state,
        String pendingConfirmationRef,
        Long lastUpdateId,
        Integer lastMessageId,
        Instant createdAt,
        Instant updatedAt,
        Instant inactivityDeadline,
        Instant absoluteExpiration,
        String auditMetadataJson) {
}
