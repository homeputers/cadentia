package com.cadentia.auth.api;

import java.util.UUID;

public record InternalAuthUserResponse(UUID userId, String email, String displayName) {
}
