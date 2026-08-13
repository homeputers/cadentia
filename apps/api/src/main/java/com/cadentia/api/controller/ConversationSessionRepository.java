package com.cadentia.api.controller;

import java.util.Optional;
import java.util.UUID;

public interface ConversationSessionRepository {
    Optional<ConversationSessionRecord> findById(UUID sessionId);

    ConversationSessionRecord save(ConversationSessionRecord record);
}
