package com.cadentia.scraperadmin;

import java.util.List;

public record ImportBatchIngestionCommand(
        String sourceSystem,
        String initiatedBy,
        List<ImportCandidateRecord> candidates) {

    public ImportBatchIngestionCommand {
        if (sourceSystem == null || sourceSystem.isBlank()) {
            throw new IllegalArgumentException("sourceSystem is required");
        }
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new IllegalArgumentException("initiatedBy is required");
        }
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates are required");
        }
        sourceSystem = sourceSystem.trim();
        initiatedBy = initiatedBy.trim();
    }
}
