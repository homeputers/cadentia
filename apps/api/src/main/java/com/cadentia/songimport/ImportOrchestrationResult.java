package com.cadentia.songimport;

public record ImportOrchestrationResult(ImportJobRecord job) {

    public ImportOrchestrationResult {
        job = ImportConnectorValidation.requireNonNull(job, "job");
    }
}
