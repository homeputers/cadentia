package com.cadentia.catalog.lyrics;

import java.util.UUID;

public record ParserReviewAnnotationCommand(UUID lyricsDocumentId, String reviewer, String note) {

    public ParserReviewAnnotationCommand {
        if (lyricsDocumentId == null) {
            throw new IllegalArgumentException("lyricsDocumentId is required");
        }
        reviewer = requireText(reviewer, "reviewer");
        note = requireText(note, "note");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
