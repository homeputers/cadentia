package com.cadentia.api.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class InMemoryConversationSessionRepository implements ConversationSessionRepository {
    private final Map<UUID, ConversationSessionRecord> sessions = new HashMap<>();

    @Override
    public Optional<ConversationSessionRecord> findById(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public ConversationSessionRecord save(ConversationSessionRecord record) {
        sessions.put(record.id(), record);
        return record;
    }
}
