package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.SongRole;
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
        SongRole songRole,
        String doctrinalNotes,
        Instant createdAt,
        Instant updatedAt) {

    public Song(
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
        this(
                id,
                canonicalTitle,
                normalizedTitle,
                primaryLanguage,
                originalArtistDisplay,
                composerCredits,
                ccliNumber,
                yearWritten,
                songStatus,
                null,
                doctrinalNotes,
                createdAt,
                updatedAt);
    }
}
