package com.cadentia.catalog.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProposedDuplicateMatchCommand(
        UUID importCandidateId,
        UUID candidateSongId,
        BigDecimal matchScore,
        String matchSignalsJson,
        DuplicateMatchStatus status,
        String suggestedBy) {

    public CreateProposedDuplicateMatchCommand {
        importCandidateId = CatalogValidation.requireId(importCandidateId, "importCandidateId");
        candidateSongId = CatalogValidation.requireId(candidateSongId, "candidateSongId");
        if (matchScore == null) {
            throw new IllegalArgumentException("matchScore is required");
        }
        matchScore = CatalogValidation.requireUnitRangeIfPresent(matchScore, "matchScore");
        matchSignalsJson = matchSignalsJson == null ? "{}"
                : CatalogValidation.requireText(matchSignalsJson, "matchSignalsJson");
        status = CatalogValidation.requireEnum(status, "status");
        suggestedBy = CatalogValidation.requireText(suggestedBy, "suggestedBy");
    }
}
