package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.LyricsParseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ParserRunHistory(
        UUID id,
        UUID lyricsDocumentId,
        String parserName,
        String parserVersion,
        String sourceContentHash,
        String triggerType,
        String actor,
        Instant createdAt,
        UUID supersedesRunId,
        UUID supersededByRunId,
        LyricsParseStatus status,
        List<String> warnings,
        List<String> confidenceSnapshot) {
}
