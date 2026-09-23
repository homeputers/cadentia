package com.cadentia.api.auth;

import java.util.UUID;

public record FirstPartyAuthUser(UUID userId, String email, String displayName) {
}
