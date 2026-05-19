package com.cadentia.catalog.lyrics;

final class ParserOutputValidation {

    private ParserOutputValidation() {}

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
