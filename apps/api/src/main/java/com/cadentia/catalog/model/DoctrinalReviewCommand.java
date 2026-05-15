package com.cadentia.catalog.model;

import java.util.UUID;

public record DoctrinalReviewCommand(
        UUID songId,
        UUID lyricsDocumentId,
        ApprovalStatus status,
        String reviewer,
        String reviewNotes) {

    public DoctrinalReviewCommand {
        CatalogValidation.requireExactlyOneEntity(songId, null, lyricsDocumentId);
        status = CatalogValidation.requireEnum(status, "status");
        reviewer = CatalogValidation.requireText(reviewer, "reviewer");
        requireHumanReviewer(reviewer);
    }

    private static void requireHumanReviewer(String reviewer) {
        String normalizedReviewer = reviewer.toLowerCase();
        if (normalizedReviewer.contains("llm")
                || normalizedReviewer.contains("openai")
                || normalizedReviewer.contains("chatgpt")
                || normalizedReviewer.contains("gpt")
                || normalizedReviewer.contains("ai-agent")) {
            throw new IllegalArgumentException("reviewer must identify a human doctrinal reviewer");
        }
    }
}
