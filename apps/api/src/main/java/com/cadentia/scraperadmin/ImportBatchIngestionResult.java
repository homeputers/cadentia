package com.cadentia.scraperadmin;

import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import java.util.List;

public record ImportBatchIngestionResult(
        ImportBatch importBatch,
        List<ImportCandidate> acceptedCandidates,
        List<ProposedDuplicateMatch> proposedMatches,
        List<ImportCandidateValidationError> validationErrors) {

    public ImportBatchIngestionResult {
        if (importBatch == null) {
            throw new NullPointerException("importBatch is required");
        }
        acceptedCandidates = List.copyOf(acceptedCandidates == null ? List.of() : acceptedCandidates);
        proposedMatches = List.copyOf(proposedMatches == null ? List.of() : proposedMatches);
        validationErrors = List.copyOf(validationErrors == null ? List.of() : validationErrors);
    }
}
