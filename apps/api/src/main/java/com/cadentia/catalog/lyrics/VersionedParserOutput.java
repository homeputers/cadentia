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
        parserName = ParserOutputValidation.requireText(parserName, "parserName");
        parserVersion = ParserOutputValidation.requireText(parserVersion, "parserVersion");
        outputSchemaVersion = ParserOutputValidation.requireText(outputSchemaVersion, "outputSchemaVersion");
        sourceContentHash = ParserOutputValidation.requireText(sourceContentHash, "sourceContentHash");
        sectionsJson = ParserOutputValidation.requireText(sectionsJson, "sectionsJson");
        chordMapJson = ParserOutputValidation.requireText(chordMapJson, "chordMapJson");
        structuralMarkersJson = ParserOutputValidation.requireText(structuralMarkersJson, "structuralMarkersJson");
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        confidence = List.copyOf(confidence == null ? List.of() : confidence);
    }
}
