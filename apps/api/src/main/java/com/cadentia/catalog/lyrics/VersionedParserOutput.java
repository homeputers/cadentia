package com.cadentia.catalog.lyrics;

import java.util.List;

public record VersionedParserOutput(
        String parserName,
        String parserVersion,
        String outputSchemaVersion,
        String sourceContentHash,
        String sectionsJson,
        String chordMapJson,
        String structuralMarkersJson,
        List<ParserWarning> warnings,
        List<ParserConfidence> confidence) {

    public VersionedParserOutput {
        parserName = requireText(parserName, "parserName");
        parserVersion = requireText(parserVersion, "parserVersion");
        outputSchemaVersion = requireText(outputSchemaVersion, "outputSchemaVersion");
        sourceContentHash = requireText(sourceContentHash, "sourceContentHash");
        sectionsJson = requireText(sectionsJson, "sectionsJson");
        chordMapJson = requireText(chordMapJson, "chordMapJson");
        structuralMarkersJson = requireText(structuralMarkersJson, "structuralMarkersJson");
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        confidence = List.copyOf(confidence == null ? List.of() : confidence);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
