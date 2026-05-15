package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import java.time.Instant;
import java.util.UUID;

public record LyricsDocument(
        UUID id,
        UUID arrangementId,
        LyricsFormat format,
        String content,
        String contentHash,
        int versionNumber,
        boolean current,
        boolean containsChords,
        boolean containsSections,
        String sourceReference,
        String createdBy,
        Instant createdAt,
        LyricsParseStatus parseStatus,
        String parseError,
        String parserName,
        String parserVersion,
        Instant parsedAt,
        String parsedSectionsJson,
        String chordMapJson,
        String structuralMarkersJson) {
}
