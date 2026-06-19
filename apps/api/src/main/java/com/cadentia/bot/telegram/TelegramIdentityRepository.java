package com.cadentia.bot.telegram;

import java.util.Optional;

public interface TelegramIdentityRepository {
    Optional<TelegramLinkedActor> findByTelegramHashes(String channel, String chatHash, String userHash);
}
