package com.cadentia.songimport;

import java.util.List;

public record CandidateValidationResult(
        NormalizedImportCandidate candidate,
        boolean valid,
        List<ConnectorFailure> failures) {

    public CandidateValidationResult {
        candidate = ImportConnectorValidation.requireNonNull(candidate, "candidate");
        failures = List.copyOf(ImportConnectorValidation.requireNonNull(failures, "failures"));
        if (valid && !failures.isEmpty()) {
            throw new IllegalArgumentException("valid validation result cannot include failures");
        }
    }

    public static CandidateValidationResult valid(NormalizedImportCandidate candidate) {
        return new CandidateValidationResult(candidate, true, List.of());
    }

    public static CandidateValidationResult invalid(
            NormalizedImportCandidate candidate,
            ConnectorFailure failure) {
        return new CandidateValidationResult(candidate, false, List.of(failure));
    }
}
