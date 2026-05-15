package com.cadentia.catalog.model;

import java.util.UUID;

public record CreateApprovalRecordCommand(
        UUID songId,
        UUID arrangementId,
        UUID lyricsDocumentId,
        ApprovalType approvalType,
        ApprovalStatus status,
        String reviewer,
        String reviewNotes) {

    public CreateApprovalRecordCommand {
        CatalogValidation.requireExactlyOneEntity(songId, arrangementId, lyricsDocumentId);
        approvalType = CatalogValidation.requireEnum(approvalType, "approvalType");
        status = CatalogValidation.requireEnum(status, "status");
        reviewer = CatalogValidation.requireText(reviewer, "reviewer");
    }
}
