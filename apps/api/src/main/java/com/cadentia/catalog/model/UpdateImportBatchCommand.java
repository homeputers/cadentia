package com.cadentia.catalog.model;

public record UpdateImportBatchCommand(
        ImportBatchStatus status,
        String summaryJson,
        boolean completed) {

    public UpdateImportBatchCommand {
        status = CatalogValidation.requireEnum(status, "status");
        summaryJson = summaryJson == null ? "{}" : CatalogValidation.requireText(summaryJson, "summaryJson");
    }
}
