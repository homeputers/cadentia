package com.cadentia.scraperadmin;

import java.math.BigDecimal;
import java.util.Map;

public final class FingerprintSignalRegistry {

    public static final String CODE_LYRICS_HASH_EXACT = "FP_LYRICS_HASH_EXACT";

    private static final Map<String, RegisteredSignal> SIGNALS = Map.of(
            CODE_LYRICS_HASH_EXACT,
            new RegisteredSignal(CODE_LYRICS_HASH_EXACT, "source_document", new BigDecimal("0.3500")));

    private FingerprintSignalRegistry() {}

    public static RegisteredSignal requireRegistered(String code) {
        RegisteredSignal signal = SIGNALS.get(code);
        if (signal == null) {
            throw new IllegalArgumentException("unregistered fingerprint signal code: " + code);
        }
        return signal;
    }

    public record RegisteredSignal(String code, String comparisonScope, BigDecimal weight) {}
}
