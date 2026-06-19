package com.cadentia.bot.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class ConstantTimeSecretMatcher {

    private ConstantTimeSecretMatcher() {
    }

    static boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
