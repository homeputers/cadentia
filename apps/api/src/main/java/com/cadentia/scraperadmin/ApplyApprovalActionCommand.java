package com.cadentia.scraperadmin;

import com.cadentia.catalog.model.ApprovalType;
import java.util.Objects;
import java.util.UUID;

public record ApplyApprovalActionCommand(
        UUID songId,
        UUID arrangementId,
        UUID lyricsDocumentId,
        ApprovalType approvalType,
        ApprovalReviewAction action,
        String reviewer,
        String reviewNotes) {

    public ApplyApprovalActionCommand {
        Objects.requireNonNull(approvalType, "approvalType is required");
        Objects.requireNonNull(action, "action is required");
        reviewer = requireText(reviewer, "reviewer");
        requireExactlyOneEntity(songId, arrangementId, lyricsDocumentId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static void requireExactlyOneEntity(UUID songId, UUID arrangementId, UUID lyricsDocumentId) {
        int present = 0;
        present += songId == null ? 0 : 1;
        present += arrangementId == null ? 0 : 1;
        present += lyricsDocumentId == null ? 0 : 1;
        if (present != 1) {
            throw new IllegalArgumentException(
                    "exactly one of songId, arrangementId, or lyricsDocumentId is required");
        }
    }
}
