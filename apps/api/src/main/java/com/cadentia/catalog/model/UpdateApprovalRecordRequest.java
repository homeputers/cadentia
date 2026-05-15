package com.cadentia.catalog.model;

public record UpdateApprovalRecordRequest(
        ApprovalStatus status,
        String reviewer,
        String reviewNotes) {

    public UpdateApprovalRecordRequest {
        status = CatalogValidation.requireEnum(status, "status");
        reviewer = CatalogValidation.requireText(reviewer, "reviewer");
    }
}
