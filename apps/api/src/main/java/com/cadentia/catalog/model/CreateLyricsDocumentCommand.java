package com.cadentia.catalog.model;

import java.util.UUID;

public record CreateLyricsDocumentCommand(
        UUID arrangementId,
        LyricsFormat format,
        String content,
        String contentHash,
        int versionNumber,
        boolean current,
        boolean containsChords,
        boolean containsSections,
        String sourceReference,
        String createdBy) {

    public CreateLyricsDocumentCommand {
        arrangementId = CatalogValidation.requireId(arrangementId, "arrangementId");
        format = CatalogValidation.requireEnum(format, "format");
        content = CatalogValidation.requireText(content, "content");
        contentHash = CatalogValidation.requireText(contentHash, "contentHash");
        if (versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        sourceReference = CatalogValidation.requireText(sourceReference, "sourceReference");
        createdBy = CatalogValidation.requireText(createdBy, "createdBy");
    }
}
