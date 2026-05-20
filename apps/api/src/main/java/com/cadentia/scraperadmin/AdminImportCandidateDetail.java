package com.cadentia.scraperadmin;

import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import java.util.List;

public record AdminImportCandidateDetail(
        ImportCandidate candidate,
        String rawSourceReference,
        String parserName,
        String parserVersion,
        String parserConfidence,
        List<String> parserWarnings,
        List<ProposedDuplicateMatch> duplicateMatches,
        List<ImportCandidateReview> reviewHistory) {

    public AdminImportCandidateDetail {
        parserWarnings = List.copyOf(parserWarnings);
        duplicateMatches = List.copyOf(duplicateMatches);
        reviewHistory = List.copyOf(reviewHistory);
    }
}
