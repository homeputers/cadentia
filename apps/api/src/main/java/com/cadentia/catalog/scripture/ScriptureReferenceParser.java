package com.cadentia.catalog.scripture;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser that normalizes free-text scripture references (including common book
 * abbreviations, numbered books, and verse ranges) into {@link CanonicalScriptureReference}s.
 * Unparsable input yields an empty list; the parser never throws on user-provided text.
 */
public final class ScriptureReferenceParser {

    private static final Pattern SCRIPTURE_REFERENCE = Pattern.compile(
            "\\b(?<book>(?:[1-3]\\s*)?[a-z]+)[.\\s\\-]*(?<chapter>\\d{1,3})"
                    + "(?:[\\s:\\-]+(?<start>\\d{1,3})(?:\\s*[-–—\\s]\\s*(?<end>\\d{1,3}))?)?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> BOOK_ALIASES = buildBookAliases();

    private ScriptureReferenceParser() {
    }

    public static List<CanonicalScriptureReference> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        Matcher matcher = SCRIPTURE_REFERENCE.matcher(ascii);
        List<CanonicalScriptureReference> references = new ArrayList<>();
        while (matcher.find()) {
            String book = normalizeBook(matcher.group("book"));
            if (book == null) {
                continue;
            }
            Integer chapter = Integer.valueOf(matcher.group("chapter"));
            Integer start = matcher.group("start") == null ? null : Integer.valueOf(matcher.group("start"));
            Integer end;
            if (matcher.group("end") != null) {
                end = Integer.valueOf(matcher.group("end"));
            } else {
                end = start;
            }
            if (start != null && end != null && end < start) {
                end = start;
            }
            references.add(new CanonicalScriptureReference(book, chapter, start, end));
        }
        if (references.isEmpty()) {
            String trimmed = value.trim();
            if (trimmed.matches("(?i)(?:[1-3]\\s*)?[a-zA-Z][a-zA-Z ]*")) {
                String bookOnly = normalizeBook(trimmed);
                if (bookOnly != null) {
                    references.add(new CanonicalScriptureReference(bookOnly, null, null, null));
                }
            }
        }
        return references;
    }

    static String normalizeBook(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        if (normalized.isEmpty()) {
            return null;
        }
        String canonical = BOOK_ALIASES.get(normalized);
        if (canonical != null) {
            return canonical;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            String base = BOOK_ALIASES.get(normalized.substring(1));
            if (base != null && !Character.isDigit(base.charAt(0))) {
                return normalized.charAt(0) + " " + base;
            }
        }
        return null;
    }

    private static Map<String, String> buildBookAliases() {
        Map<String, String> aliases = new java.util.HashMap<>();
        register(aliases, "genesis", "genesis", "gen", "ge", "gn");
        register(aliases, "exodus", "exodus", "ex", "exo", "exod");
        register(aliases, "leviticus", "leviticus", "lev", "lv");
        register(aliases, "numbers", "numbers", "num", "nm", "nu");
        register(aliases, "deuteronomy", "deuteronomy", "deut", "dt");
        register(aliases, "joshua", "joshua", "josh", "jos");
        register(aliases, "judges", "judges", "judg", "jdg");
        register(aliases, "ruth", "ruth", "ru");
        register(aliases, "1 samuel", "1samuel");
        register(aliases, "2 samuel", "2samuel");
        register(aliases, "samuel", "samuel", "sam");
        register(aliases, "1 kings", "1kings");
        register(aliases, "2 kings", "2kings");
        register(aliases, "kings", "kings", "kgs");
        register(aliases, "1 chronicles", "1chronicles");
        register(aliases, "2 chronicles", "2chronicles");
        register(aliases, "chronicles", "chronicles", "chr");
        register(aliases, "ezra", "ezra", "ezr");
        register(aliases, "nehemiah", "nehemiah", "neh");
        register(aliases, "esther", "esther", "est");
        register(aliases, "job", "job", "jb");
        register(aliases, "psalms", "psalms", "psalm", "ps", "psa", "psm");
        register(aliases, "proverbs", "proverbs", "prov", "prv");
        register(aliases, "ecclesiastes", "ecclesiastes", "eccl", "ecc");
        register(aliases, "song of solomon", "songofsolomon", "songofsongs", "solomon", "sos");
        register(aliases, "isaiah", "isaiah", "isa");
        register(aliases, "jeremiah", "jeremiah", "jer");
        register(aliases, "lamentations", "lamentations", "lam");
        register(aliases, "ezekiel", "ezekiel", "ezek", "ezk");
        register(aliases, "daniel", "daniel", "dan", "dn");
        register(aliases, "hosea", "hosea", "hos");
        register(aliases, "joel", "joel", "jl");
        register(aliases, "amos", "amos", "am");
        register(aliases, "obadiah", "obadiah", "obad", "ob");
        register(aliases, "jonah", "jonah", "jon");
        register(aliases, "micah", "micah", "mic");
        register(aliases, "nahum", "nahum", "nah");
        register(aliases, "habakkuk", "habakkuk", "hab");
        register(aliases, "zephaniah", "zephaniah", "zeph", "zep");
        register(aliases, "haggai", "haggai", "hag");
        register(aliases, "zechariah", "zechariah", "zech", "zec");
        register(aliases, "malachi", "malachi", "mal");
        register(aliases, "matthew", "matthew", "matt", "mt");
        register(aliases, "mark", "mark", "mk");
        register(aliases, "luke", "luke", "lk");
        register(aliases, "john", "john", "jn", "jhn");
        register(aliases, "acts", "acts", "act");
        register(aliases, "romans", "romans", "rom", "ro");
        register(aliases, "1 corinthians", "1corinthians", "1cor");
        register(aliases, "2 corinthians", "2corinthians", "2cor");
        register(aliases, "corinthians", "corinthians", "cor");
        register(aliases, "galatians", "galatians", "gal");
        register(aliases, "ephesians", "ephesians", "eph");
        register(aliases, "philippians", "philippians", "phil", "php", "philipp");
        register(aliases, "colossians", "colossians", "col");
        register(aliases, "1 thessalonians", "1thessalonians", "1thess", "1thes");
        register(aliases, "2 thessalonians", "2thessalonians", "2thess", "2thes");
        register(aliases, "thessalonians", "thessalonians", "thess", "thes");
        register(aliases, "1 timothy", "1timothy", "1tim");
        register(aliases, "2 timothy", "2timothy", "2tim");
        register(aliases, "timothy", "timothy", "tim");
        register(aliases, "titus", "titus", "tit");
        register(aliases, "philemon", "philemon", "philem", "phlm");
        register(aliases, "hebrews", "hebrews", "heb");
        register(aliases, "james", "james", "jas", "jms");
        register(aliases, "1 peter", "1peter", "1pet");
        register(aliases, "2 peter", "2peter", "2pet");
        register(aliases, "peter", "peter", "pet");
        register(aliases, "1 john", "1john", "1jn");
        register(aliases, "2 john", "2john", "2jn");
        register(aliases, "3 john", "3john", "3jn");
        register(aliases, "jude", "jude");
        register(aliases, "revelation", "revelation", "rev", "re");
        return Map.copyOf(aliases);
    }

    private static void register(Map<String, String> aliases, String canonical, String... keys) {
        for (String key : keys) {
            String normalizedKey = key.replaceAll("[^a-z0-9]+", "");
            aliases.put(normalizedKey, canonical);
        }
    }
}
