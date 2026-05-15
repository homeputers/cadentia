package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.LyricsFormat;
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
        Instant createdAt) {
}
