package com.cadentia.catalog.model;

import java.util.UUID;

public record CreateImportCandidateCommand(
        UUID importBatchId,
        String externalCandidateId,
        String rawTitle,
        String normalizedTitle,
        String sourceArtistName,
        String sourceArtistMetadataJson,
        String ccliNumber,
        String lyricsHash,
        String sourcePayloadJson,
        ImportCandidateStatus status) {

    public CreateImportCandidateCommand {
        importBatchId = CatalogValidation.requireId(importBatchId, "importBatchId");
        externalCandidateId = CatalogValidation.requireOptionalTextIfPresent(externalCandidateId, "externalCandidateId");
        rawTitle = CatalogValidation.requireText(rawTitle, "rawTitle");
        normalizedTitle = CatalogValidation.requireText(normalizedTitle, "normalizedTitle");
        sourceArtistName = CatalogValidation.requireOptionalTextIfPresent(sourceArtistName, "sourceArtistName");
        sourceArtistMetadataJson = sourceArtistMetadataJson == null ? "{}"
                : CatalogValidation.requireText(sourceArtistMetadataJson, "sourceArtistMetadataJson");
        ccliNumber = CatalogValidation.requireOptionalTextIfPresent(ccliNumber, "ccliNumber");
        lyricsHash = CatalogValidation.requireOptionalTextIfPresent(lyricsHash, "lyricsHash");
        sourcePayloadJson = sourcePayloadJson == null ? "{}"
                : CatalogValidation.requireText(sourcePayloadJson, "sourcePayloadJson");
        status = CatalogValidation.requireEnum(status, "status");
    }
}
