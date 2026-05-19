package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.model.LyricsParseStatus;
import java.time.Instant;
import java.util.UUID;

public record ParserReviewResultView(
        UUID lyricsDocumentId,
        String sourceReference,
        String sourceContentHash,
        LyricsParseStatus parseStatus,
        String parseError,
        String parserName,
        String parserVersion,
        Instant parsedAt,
        String parsedSectionsJson,
        String chordMapJson,
        String structuralMarkersJson,
        boolean stale) {

    public static ParserReviewResultView from(LyricsDocument document, boolean stale) {
        return new ParserReviewResultView(
                document.id(),
                document.sourceReference(),
                document.contentHash(),
                document.parseStatus(),
                document.parseError(),
                document.parserName(),
                document.parserVersion(),
                document.parsedAt(),
                document.parsedSectionsJson(),
                document.chordMapJson(),
                document.structuralMarkersJson(),
                stale);
    }
}
