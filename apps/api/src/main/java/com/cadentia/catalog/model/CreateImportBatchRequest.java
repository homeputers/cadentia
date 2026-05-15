package com.cadentia.catalog.model;

public record CreateImportBatchRequest(
        String sourceSystem,
        String initiatedBy,
        ImportBatchStatus status,
        String summaryJson) {

    public CreateImportBatchRequest {
        sourceSystem = CatalogValidation.requireText(sourceSystem, "sourceSystem");
        initiatedBy = CatalogValidation.requireText(initiatedBy, "initiatedBy");
        status = CatalogValidation.requireEnum(status, "status");
        summaryJson = summaryJson == null ? "{}" : CatalogValidation.requireText(summaryJson, "summaryJson");
    }
}
