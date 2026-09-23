package com.cadentia.api.auth;

import java.util.UUID;

public record FirstPartyAuthInvitation(
        UUID userId,
        String email,
        String displayName,
        String activationToken) {
}
