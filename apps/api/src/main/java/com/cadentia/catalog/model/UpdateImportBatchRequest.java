package com.cadentia.catalog.model;

public record UpdateImportBatchRequest(
        ImportBatchStatus status,
        String summaryJson,
        boolean completed) {

    public UpdateImportBatchRequest {
        status = CatalogValidation.requireEnum(status, "status");
        summaryJson = summaryJson == null ? "{}" : CatalogValidation.requireText(summaryJson, "summaryJson");
    }
}
