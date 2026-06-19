package com.cadentia.bot.telegram;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelegramIdentifierHasher {
    private final byte[] key;

    public TelegramIdentifierHasher(@Value("${cadentia.telegram.identity-hash-secret:local-dev-telegram-identity-secret}") String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String channel, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Telegram identifier is required for hashing.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((channel + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Telegram identifier hashing failed.", ex);
        }
    }
}
