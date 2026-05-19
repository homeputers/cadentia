package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeterministicLyricsParser implements LyricsParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARSER_VERSION = "adr-009-v1";
    private static final Pattern CHORDPRO_SECTION_DIRECTIVE = Pattern.compile(
            "^\\{start_of_(verse|chorus|bridge|tag|pre_chorus)(?::\\s*([^}]+))?}$");
    private static final Pattern CHORDPRO_END_DIRECTIVE = Pattern.compile("^\\{end_of_[^}]+}$");
    private static final Pattern CHORDPRO_DIRECTIVE = Pattern.compile("^\\{([^}:]+)(?::\\s*([^}]+))?}$");
    private static final Pattern MARKDOWN_HEADER = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern PLAIN_SECTION_LABEL = Pattern.compile(
            "^(verse|chorus|bridge|tag|pre[- ]?chorus)(?:\\s+\\d+)?\\s*:?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONSONG_SECTION_LABEL = Pattern.compile("^\\[(.+)]\\s*$");
    private static final Pattern PLAIN_REPEAT_MARKER = Pattern.compile("^(x\\d+|repeat(?:\\s+x?\\d+)?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHORD = Pattern.compile("\\[([^]\\r\\n]+)]");
    private static final Pattern CHORD_ROOT = Pattern.compile("^([A-G](?:#|b)?)");
    private static final Pattern NORMALIZED_CHORD = Pattern.compile(
            "^[A-G](?:#|b)?(?:m|maj|min|dim|aug|sus|add)?\\d*(?:\\([^)]*\\))?(?:/[A-G](?:#|b)?)?$");

    private final LyricsFormat format;

    private DeterministicLyricsParser(LyricsFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
        this.format = format;
    }

    public static DeterministicLyricsParser forFormat(LyricsFormat format) {
        return new DeterministicLyricsParser(format);
    }

    @Override
    public LyricsFormat format() {
        return format;
    }

    @Override
    public String parserName() {
        return "deterministic-" + format.storageValue() + "-parser";
    }

    @Override
    public String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    public LyricsParseResult parse(String content) {
        if (content == null || content.isBlank()) {
            return LyricsParseResult.failed(parserName(), parserVersion(), "content is required for derived parsing");
        }
        ParsedAccumulator accumulator = new ParsedAccumulator();
        parseLines(content, accumulator);
        accumulator.closeSection(accumulator.lineNumber());
        accumulator.finalizeMusicalAnalysis();
        return LyricsParseResult.parsed(
                parserName(),
                parserVersion(),
                toJson(accumulator.sections()),
                toJson(accumulator.chords()),
                toJson(accumulator.markers()));
    }

    private void parseLines(String content, ParsedAccumulator accumulator) {
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            int lineNumber = index + 1;
            String line = lines[index];
            accumulator.lineNumber(lineNumber);
            parseStructuralMarker(line, lineNumber, accumulator);
            parseMusicalMetadata(line, lineNumber, accumulator);
            parseChords(line, lineNumber, accumulator);
            if (!isMarkerOnlyLine(line)) {
                accumulator.addLyricLine(line, lineNumber);
            }
        }
    }

    private void parseMusicalMetadata(String line, int lineNumber, ParsedAccumulator accumulator) {
        String trimmed = line.trim();
        if (trimmed.isBlank()) {
            return;
        }
        if (format == LyricsFormat.CHORDPRO) {
            Matcher directive = CHORDPRO_DIRECTIVE.matcher(trimmed);
            if (directive.matches()) {
                accumulator.recordMetadata(directive.group(1), directive.group(2), lineNumber);
            }
            return;
        }
        int separator = trimmed.indexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            return;
        }
        String key = trimmed.substring(0, separator).trim();
        String value = trimmed.substring(separator + 1).trim();
        accumulator.recordMetadata(key, value, lineNumber);
    }

    private void parseStructuralMarker(String line, int lineNumber, ParsedAccumulator accumulator) {
        Matcher chordProSection = CHORDPRO_SECTION_DIRECTIVE.matcher(line.trim());
        if (format == LyricsFormat.CHORDPRO && chordProSection.matches()) {
            String label = sectionLabel(chordProSection.group(1), chordProSection.group(2));
            accumulator.addMarker("section_start", label, lineNumber);
            accumulator.startSection(label, lineNumber);
            return;
        }
        if (format == LyricsFormat.CHORDPRO && CHORDPRO_END_DIRECTIVE.matcher(line.trim()).matches()) {
            accumulator.addMarker("section_end", line.trim(), lineNumber);
            accumulator.closeSection(lineNumber);
            return;
        }
        Matcher chordProDirective = CHORDPRO_DIRECTIVE.matcher(line.trim());
        if (format == LyricsFormat.CHORDPRO && chordProDirective.matches()) {
            accumulator.addMarker("directive", chordProDirective.group(1), lineNumber);
            return;
        }
        Matcher markdownHeader = MARKDOWN_HEADER.matcher(line.trim());
        if (format == LyricsFormat.MARKDOWN && markdownHeader.matches()) {
            String label = markdownHeader.group(2).trim();
            accumulator.addMarker("heading", label, lineNumber);
            accumulator.startSection(label, lineNumber);
            return;
        }
        Matcher plainLabel = PLAIN_SECTION_LABEL.matcher(line.trim());
        if (format == LyricsFormat.PLAIN_TEXT && plainLabel.matches()) {
            String label = line.trim().replaceAll(":$", "");
            accumulator.addMarker("section_label", label, lineNumber);
            accumulator.startSection(label, lineNumber);
            return;
        }
        Matcher onSongLabel = ONSONG_SECTION_LABEL.matcher(line.trim());
        if (format == LyricsFormat.ONSONG && onSongLabel.matches()) {
            String label = onSongLabel.group(1).trim();
            accumulator.addMarker("section_label", label, lineNumber);
            accumulator.startSection(label, lineNumber);
            return;
        }
        if ((format == LyricsFormat.PLAIN_TEXT || format == LyricsFormat.ONSONG)
                && PLAIN_REPEAT_MARKER.matcher(line.trim()).matches()) {
            accumulator.addMarker("repeat_hint", line.trim(), lineNumber);
            return;
        }
        if ((format == LyricsFormat.CHORDPRO || format == LyricsFormat.MARKDOWN || format == LyricsFormat.ONSONG)
                && line.contains("[") && !line.contains("]")) {
            accumulator.addMarker("warning_malformed_marker", line.trim(), lineNumber);
        }
    }

    private void parseChords(String line, int lineNumber, ParsedAccumulator accumulator) {
        if (format != LyricsFormat.CHORDPRO && format != LyricsFormat.MARKDOWN && format != LyricsFormat.ONSONG) {
            return;
        }
        Matcher matcher = CHORD.matcher(line);
        while (matcher.find()) {
            String rawChord = matcher.group(1).trim();
            String normalizedChord = normalizeChord(rawChord);
            accumulator.addChord(rawChord, normalizedChord, lineNumber, matcher.start());
            if (normalizedChord != null) {
                accumulator.recordChordRoot(normalizedChord);
            }
            if (normalizedChord == null) {
                accumulator.addMarker("warning_unknown_chord", rawChord, lineNumber);
            }
        }
    }

    private boolean isMarkerOnlyLine(String line) {
        String trimmed = line.trim();
        if (format == LyricsFormat.CHORDPRO) {
            return CHORDPRO_SECTION_DIRECTIVE.matcher(trimmed).matches()
                    || CHORDPRO_END_DIRECTIVE.matcher(trimmed).matches()
                    || CHORDPRO_DIRECTIVE.matcher(trimmed).matches();
        }
        return format == LyricsFormat.MARKDOWN && MARKDOWN_HEADER.matcher(trimmed).matches();
    }

    private String normalizeChord(String source) {
        if (!NORMALIZED_CHORD.matcher(source).matches()) {
            return null;
        }
        return source.replace("min", "m").replace("maj", "M");
    }

    private String sectionLabel(String directiveName, String explicitLabel) {
        if (explicitLabel != null && !explicitLabel.isBlank()) {
            return explicitLabel.trim();
        }
        return directiveName.replace('_', ' ');
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize derived lyrics structure", exception);
        }
    }

    private static final class ParsedAccumulator {
        private final List<Map<String, Object>> sections = new ArrayList<>();
        private final List<Map<String, Object>> chords = new ArrayList<>();
        private final List<Map<String, Object>> markers = new ArrayList<>();
        private final List<String> declaredKeys = new ArrayList<>();
        private final List<Integer> declaredBpms = new ArrayList<>();
        private final List<String> declaredMeters = new ArrayList<>();
        private final List<String> chordRoots = new ArrayList<>();
        private static final double NASHVILLE_KEY_CONFIDENCE_THRESHOLD = 0.5;
        private static final Map<String, Integer> NOTE_TO_SEMITONE = buildNoteToSemitone();
        private String currentLabel;
        private int currentStartLine;
        private final List<String> currentLines = new ArrayList<>();
        private int lineNumber;

        List<Map<String, Object>> sections() {
            return sections;
        }

        List<Map<String, Object>> chords() {
            return chords;
        }

        List<Map<String, Object>> markers() {
            return markers;
        }

        int lineNumber() {
            return lineNumber;
        }

        void lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        void startSection(String label, int lineNumber) {
            closeSection(lineNumber - 1);
            currentLabel = label;
            currentStartLine = lineNumber;
            currentLines.clear();
        }

        void addLyricLine(String line, int lineNumber) {
            if (currentLabel == null && !line.isBlank()) {
                startSection("body", lineNumber);
            }
            if (currentLabel != null) {
                currentLines.add(line);
            }
        }

        void closeSection(int endLine) {
            if (currentLabel == null) {
                return;
            }
            sections.add(Map.of(
                    "label", currentLabel,
                    "startLine", currentStartLine,
                    "endLine", Math.max(currentStartLine, endLine),
                    "lines", List.copyOf(currentLines)));
            currentLabel = null;
            currentLines.clear();
        }

        void addChord(String sourceChord, String normalizedChord, int lineNumber, int characterOffset) {
            Map<String, Object> chord = new HashMap<>();
            chord.put("sourceChord", sourceChord);
            chord.put("normalizedChord", normalizedChord == null ? sourceChord : normalizedChord);
            chord.put("isNormalized", normalizedChord != null);
            chord.put("line", lineNumber);
            chord.put("characterOffset", characterOffset);
            chords.add(chord);
        }

        void addMarker(String type, String label, int lineNumber) {
            markers.add(Map.of("type", type, "label", label, "line", lineNumber));
        }

        void recordMetadata(String fieldName, String value, int lineNumber) {
            if (fieldName == null || value == null || value.isBlank()) {
                return;
            }
            String normalizedField = fieldName.trim().toLowerCase();
            if (normalizedField.equals("key")) {
                declaredKeys.add(value);
                return;
            }
            if (normalizedField.equals("tempo") || normalizedField.equals("bpm")) {
                String digits = value.replaceAll("[^0-9]", "");
                if (!digits.isBlank()) {
                    declaredBpms.add(Integer.parseInt(digits));
                }
                return;
            }
            if (normalizedField.equals("time") || normalizedField.equals("meter") || normalizedField.equals("timesig")) {
                declaredMeters.add(value);
            }
        }

        void recordChordRoot(String normalizedChord) {
            Matcher root = CHORD_ROOT.matcher(normalizedChord);
            if (root.find()) {
                chordRoots.add(root.group(1));
            }
        }

        void finalizeMusicalAnalysis() {
            KeyAnalysis keyAnalysis = addKeyAnalysis();
            addBpmAnalysis();
            addMeterAnalysis();
            addNashvilleNumbers(keyAnalysis);
            addFingerprints();
        }

        private void addNashvilleNumbers(KeyAnalysis keyAnalysis) {
            if (!keyAnalysis.supportedForNashville()) {
                markers.add(Map.of(
                        "type", "warning_nashville_conversion_skipped",
                        "label", "key unavailable or low confidence",
                        "line", 0,
                        "confidence", keyAnalysis.confidence,
                        "keyEvidence", keyAnalysis.evidence));
                return;
            }
            chords.sort(Comparator.<Map<String, Object>>comparingInt(chord -> (Integer) chord.get("line"))
                    .thenComparingInt(chord -> (Integer) chord.get("characterOffset")));
            for (Map<String, Object> chord : chords) {
                String normalizedChord = String.valueOf(chord.get("normalizedChord"));
                String nashville = toNashville(normalizedChord, keyAnalysis);
                if (nashville == null) {
                    markers.add(Map.of(
                            "type", "warning_unsupported_nashville_chord",
                            "label", String.valueOf(chord.get("sourceChord")),
                            "line", chord.get("line"),
                            "characterOffset", chord.get("characterOffset")));
                } else {
                    chord.put("nashvilleNumber", nashville);
                    chord.put("nashvilleConfidence", keyAnalysis.confidence);
                }
            }
        }

        private void addFingerprints() {
            String rawFingerprint = hash("raw-source", joinLyricLines(false));
            String lyricsFingerprint = hash("lyrics-normalized", joinLyricLines(true));
            String chordFingerprint = hash("chord-progression", joinedNormalizedChords());
            String sectionFingerprint = hash("section-sequence", joinedSectionLabels());
            String relativeMovementFingerprint = hash("chord-movement", keyIndependentChordMovement());

            markers.add(Map.of(
                    "type", "fingerprint",
                    "field", "raw_source",
                    "value", rawFingerprint,
                    "signal", "hash(raw-content)"));
            markers.add(Map.of(
                    "type", "fingerprint",
                    "field", "lyrics_normalized",
                    "value", lyricsFingerprint,
                    "signal", "hash(normalized-lyrics)"));
            markers.add(Map.of(
                    "type", "fingerprint",
                    "field", "chord_progression",
                    "value", chordFingerprint,
                    "signal", "hash(normalized-chord-sequence)"));
            markers.add(Map.of(
                    "type", "fingerprint",
                    "field", "section_sequence",
                    "value", sectionFingerprint,
                    "signal", "hash(section-label-order)"));
            markers.add(Map.of(
                    "type", "fingerprint",
                    "field", "key_independent_chord_movement",
                    "value", relativeMovementFingerprint,
                    "signal", "hash(relative-semitone-movement)"));
            markers.add(Map.of(
                    "type", "duplicate_support",
                    "field", "fingerprint_bundle",
                    "value", Map.of(
                            "rawSource", rawFingerprint,
                            "lyricsNormalized", lyricsFingerprint,
                            "chordProgression", chordFingerprint,
                            "sectionSequence", sectionFingerprint,
                            "keyIndependentChordMovement", relativeMovementFingerprint),
                    "evidence", "deterministic_parser_signals"));
        }

        private String joinedNormalizedChords() {
            StringJoiner joiner = new StringJoiner("|");
            for (Map<String, Object> chord : chords) {
                joiner.add(String.valueOf(chord.get("normalizedChord")));
            }
            return joiner.toString();
        }

        private String joinedSectionLabels() {
            StringJoiner joiner = new StringJoiner("|");
            for (Map<String, Object> section : sections) {
                joiner.add(String.valueOf(section.get("label")).toLowerCase(Locale.ROOT).trim());
            }
            return joiner.toString();
        }

        private String joinLyricLines(boolean normalize) {
            StringJoiner joiner = new StringJoiner("\n");
            for (Map<String, Object> section : sections) {
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) section.get("lines");
                for (String line : lines) {
                    if (normalize) {
                        String normalizedLine = line
                                .replaceAll("\\[[^\\]\\r\\n]+]", "")
                                .replaceAll("\\s+", " ")
                                .trim()
                                .toLowerCase(Locale.ROOT);
                        joiner.add(normalizedLine);
                    } else {
                        joiner.add(line);
                    }
                }
            }
            return joiner.toString();
        }

        private String keyIndependentChordMovement() {
            List<Integer> notes = new ArrayList<>();
            for (Map<String, Object> chord : chords) {
                String normalizedChord = String.valueOf(chord.get("normalizedChord"));
                Integer note = rootToSemitone(normalizedChord);
                if (note != null) {
                    notes.add(note);
                }
            }
            if (notes.size() < 2) {
                return "none";
            }
            StringJoiner joiner = new StringJoiner("|");
            for (int index = 1; index < notes.size(); index++) {
                int interval = Math.floorMod(notes.get(index) - notes.get(index - 1), 12);
                joiner.add(Integer.toString(interval));
            }
            return joiner.toString();
        }

        private String toNashville(String normalizedChord, KeyAnalysis keyAnalysis) {
            Matcher rootMatcher = CHORD_ROOT.matcher(normalizedChord);
            if (!rootMatcher.find()) {
                return null;
            }
            Integer chordRoot = NOTE_TO_SEMITONE.get(rootMatcher.group(1));
            if (chordRoot == null) {
                return null;
            }
            int interval = Math.floorMod(chordRoot - keyAnalysis.tonicSemitone, 12);
            String degree = degreeForInterval(interval, keyAnalysis.minorMode);
            if (degree == null) {
                return null;
            }
            int slashIndex = normalizedChord.indexOf('/');
            if (slashIndex < 0 || slashIndex >= normalizedChord.length() - 1) {
                return degree;
            }
            String bass = normalizedChord.substring(slashIndex + 1);
            Integer bassRoot = NOTE_TO_SEMITONE.get(bass);
            if (bassRoot == null) {
                return null;
            }
            String bassDegree = degreeForInterval(Math.floorMod(bassRoot - keyAnalysis.tonicSemitone, 12), keyAnalysis.minorMode);
            return bassDegree == null ? null : degree + "/" + bassDegree;
        }

        private String degreeForInterval(int interval, boolean minorMode) {
            if (minorMode) {
                return switch (interval) {
                    case 0 -> "1m";
                    case 2 -> "2";
                    case 3 -> "b3";
                    case 5 -> "4";
                    case 7 -> "5";
                    case 8 -> "b6";
                    case 10 -> "b7";
                    default -> null;
                };
            }
            return switch (interval) {
                case 0 -> "1";
                case 2 -> "2";
                case 4 -> "3";
                case 5 -> "4";
                case 7 -> "5";
                case 9 -> "6";
                case 11 -> "7";
                default -> null;
            };
        }

        private Integer rootToSemitone(String normalizedChord) {
            Matcher root = CHORD_ROOT.matcher(normalizedChord);
            if (!root.find()) {
                return null;
            }
            return NOTE_TO_SEMITONE.get(root.group(1));
        }

        private String hash(String prefix, String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String hashed = HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
                return prefix + ":sha256:" + hashed;
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 hashing unavailable", exception);
            }
        }

        private KeyAnalysis addKeyAnalysis() {
            if (!declaredKeys.isEmpty()) {
                String first = declaredKeys.get(0);
                markers.add(Map.of("type", "analysis", "field", "key", "value", first, "confidence", 0.98, "evidence", "explicit_metadata"));
                if (declaredKeys.stream().distinct().count() > 1) {
                    markers.add(Map.of("type", "warning_conflicting_key_metadata", "label", String.join(", ", declaredKeys), "line", 0));
                }
                return KeyAnalysis.from(first, 0.98, "explicit_metadata");
            }
            if (chordRoots.isEmpty()) {
                markers.add(Map.of("type", "analysis", "field", "key", "value", "unknown", "confidence", 0.0, "evidence", "missing_data"));
                return KeyAnalysis.unknown(0.0, "missing_data");
            }
            String inferred = chordRoots.get(0);
            long distinctRoots = chordRoots.stream().distinct().count();
            double confidence = distinctRoots <= 2 ? 0.55 : 0.32;
            String evidence = distinctRoots <= 2 ? "inferred_from_chords" : "ambiguous_chord_distribution";
            markers.add(Map.of("type", "analysis", "field", "key", "value", inferred, "confidence", confidence, "evidence", evidence));
            return KeyAnalysis.from(inferred, confidence, evidence);
        }

        private void addBpmAnalysis() {
            if (!declaredBpms.isEmpty()) {
                int bpm = declaredBpms.get(0);
                markers.add(Map.of("type", "analysis", "field", "bpm", "value", bpm, "confidence", 0.95, "evidence", "explicit_metadata"));
                if (declaredBpms.stream().distinct().count() > 1) {
                    markers.add(Map.of("type", "warning_conflicting_bpm_metadata", "label", declaredBpms.toString(), "line", 0));
                }
                return;
            }
            markers.add(Map.of("type", "analysis", "field", "bpm", "value", "unknown", "confidence", 0.0, "evidence", "missing_data"));
        }

        private void addMeterAnalysis() {
            if (!declaredMeters.isEmpty()) {
                markers.add(Map.of("type", "analysis", "field", "meter", "value", declaredMeters.get(0), "confidence", 0.92, "evidence", "explicit_metadata"));
                return;
            }
            markers.add(Map.of("type", "analysis", "field", "meter", "value", "unknown", "confidence", 0.0, "evidence", "missing_data"));
        }

        private static Map<String, Integer> buildNoteToSemitone() {
            Map<String, Integer> notes = new HashMap<>();
            notes.put("C", 0);
            notes.put("C#", 1);
            notes.put("Db", 1);
            notes.put("D", 2);
            notes.put("D#", 3);
            notes.put("Eb", 3);
            notes.put("E", 4);
            notes.put("F", 5);
            notes.put("F#", 6);
            notes.put("Gb", 6);
            notes.put("G", 7);
            notes.put("G#", 8);
            notes.put("Ab", 8);
            notes.put("A", 9);
            notes.put("A#", 10);
            notes.put("Bb", 10);
            notes.put("B", 11);
            return Map.copyOf(notes);
        }

        private record KeyAnalysis(int tonicSemitone, boolean minorMode, double confidence, String evidence, boolean known) {
            static KeyAnalysis from(String key, double confidence, String evidence) {
                if (key == null || key.isBlank()) {
                    return unknown(confidence, evidence);
                }
                String normalized = key.trim();
                boolean minor = normalized.endsWith("m");
                String tonic = minor ? normalized.substring(0, normalized.length() - 1) : normalized;
                Integer semitone = NOTE_TO_SEMITONE.get(tonic);
                return semitone == null ? unknown(confidence, evidence) : new KeyAnalysis(semitone, minor, confidence, evidence, true);
            }

            static KeyAnalysis unknown(double confidence, String evidence) {
                return new KeyAnalysis(-1, false, confidence, evidence, false);
            }

            boolean supportedForNashville() {
                return known && confidence >= NASHVILLE_KEY_CONFIDENCE_THRESHOLD;
            }
        }
    }
}
