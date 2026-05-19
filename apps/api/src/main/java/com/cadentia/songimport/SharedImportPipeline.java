package com.cadentia.songimport;

public interface SharedImportPipeline {

    CandidateValidationResult validate(ConnectorExecutionContext context, NormalizedImportCandidate candidate);

    StagedImportCandidate stage(ConnectorExecutionContext context, NormalizedImportCandidate candidate);
}
