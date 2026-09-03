package com.cadentia.catalog.scripture;

import java.util.Locale;
import java.util.Objects;

/**
 * Canonical, deterministic representation of a scripture reference with book, optional chapter, and
 * optional verse range. Book-only and chapter-level references are supported so curated SCRIPTURE
 * tags of any granularity can be matched against requests.
 */
public record CanonicalScriptureReference(String book, Integer chapter, Integer startVerse, Integer endVerse) {

    public enum MatchTier {
        NONE,
        BOOK,
        CHAPTER,
        EXACT_OR_OVERLAP
    }

    public CanonicalScriptureReference {
        Objects.requireNonNull(book, "book must not be null");
        if (book.isBlank()) {
            throw new IllegalArgumentException("book must not be blank");
        }
        if (startVerse == null && endVerse != null) {
            throw new IllegalArgumentException("endVerse requires startVerse");
        }
    }

    /**
     * Returns the strongest containment tier between this reference (a curated tag/document scope)
     * and the query reference (the requested scope).
     */
    public MatchTier matchTier(CanonicalScriptureReference query) {
        if (!book.equals(query.book())) {
            return MatchTier.NONE;
        }
        if (chapter == null || query.chapter() == null) {
            return MatchTier.BOOK;
        }
        if (!chapter.equals(query.chapter())) {
            return MatchTier.NONE;
        }
        if (startVerse == null && query.startVerse() == null) {
            return MatchTier.EXACT_OR_OVERLAP;
        }
        if (startVerse == null || query.startVerse() == null) {
            return MatchTier.CHAPTER;
        }
        int queryEnd = query.endVerse() == null ? query.startVerse() : query.endVerse();
        int documentEnd = endVerse == null ? startVerse : endVerse;
        return startVerse <= queryEnd && documentEnd >= query.startVerse()
                ? MatchTier.EXACT_OR_OVERLAP
                : MatchTier.NONE;
    }

    public String display() {
        StringBuilder value = new StringBuilder(displayBook());
        if (chapter == null) {
            return value.toString();
        }
        value.append(' ').append(chapter);
        if (startVerse == null) {
            return value.toString();
        }
        value.append(':').append(startVerse);
        if (endVerse != null && !endVerse.equals(startVerse)) {
            value.append('-').append(endVerse);
        }
        return value.toString();
    }

    private String displayBook() {
        StringBuilder result = new StringBuilder();
        for (String word : book.split(" ")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            if (!word.isEmpty()) {
                result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return result.toString();
    }
}
