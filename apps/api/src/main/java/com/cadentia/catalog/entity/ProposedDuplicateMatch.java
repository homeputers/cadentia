package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.DuplicateMatchStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProposedDuplicateMatch(
        UUID id,
        UUID importCandidateId,
        UUID candidateSongId,
        BigDecimal matchScore,
        String matchSignalsJson,
        DuplicateMatchStatus status,
        String suggestedBy,
        Instant createdAt,
        Instant updatedAt) {
}
