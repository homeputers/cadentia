package com.cadentia.scraperadmin;

public record StructuredReviewNote(
        String category,
        String body,
        String followUpAction) {

    public StructuredReviewNote {
        category = requireText(category, "category");
        body = requireText(body, "body");
        followUpAction = blankToNull(followUpAction);
    }

    private static String requireText(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
