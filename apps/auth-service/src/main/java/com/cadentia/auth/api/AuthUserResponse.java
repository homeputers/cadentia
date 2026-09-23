package com.cadentia.auth.api;

import com.cadentia.auth.domain.AuthUser;
import java.util.UUID;

public record AuthUserResponse(UUID userId, String email, String displayName) {

    public static AuthUserResponse from(AuthUser user) {
        return new AuthUserResponse(user.userId(), user.email(), user.displayName());
    }
}
