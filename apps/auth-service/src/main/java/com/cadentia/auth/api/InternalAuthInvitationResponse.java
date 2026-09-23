package com.cadentia.auth.api;

import java.util.UUID;

public record InternalAuthInvitationResponse(
        UUID userId,
        String email,
        String displayName,
        String activationToken) {
}
