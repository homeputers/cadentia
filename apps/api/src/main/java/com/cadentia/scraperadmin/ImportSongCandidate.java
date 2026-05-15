package com.cadentia.scraperadmin;

import java.util.Optional;

public record ImportSongCandidate(
        String rawTitle,
        String normalizedTitle,
        String sourceArtistName,
        String ccliNumber,
        String lyricsHash) {

    public ImportSongCandidate {
        if (rawTitle == null || rawTitle.isBlank()) {
            throw new IllegalArgumentException("rawTitle is required");
        }
        normalizedTitle = blankToNull(normalizedTitle);
        sourceArtistName = blankToNull(sourceArtistName);
        ccliNumber = normalizeIdentifier(ccliNumber);
        lyricsHash = blankToNull(lyricsHash);
    }

    public Optional<String> optionalNormalizedTitle() {
        return Optional.ofNullable(normalizedTitle);
    }

    public Optional<String> optionalSourceArtistName() {
        return Optional.ofNullable(sourceArtistName);
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
