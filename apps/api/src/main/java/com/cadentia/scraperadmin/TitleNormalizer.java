package com.cadentia.scraperadmin;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic song title normalization for duplicate detection.
 *
 * <p>Rules are intentionally simple and auditable: lowercase with {@link Locale#ROOT}, remove
 * diacritics, expand ampersands to {@code and}, drop parenthetical variant suffixes, replace
 * punctuation with spaces, and collapse whitespace into hyphen-separated tokens. Parenthetical
 * text is treated as source variant metadata such as live, acoustic, radio edit, or featured
 * artist notes and is not part of the
 * deduplication key.
 */
public final class TitleNormalizer {

    private static final Pattern DIACRITIC_MARK = Pattern.compile("\\p{M}+");
    private static final Pattern PARENTHETICAL_TEXT = Pattern.compile("\\s*\\([^)]*\\)");
    private static final Pattern APOSTROPHE = Pattern.compile("[’'`]");
    private static final Pattern AMPERSAND = Pattern.compile("&");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalize(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        String withoutDiacritics = DIACRITIC_MARK.matcher(
                        Normalizer.normalize(title, Normalizer.Form.NFD))
                .replaceAll("");
        String normalized = withoutDiacritics.toLowerCase(Locale.ROOT);
        normalized = AMPERSAND.matcher(normalized).replaceAll(" and ");
        normalized = PARENTHETICAL_TEXT.matcher(normalized).replaceAll(" ");
        normalized = APOSTROPHE.matcher(normalized).replaceAll("");
        normalized = NON_ALPHANUMERIC.matcher(normalized).replaceAll(" ");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("title must contain alphanumeric text");
        }
        return WHITESPACE.matcher(normalized).replaceAll("-");
    }
}
