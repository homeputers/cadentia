package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeterministicLyricsParser implements LyricsParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARSER_VERSION = "adr-004-v1";
    private static final Pattern CHORDPRO_SECTION_DIRECTIVE = Pattern.compile(
            "^\\{start_of_(verse|chorus|bridge|tag|pre_chorus)(?::\\s*([^}]+))?}$");
    private static final Pattern CHORDPRO_END_DIRECTIVE = Pattern.compile("^\\{end_of_[^}]+}$");
    private static final Pattern CHORDPRO_DIRECTIVE = Pattern.compile("^\\{([^}:]+)(?::\\s*([^}]+))?}$");
    private static final Pattern MARKDOWN_HEADER = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern PLAIN_SECTION_LABEL = Pattern.compile(
            "^(verse|chorus|bridge|tag|pre[- ]?chorus)(?:\\s+\\d+)?\\s*:?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHORD = Pattern.compile("\\[([^]\\r\\n]+)]");

    private final LyricsFormat format;

    private DeterministicLyricsParser(LyricsFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("format is required");
        }
        this.format = format;
    }

    public static DeterministicLyricsParser forFormat(LyricsFormat format) {
        if (format == LyricsFormat.ONSONG) {
            throw new IllegalArgumentException("OnSong parser is not implemented");
        }
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
            parseChords(line, lineNumber, accumulator);
            if (!isMarkerOnlyLine(line)) {
                accumulator.addLyricLine(line, lineNumber);
            }
        }
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
        }
    }

    private void parseChords(String line, int lineNumber, ParsedAccumulator accumulator) {
        if (format != LyricsFormat.CHORDPRO && format != LyricsFormat.MARKDOWN) {
            return;
        }
        Matcher matcher = CHORD.matcher(line);
        while (matcher.find()) {
            accumulator.addChord(matcher.group(1), lineNumber, matcher.start());
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

        void addChord(String chord, int lineNumber, int characterOffset) {
            chords.add(Map.of("chord", chord, "line", lineNumber, "characterOffset", characterOffset));
        }

        void addMarker(String type, String label, int lineNumber) {
            markers.add(Map.of("type", type, "label", label, "line", lineNumber));
        }
    }
}
