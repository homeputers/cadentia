package com.cadentia.bot.telegram;

import java.util.Optional;
import java.util.Set;

public interface TelegramIdentityRepository {
    Optional<TelegramLinkedActor> findByTelegramHashes(String channel, String chatHash, String userHash);

    /** Creates or re-activates a LINKED identity for the given hashes and instance. */
    TelegramLinkedActor saveLink(String channel, String chatHash, String userHash, String churchInstanceId, Set<String> roles);
}
