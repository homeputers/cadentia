package com.cadentia.songimport;

public record ImportOrchestrationResult(ImportJobRecord job, ImportOperationalMetrics metrics) {

    public ImportOrchestrationResult {
        job = ImportConnectorValidation.requireNonNull(job, "job");
        metrics = ImportConnectorValidation.requireNonNull(metrics, "metrics");
    }

    public ImportOrchestrationResult(ImportJobRecord job) {
        this(job, ImportOperationalMetrics.from(job));
    }
}
