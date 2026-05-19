package com.cadentia.catalog.lyrics;

public record ParserWarning(String code, String message, Integer lineNumber) {

    public ParserWarning {
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
