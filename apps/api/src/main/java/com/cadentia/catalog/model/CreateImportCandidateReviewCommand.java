package com.cadentia.catalog.model;

import java.util.UUID;

public record CreateImportCandidateReviewCommand(
        UUID importCandidateId,
        UUID proposedDuplicateMatchId,
        ImportCandidateReviewDecision decision,
        String reviewer,
        String reviewNotes) {

    public CreateImportCandidateReviewCommand {
        importCandidateId = CatalogValidation.requireId(importCandidateId, "importCandidateId");
        decision = CatalogValidation.requireEnum(decision, "decision");
        reviewer = CatalogValidation.requireText(reviewer, "reviewer");
        reviewNotes = CatalogValidation.requireOptionalTextIfPresent(reviewNotes, "reviewNotes");
        if (requiresProposedMatch(decision) && proposedDuplicateMatchId == null) {
            throw new IllegalArgumentException("proposedDuplicateMatchId is required for match review decisions");
        }
    }

    private static boolean requiresProposedMatch(ImportCandidateReviewDecision decision) {
        return decision == ImportCandidateReviewDecision.CONFIRM_MATCH
                || decision == ImportCandidateReviewDecision.REJECT_MATCH;
    }
}
