package com.cadentia.catalog.lyrics;

import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.model.LyricsParseStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ParserReviewResultView(
        UUID lyricsDocumentId,
        String sourceReference,
        String sourceContentHash,
        LyricsParseStatus parseStatus,
        String parseError,
        String parserName,
        String parserVersion,
        String parserCapabilityRegistryVersion,
        List<ParserCapability> parserCapabilities,
        Map<String, ParserDiagnosticCode> parserDiagnosticCodebook,
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
                "adr-009-capabilities-v1",
                List.of(),
                Map.of(),
                document.parsedAt(),
                document.parsedSectionsJson(),
                document.chordMapJson(),
                document.structuralMarkersJson(),
                stale);
    }
}
