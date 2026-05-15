package com.cadentia.scraperadmin;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import java.util.Objects;
import java.util.UUID;

public record MergeIntoExistingSongCommand(
        UUID importCandidateId,
        UUID targetSongId,
        String reviewer,
        String sourceSystem,
        String sourceUri,
        String sourceLabel,
        LicenseType licenseType,
        String licenseNotes,
        ImportMethod importMethod) {

    public MergeIntoExistingSongCommand {
        importCandidateId = Objects.requireNonNull(importCandidateId, "importCandidateId is required");
        targetSongId = Objects.requireNonNull(targetSongId, "targetSongId is required");
        reviewer = requireText(reviewer, "reviewer");
        sourceSystem = requireText(sourceSystem, "sourceSystem");
        sourceLabel = requireText(sourceLabel, "sourceLabel");
        licenseType = Objects.requireNonNull(licenseType, "licenseType is required");
        importMethod = Objects.requireNonNull(importMethod, "importMethod is required");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
