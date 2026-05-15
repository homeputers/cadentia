package com.cadentia.catalog.model;

public record UpdateApprovalRecordCommand(
        ApprovalStatus status,
        String reviewer,
        String reviewNotes) {

    public UpdateApprovalRecordCommand {
        status = CatalogValidation.requireEnum(status, "status");
        reviewer = CatalogValidation.requireText(reviewer, "reviewer");
    }
}
