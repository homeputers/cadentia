package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.model.LyricsParseStatus;
import com.cadentia.catalog.model.UpdateLyricsParseResultCommand;

public record LyricsParseResult(
        LyricsParseStatus status,
        String diagnosticCode,
        String error,
        String parserName,
        String parserVersion,
        String parsedSectionsJson,
        String chordMapJson,
        String structuralMarkersJson) {

    public static LyricsParseResult parsed(
            String parserName,
            String parserVersion,
            String parsedSectionsJson,
            String chordMapJson,
            String structuralMarkersJson) {
        return new LyricsParseResult(
                LyricsParseStatus.PARSED,
                null,
                null,
                parserName,
                parserVersion,
                parsedSectionsJson,
                chordMapJson,
                structuralMarkersJson);
    }

    public static LyricsParseResult failed(String parserName, String parserVersion, String error) {
        return new LyricsParseResult(LyricsParseStatus.FAILED, "PARSER_CONTENT_REQUIRED", error, parserName, parserVersion, null, null, null);
    }

    public static LyricsParseResult unsupported(String diagnosticCode, String error) {
        return new LyricsParseResult(LyricsParseStatus.UNSUPPORTED, diagnosticCode, error, null, null, null, null, null);
    }

    public UpdateLyricsParseResultCommand toCommand() {
        return new UpdateLyricsParseResultCommand(
                status,
                error,
                parserName,
                parserVersion,
                parsedSectionsJson,
                chordMapJson,
                structuralMarkersJson);
    }
}
