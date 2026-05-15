package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.ImportCandidateStatus;
import java.time.Instant;
import java.util.UUID;

public record ImportCandidate(
        UUID id,
        UUID importBatchId,
        String externalCandidateId,
        String rawTitle,
        String normalizedTitle,
        String sourceArtistName,
        String sourceArtistMetadataJson,
        String ccliNumber,
        String lyricsHash,
        String sourcePayloadJson,
        ImportCandidateStatus status,
        UUID mergedSongId,
        Instant createdAt,
        Instant updatedAt) {
}
