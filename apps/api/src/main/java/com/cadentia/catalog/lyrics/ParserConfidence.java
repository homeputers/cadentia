package com.cadentia.catalog.lyrics;

public record ParserConfidence(String field, double score, String evidence) {

    public ParserConfidence {
        field = requireText(field, "field");
        evidence = requireText(evidence, "evidence");
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be between 0 and 1");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
