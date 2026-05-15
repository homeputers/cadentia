package com.cadentia.catalog.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProvenanceRecordRequest(
        UUID songId,
        UUID arrangementId,
        UUID lyricsDocumentId,
        UUID importBatchId,
        String sourceSystem,
        String sourceUri,
        String sourceLabel,
        LicenseType licenseType,
        String licenseNotes,
        ImportMethod importMethod,
        BigDecimal confidenceScore) {

    public CreateProvenanceRecordRequest {
        CatalogValidation.requireExactlyOneEntity(songId, arrangementId, lyricsDocumentId);
        importBatchId = CatalogValidation.requireId(importBatchId, "importBatchId");
        sourceSystem = CatalogValidation.requireText(sourceSystem, "sourceSystem");
        sourceUri = CatalogValidation.requireUriIfPresent(sourceUri, "sourceUri");
        sourceLabel = CatalogValidation.requireText(sourceLabel, "sourceLabel");
        licenseType = CatalogValidation.requireEnum(licenseType, "licenseType");
        importMethod = CatalogValidation.requireEnum(importMethod, "importMethod");
        confidenceScore = CatalogValidation.requireUnitRangeIfPresent(confidenceScore, "confidenceScore");
    }
}
