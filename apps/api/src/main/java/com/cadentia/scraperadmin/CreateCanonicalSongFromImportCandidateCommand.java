package com.cadentia.scraperadmin;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import java.util.Objects;
import java.util.UUID;

public record CreateCanonicalSongFromImportCandidateCommand(
        UUID importCandidateId,
        String reviewer,
        String canonicalTitle,
        String primaryLanguage,
        String originalArtistDisplay,
        String composerCredits,
        String ccliNumber,
        Integer yearWritten,
        String doctrinalNotes,
        String arrangementName,
        String sourceSystem,
        String sourceUri,
        String sourceLabel,
        LicenseType licenseType,
        String licenseNotes,
        ImportMethod importMethod) {

    public CreateCanonicalSongFromImportCandidateCommand {
        importCandidateId = Objects.requireNonNull(importCandidateId, "importCandidateId is required");
        reviewer = requireText(reviewer, "reviewer");
        canonicalTitle = requireText(canonicalTitle, "canonicalTitle");
        primaryLanguage = requireText(primaryLanguage, "primaryLanguage");
        sourceSystem = requireText(sourceSystem, "sourceSystem");
        sourceLabel = requireText(sourceLabel, "sourceLabel");
        licenseType = Objects.requireNonNull(licenseType, "licenseType is required");
        importMethod = Objects.requireNonNull(importMethod, "importMethod is required");
        if (arrangementName != null && arrangementName.isBlank()) {
            throw new IllegalArgumentException("arrangementName must not be blank when provided");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
