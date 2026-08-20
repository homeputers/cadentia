package com.cadentia.scraperadmin;

import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LicenseType;
import java.util.List;
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
        ImportMethod importMethod,
        String musicalKey,
        KeyMode keyMode,
        Integer tempoBpm,
        String timeSignature,
        Integer durationSeconds,
        Integer energyLevel,
        Integer difficultyLevel,
        String lyrics,
        String lyricsFormat,
        String chordChart,
        String arrangementNotes,
        List<String> themes,
        List<String> scriptureReferences) {

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
        themes = List.copyOf(themes == null ? List.of() : themes);
        scriptureReferences = List.copyOf(scriptureReferences == null ? List.of() : scriptureReferences);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
