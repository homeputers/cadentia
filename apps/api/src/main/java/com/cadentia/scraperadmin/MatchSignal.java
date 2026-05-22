package com.cadentia.scraperadmin;

import java.math.BigDecimal;

public record MatchSignal(
        String name,
        boolean matched,
        BigDecimal weight,
        String candidateValue,
        String catalogValue,
        String explanation,
        FingerprintSupportSignal fingerprintSupportSignal) {

    public MatchSignal {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (weight == null) {
            throw new NullPointerException("weight is required");
        }
        candidateValue = candidateValue == null ? "" : candidateValue;
        catalogValue = catalogValue == null ? "" : catalogValue;
        explanation = explanation == null ? "" : explanation;
    }

    public MatchSignal(String name, boolean matched, BigDecimal weight, String candidateValue, String catalogValue, String explanation) {
        this(name, matched, weight, candidateValue, catalogValue, explanation, null);
    }
}
