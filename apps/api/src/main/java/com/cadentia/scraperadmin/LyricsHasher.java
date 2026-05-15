package com.cadentia.scraperadmin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Computes hashes only for text already allowed by source provenance. */
public final class LyricsHasher {

    private static final Pattern DIACRITIC_MARK = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public Optional<String> hashAllowedSourceText(String lyricsText, boolean allowedBySourceProvenance) {
        if (!allowedBySourceProvenance || lyricsText == null || lyricsText.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("sha256:" + sha256(normalizeLyricsText(lyricsText)));
    }

    private String normalizeLyricsText(String lyricsText) {
        String withoutDiacritics = DIACRITIC_MARK.matcher(
                        Normalizer.normalize(lyricsText, Normalizer.Form.NFD))
                .replaceAll("");
        return WHITESPACE.matcher(withoutDiacritics.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
