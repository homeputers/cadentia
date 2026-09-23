package com.cadentia.auth.api;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        AuthUserResponse user) {
}
