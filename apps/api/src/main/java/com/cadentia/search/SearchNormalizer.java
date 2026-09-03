package com.cadentia.search;

import com.cadentia.catalog.scripture.ScriptureReferenceParser;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9#]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE = Pattern.compile("\\s+");

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
        return ScriptureReferenceParser.parse(value).stream()
                .filter(reference -> reference.chapter() != null)
                .map(reference -> new NormalizedScriptureReference(
                        reference.book(), reference.chapter(), reference.startVerse(), reference.endVerse()))
                .toList();
    }
}
