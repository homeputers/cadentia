package com.cadentia.scraperadmin;

import java.util.Optional;
import java.util.UUID;

public record CatalogSongCandidate(
        UUID songId,
        String canonicalTitle,
        String normalizedTitle,
        String originalArtistDisplay,
        String ccliNumber,
        String lyricsHash) {

    public CatalogSongCandidate {
        if (songId == null) {
            throw new NullPointerException("songId is required");
        }
        if (canonicalTitle == null || canonicalTitle.isBlank()) {
            throw new IllegalArgumentException("canonicalTitle is required");
        }
        if (normalizedTitle == null || normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("normalizedTitle is required");
        }
        normalizedTitle = normalizedTitle.trim();
        originalArtistDisplay = blankToNull(originalArtistDisplay);
        ccliNumber = normalizeIdentifier(ccliNumber);
        lyricsHash = blankToNull(lyricsHash);
    }

    public Optional<String> optionalOriginalArtistDisplay() {
        return Optional.ofNullable(originalArtistDisplay);
    }

    public Optional<String> optionalCcliNumber() {
        return Optional.ofNullable(ccliNumber);
    }

    public Optional<String> optionalLyricsHash() {
        return Optional.ofNullable(lyricsHash);
    }

    private static String normalizeIdentifier(String value) {
        String stripped = blankToNull(value);
        return stripped == null ? null : stripped.replaceAll("\\s+", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
