package com.cadentia.bot.telegram;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelegramAccessRequestRepository {

    TelegramAccessRequest save(TelegramAccessRequest request);

    Optional<TelegramAccessRequest> findPending(String channel, String chatHash, String userHash, String churchInstanceId);

    Optional<TelegramAccessRequest> findById(UUID id);

    List<TelegramAccessRequest> findByInstanceAndStatus(String churchInstanceId, TelegramAccessRequestStatus status);

    /**
     * Transitions a PENDING request to the given decision, purging the raw chat ID. Returns the
     * updated request, or empty when the request does not exist or was already decided.
     */
    Optional<TelegramAccessRequest> decide(
            UUID id, TelegramAccessRequestStatus decision, String decidedBy, String decisionReason, Instant decidedAt);
}
