package com.cadentia.bot.telegram;

import java.util.Set;
import java.util.UUID;

public record TelegramLinkedActor(
        UUID actorId,
        String churchInstanceId,
        Set<String> roles,
        TelegramIdentityStatus status) {

    public TelegramLinkedActor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasAnyRole(Set<String> allowedRoles) {
        return roles.stream().anyMatch(allowedRoles::contains);
    }
}
