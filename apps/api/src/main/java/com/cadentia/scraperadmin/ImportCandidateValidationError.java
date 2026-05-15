package com.cadentia.scraperadmin;

public record ImportCandidateValidationError(
        String candidateIdentifier,
        String field,
        String message) {

    public ImportCandidateValidationError {
        if (candidateIdentifier == null || candidateIdentifier.isBlank()) {
            throw new IllegalArgumentException("candidateIdentifier is required");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}
