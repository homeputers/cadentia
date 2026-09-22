package com.cadentia.catalog.model;

public record CreateSongCommand(
        String canonicalTitle,
        String normalizedTitle,
        String primaryLanguage,
        String originalArtistDisplay,
        String composerCredits,
        String ccliNumber,
        Integer yearWritten,
        SongStatus songStatus,
        SongRole songRole,
        String doctrinalNotes) {

    public CreateSongCommand(
            String canonicalTitle,
            String normalizedTitle,
            String primaryLanguage,
            String originalArtistDisplay,
            String composerCredits,
            String ccliNumber,
            Integer yearWritten,
            SongStatus songStatus,
            String doctrinalNotes) {
        this(
                canonicalTitle,
                normalizedTitle,
                primaryLanguage,
                originalArtistDisplay,
                composerCredits,
                ccliNumber,
                yearWritten,
                songStatus,
                null,
                doctrinalNotes);
    }

    public CreateSongCommand {
        canonicalTitle = CatalogValidation.requireText(canonicalTitle, "canonicalTitle");
        normalizedTitle = CatalogValidation.requireText(normalizedTitle, "normalizedTitle");
        primaryLanguage = CatalogValidation.requireText(primaryLanguage, "primaryLanguage");
        originalArtistDisplay = CatalogValidation.requireOptionalTextIfPresent(originalArtistDisplay, "originalArtistDisplay");
        ccliNumber = CatalogValidation.requireOptionalTextIfPresent(ccliNumber, "ccliNumber");
        yearWritten = CatalogValidation.requireRangeIfPresent(yearWritten, 1, 9999, "yearWritten");
        songStatus = CatalogValidation.requireEnum(songStatus, "songStatus");
    }
}
