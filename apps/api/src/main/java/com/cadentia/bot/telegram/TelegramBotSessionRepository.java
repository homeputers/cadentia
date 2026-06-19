package com.cadentia.bot.telegram;

import java.util.Optional;
import java.util.UUID;

public interface TelegramBotSessionRepository {
    Optional<TelegramBotSession> findActive(String channel, String chatHash, String userHash);

    TelegramBotSession save(TelegramBotSession session);

    void transition(UUID sessionId, TelegramSessionState state);
}
