package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.SongStatus;
import java.time.Instant;
import java.util.UUID;

public record Song(
        UUID id,
        String canonicalTitle,
        String normalizedTitle,
        String primaryLanguage,
        String originalArtistDisplay,
        String composerCredits,
        String ccliNumber,
        Integer yearWritten,
        SongStatus songStatus,
        String doctrinalNotes,
        Instant createdAt,
        Instant updatedAt) {
}
