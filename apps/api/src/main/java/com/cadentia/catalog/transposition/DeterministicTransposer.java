package com.cadentia.catalog.transposition;

import com.cadentia.catalog.model.KeyMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeterministicTransposer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<LinkedHashMap<String, Object>>> CHORD_MAP_TYPE = new TypeReference<>() {
    };
    private static final String[] SHARP_SPELLINGS = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    private static final String[] FLAT_SPELLINGS = {
        "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    };
    private static final Map<String, Integer> SUPPORTED_PITCH_CLASSES = Map.ofEntries(
            Map.entry("C", 0),
            Map.entry("C#", 1),
            Map.entry("Db", 1),
            Map.entry("D", 2),
            Map.entry("D#", 3),
            Map.entry("Eb", 3),
            Map.entry("E", 4),
            Map.entry("F", 5),
            Map.entry("F#", 6),
            Map.entry("Gb", 6),
            Map.entry("G", 7),
            Map.entry("G#", 8),
            Map.entry("Ab", 8),
            Map.entry("A", 9),
            Map.entry("A#", 10),
            Map.entry("Bb", 10),
            Map.entry("B", 11));
    private static final Set<String> SHARP_NATURAL_TARGETS = Set.of("C", "G", "D", "A", "E", "B");
    private static final Set<String> FLAT_NATURAL_TARGETS = Set.of("F");
    private static final Pattern CHORD_TOKEN = Pattern.compile("^([A-G](?:#|b)?)([^/\\s]*)(?:/([A-G](?:#|b)?))?$");
    private static final Pattern BRACKETED_TOKEN = Pattern.compile("\\[([^]\\r\\n]+)]");
    private static final Pattern SECTION_LABEL = Pattern.compile(
            "(?i)^(intro|verse|chorus|bridge|tag|pre[- ]?chorus|interlude|outro)(?:\\s+\\d+)?$");

    public int semitoneInterval(MusicalKey baseKey, MusicalKey targetKey) {
        int basePitchClass = pitchClassForKey(baseKey, "base key");
        int targetPitchClass = pitchClassForKey(targetKey, "target key");
        return Math.floorMod(targetPitchClass - basePitchClass, 12);
    }

    public String transposeChord(MusicalKey baseKey, MusicalKey targetKey, String chordSymbol) {
        if (chordSymbol == null || chordSymbol.isBlank()) {
            throw new TranspositionException("Chord symbol is required");
        }
        validateKey(baseKey, "base key");
        validateKey(targetKey, "target key");
        int interval = semitoneInterval(baseKey, targetKey);
        if (interval == 0) {
            parseChord(chordSymbol.trim());
            return chordSymbol.trim();
        }
        SpellingFamily spellingFamily = spellingFamilyFor(targetKey.tonic());
        ParsedChord parsedChord = parseChord(chordSymbol.trim());
        String transposedRoot = spellPitchClass(parsedChord.rootPitchClass(), interval, spellingFamily);
        if (parsedChord.bassPitchClass() == null) {
            return transposedRoot + parsedChord.qualityAndModifiers();
        }
        String transposedBass = spellPitchClass(parsedChord.bassPitchClass(), interval, spellingFamily);
        return transposedRoot + parsedChord.qualityAndModifiers() + "/" + transposedBass;
    }

    public List<String> transposeChordSymbols(MusicalKey baseKey, MusicalKey targetKey, List<String> chordSymbols) {
        if (chordSymbols == null) {
            throw new IllegalArgumentException("chordSymbols is required");
        }
        List<String> transposed = new ArrayList<>();
        for (String chordSymbol : chordSymbols) {
            transposed.add(transposeChord(baseKey, targetKey, chordSymbol));
        }
        return List.copyOf(transposed);
    }

    public String transposeChordSheet(MusicalKey baseKey, MusicalKey targetKey, String chordSheet) {
        if (chordSheet == null) {
            throw new IllegalArgumentException("chordSheet is required");
        }
        String withBracketedChords = transposeBracketedChords(baseKey, targetKey, chordSheet);
        String[] lines = withBracketedChords.split("\\R", -1);
        List<String> transposedLines = new ArrayList<>();
        for (String line : lines) {
            transposedLines.add(transposeChordOnlyLine(baseKey, targetKey, line));
        }
        return String.join("\n", transposedLines);
    }

    public String transposeChordMapJson(MusicalKey baseKey, MusicalKey targetKey, String chordMapJson) {
        if (chordMapJson == null || chordMapJson.isBlank()) {
            throw new IllegalArgumentException("chordMapJson is required");
        }
        try {
            List<LinkedHashMap<String, Object>> entries = OBJECT_MAPPER.readValue(chordMapJson, CHORD_MAP_TYPE);
            List<LinkedHashMap<String, Object>> transposedEntries = new ArrayList<>();
            for (Map<String, Object> entry : entries) {
                if (!(entry.get("chord") instanceof String chord)) {
                    throw new TranspositionException("Parsed chord map entry is missing string chord field");
                }
                LinkedHashMap<String, Object> transposedEntry = new LinkedHashMap<>(entry);
                transposedEntry.put("chord", transposeChord(baseKey, targetKey, chord));
                transposedEntries.add(transposedEntry);
            }
            return OBJECT_MAPPER.writeValueAsString(transposedEntries);
        } catch (JsonProcessingException exception) {
            throw new TranspositionException("Unable to parse chord map JSON for transposition", exception);
        }
    }

    private String transposeBracketedChords(MusicalKey baseKey, MusicalKey targetKey, String chordSheet) {
        Matcher matcher = BRACKETED_TOKEN.matcher(chordSheet);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            if (SECTION_LABEL.matcher(token).matches()) {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group()));
            } else {
                String transposed = transposeChord(baseKey, targetKey, token);
                matcher.appendReplacement(builder, Matcher.quoteReplacement("[" + transposed + "]"));
            }
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String transposeChordOnlyLine(MusicalKey baseKey, MusicalKey targetKey, String line) {
        if (line.isBlank()) {
            return line;
        }
        String leadingWhitespace = line.substring(0, line.length() - line.stripLeading().length());
        String trailingWhitespace = line.substring(line.stripTrailing().length());
        String trimmed = line.trim();
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            if (!canParseChord(token)) {
                return line;
            }
        }
        List<String> transposedTokens = new ArrayList<>();
        for (String token : tokens) {
            transposedTokens.add(transposeChord(baseKey, targetKey, token));
        }
        return leadingWhitespace + String.join(" ", transposedTokens) + trailingWhitespace;
    }

    private boolean canParseChord(String token) {
        try {
            parseChord(token);
            return true;
        } catch (TranspositionException exception) {
            return false;
        }
    }

    private ParsedChord parseChord(String chordSymbol) {
        String token = chordSymbol.trim();
        if (token.isEmpty()) {
            throw new TranspositionException("Chord symbol is required");
        }
        if (token.chars().filter(character -> character == '/').count() > 1) {
            throw unsupported(token, "multiple slash bass notes or polychords are not supported");
        }
        if (token.contains("|") || token.toLowerCase().contains(" over ")) {
            throw unsupported(token, "polychords are not supported");
        }
        if (token.matches(".*\\s+.*")) {
            throw unsupported(token, "spaces inside a chord symbol are not supported");
        }
        if (token.matches("^[0-9#b].*")) {
            throw unsupported(token, "Nashville-style notation is not supported");
        }
        if (token.matches("(?i)^(H|Do|Re|Mi).*")) {
            throw unsupported(token, "locale-specific note names are not supported");
        }
        if (token.matches("(?i)^[ivx]+.*")) {
            throw unsupported(token, "Roman-numeral analysis is not supported");
        }
        Matcher matcher = CHORD_TOKEN.matcher(token);
        if (!matcher.matches()) {
            throw unsupported(token, "unsupported chord syntax");
        }
        String root = matcher.group(1);
        String qualityAndModifiers = matcher.group(2);
        String bass = matcher.group(3);
        Integer rootPitchClass = pitchClassForToken(root, token);
        validateQuality(token, qualityAndModifiers);
        Integer bassPitchClass = bass == null ? null : pitchClassForToken(bass, token);
        return new ParsedChord(rootPitchClass, qualityAndModifiers, bassPitchClass);
    }

    private void validateQuality(String originalToken, String qualityAndModifiers) {
        if (qualityAndModifiers == null || qualityAndModifiers.isEmpty()) {
            return;
        }
        if (qualityAndModifiers.startsWith("#") || qualityAndModifiers.startsWith("b")) {
            throw unsupported(originalToken, "double accidentals and theoretical spellings are not supported");
        }
        if (!qualityAndModifiers.matches("[A-Za-z0-9()#+\\-°]*")) {
            throw unsupported(originalToken, "unsupported chord quality or modifier syntax");
        }
    }

    private Integer pitchClassForKey(MusicalKey key, String label) {
        validateKey(key, label);
        return SUPPORTED_PITCH_CLASSES.get(key.tonic());
    }

    private void validateKey(MusicalKey key, String label) {
        if (key == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (key.mode() != KeyMode.MAJOR && key.mode() != KeyMode.MINOR) {
            throw new TranspositionException(label + " mode " + key.mode() + " is not transposable");
        }
        if (!SUPPORTED_PITCH_CLASSES.containsKey(key.tonic())) {
            throw new TranspositionException(label + " tonic " + key.tonic() + " is not supported");
        }
    }

    private Integer pitchClassForToken(String token, String originalChord) {
        Integer pitchClass = SUPPORTED_PITCH_CLASSES.get(token);
        if (pitchClass == null) {
            throw unsupported(originalChord, "unsupported pitch token " + token);
        }
        return pitchClass;
    }

    private SpellingFamily spellingFamilyFor(String targetTonic) {
        if (targetTonic.contains("b")) {
            return SpellingFamily.FLAT;
        }
        if (targetTonic.contains("#")) {
            return SpellingFamily.SHARP;
        }
        if (FLAT_NATURAL_TARGETS.contains(targetTonic)) {
            return SpellingFamily.FLAT;
        }
        if (SHARP_NATURAL_TARGETS.contains(targetTonic)) {
            return SpellingFamily.SHARP;
        }
        throw new TranspositionException("target key tonic " + targetTonic + " is not supported");
    }

    private String spellPitchClass(int pitchClass, int interval, SpellingFamily spellingFamily) {
        int transposedPitchClass = Math.floorMod(pitchClass + interval, 12);
        if (spellingFamily == SpellingFamily.FLAT) {
            return FLAT_SPELLINGS[transposedPitchClass];
        }
        return SHARP_SPELLINGS[transposedPitchClass];
    }

    private TranspositionException unsupported(String token, String reason) {
        return new TranspositionException("Unsupported notation in chord '" + token + "': " + reason);
    }

    private enum SpellingFamily {
        SHARP,
        FLAT
    }

    private record ParsedChord(int rootPitchClass, String qualityAndModifiers, Integer bassPitchClass) {
    }
}
