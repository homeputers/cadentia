package com.cadentia.catalog.lyrics;

public record ParserConfidence(String field, double score, String evidence) {

    public ParserConfidence {
        field = ParserOutputValidation.requireText(field, "field");
        evidence = ParserOutputValidation.requireText(evidence, "evidence");
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be between 0 and 1");
        }
    }
}
