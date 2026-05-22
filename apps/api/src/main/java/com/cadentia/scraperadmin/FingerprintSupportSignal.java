package com.cadentia.scraperadmin;

import java.math.BigDecimal;

public record FingerprintSupportSignal(
        String signalCode,
        String comparisonScope,
        BigDecimal weight,
        String direction,
        String evidence) {

    public FingerprintSupportSignal {
        if (signalCode == null || signalCode.isBlank()) {
            throw new IllegalArgumentException("signalCode is required");
        }
        if (comparisonScope == null || comparisonScope.isBlank()) {
            throw new IllegalArgumentException("comparisonScope is required");
        }
        if (weight == null) {
            throw new NullPointerException("weight is required");
        }
        if (direction == null || direction.isBlank()) {
            throw new IllegalArgumentException("direction is required");
        }
        evidence = evidence == null ? "" : evidence;
    }
}
