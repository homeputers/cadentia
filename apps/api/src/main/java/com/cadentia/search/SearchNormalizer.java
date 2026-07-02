package com.cadentia.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9#]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Map<String, String> SCRIPTURE_BOOKS = Map.ofEntries(
            Map.entry("gen", "genesis"), Map.entry("ge", "genesis"),
            Map.entry("ex", "exodus"), Map.entry("exo", "exodus"),
            Map.entry("ps", "psalms"), Map.entry("psa", "psalms"), Map.entry("psalm", "psalms"),
            Map.entry("jn", "john"), Map.entry("jhn", "john"), Map.entry("john", "john"),
            Map.entry("rom", "romans"), Map.entry("ro", "romans"),
            Map.entry("rev", "revelation"), Map.entry("re", "revelation"));
    private static final Pattern SCRIPTURE_REFERENCE = Pattern.compile(
            "\\b(?<book>(?:[1-3]\\s*)?[a-z]+)\\.?\\s*(?<chapter>\\d{1,3})(?::(?<start>\\d{1,3})(?:\\s*-\\s*(?<end>\\d{1,3}))?)?\\b",
            Pattern.CASE_INSENSITIVE);

    private SearchNormalizer() {
    }

    public static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String lower = ascii.toLowerCase(Locale.ROOT).replace('&', ' ');
        return SPACE.matcher(NON_ALNUM.matcher(lower).replaceAll(" ").trim()).replaceAll(" ");
    }

    public static List<String> tokens(String value) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.of(normalized.split(" "));
    }

    public static String normalizeKey(String value) {
        String normalized = normalizeText(value).replace(" sharp", "#").replace(" flat", "b");
        return normalized.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    public static List<NormalizedScriptureReference> parseScriptureReferences(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        Matcher matcher = SCRIPTURE_REFERENCE.matcher(ascii);
        List<NormalizedScriptureReference> references = new ArrayList<>();
        while (matcher.find()) {
            String book = normalizeBook(matcher.group("book"));
            int chapter = Integer.parseInt(matcher.group("chapter"));
            Integer start = matcher.group("start") == null ? null : Integer.parseInt(matcher.group("start"));
            Integer end = null;
            if (matcher.group("end") != null) {
                end = Integer.parseInt(matcher.group("end"));
            } else if (start != null) {
                end = start;
            }
            references.add(new NormalizedScriptureReference(book, chapter, start, end));
        }
        return references;
    }

    static String normalizeBook(String value) {
        String normalized = normalizeText(value).replace(" ", "");
        String numericPrefix = "";
        if (!normalized.isEmpty() && Character.isDigit(normalized.charAt(0))) {
            numericPrefix = normalized.substring(0, 1) + " ";
            normalized = normalized.substring(1);
        }
        return numericPrefix + SCRIPTURE_BOOKS.getOrDefault(normalized, normalized);
    }
}
