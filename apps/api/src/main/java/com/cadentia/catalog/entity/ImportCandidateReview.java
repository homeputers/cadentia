package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import java.time.Instant;
import java.util.UUID;

public record ImportCandidateReview(
        UUID id,
        UUID importCandidateId,
        UUID proposedDuplicateMatchId,
        ImportCandidateReviewDecision decision,
        String reviewer,
        String reviewNotes,
        Instant reviewedAt) {
}
