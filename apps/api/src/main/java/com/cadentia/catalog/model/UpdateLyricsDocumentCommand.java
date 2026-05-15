package com.cadentia.catalog.model;

public record UpdateLyricsDocumentCommand(
        LyricsFormat format,
        String content,
        String contentHash,
        boolean containsChords,
        boolean containsSections,
        String sourceReference,
        String editedBy) {

    public UpdateLyricsDocumentCommand {
        format = CatalogValidation.requireEnum(format, "format");
        content = CatalogValidation.requireText(content, "content");
        contentHash = CatalogValidation.requireText(contentHash, "contentHash");
        sourceReference = CatalogValidation.requireText(sourceReference, "sourceReference");
        editedBy = CatalogValidation.requireText(editedBy, "editedBy");
    }
}
