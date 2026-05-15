package com.cadentia.catalog.model;

public record UpdateLyricsParseResultCommand(
        LyricsParseStatus parseStatus,
        String parseError,
        String parserName,
        String parserVersion,
        String parsedSectionsJson,
        String chordMapJson,
        String structuralMarkersJson) {

    public UpdateLyricsParseResultCommand {
        parseStatus = CatalogValidation.requireEnum(parseStatus, "parseStatus");
        if (parseStatus == LyricsParseStatus.PARSED) {
            parserName = CatalogValidation.requireText(parserName, "parserName");
            parserVersion = CatalogValidation.requireText(parserVersion, "parserVersion");
            parsedSectionsJson = CatalogValidation.requireText(parsedSectionsJson, "parsedSectionsJson");
            chordMapJson = CatalogValidation.requireText(chordMapJson, "chordMapJson");
            structuralMarkersJson = CatalogValidation.requireText(structuralMarkersJson, "structuralMarkersJson");
        }
        if (parseStatus == LyricsParseStatus.FAILED || parseStatus == LyricsParseStatus.UNSUPPORTED) {
            parseError = CatalogValidation.requireText(parseError, "parseError");
        }
    }
}
