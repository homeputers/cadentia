package com.cadentia.songimport;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class ImportJobOrchestrator {

    private final ConnectorLifecycleRunner lifecycleRunner;
    private final Clock clock;
    private final int maxAttempts;

    public ImportJobOrchestrator(ConnectorLifecycleRunner lifecycleRunner, Clock clock, int maxAttempts) {
        this.lifecycleRunner = ImportConnectorValidation.requireNonNull(lifecycleRunner, "lifecycleRunner");
        this.clock = ImportConnectorValidation.requireNonNull(clock, "clock");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public ImportOrchestrationResult run(
            ProviderAdapter providerAdapter,
            ConnectorExecutionContext context,
            ConnectorConfiguration configuration,
            boolean cancellationRequested) {
        List<ImportJobAuditEvent> auditEvents = new ArrayList<>();
        auditEvents.add(event(context, null, ImportJobStatus.QUEUED, "JOB_QUEUED", "queued", 0));

        if (cancellationRequested) {
            auditEvents.add(event(context, null, ImportJobStatus.CANCELED, "JOB_CANCELED", "canceled-before-execution", 0));
            return new ImportOrchestrationResult(new ImportJobRecord(
                    context.importBatchId(), context.runId(), ImportJobStatus.CANCELED, 0, List.of(), List.of(), auditEvents));
        }

        int attempt = 0;
        List<ImportSourceFailure> sourceFailures = List.of();
        List<StagedImportCandidate> stagedCandidates = List.of();
        while (attempt < maxAttempts) {
            attempt++;
            auditEvents.add(event(context, null, ImportJobStatus.RUNNING, "JOB_RUNNING", "running", attempt));
            ConnectorLifecycleResult lifecycleResult = lifecycleRunner.run(providerAdapter, context, configuration);
            stagedCandidates = lifecycleResult.stagedCandidates();
            sourceFailures = toSourceFailures(lifecycleResult.failures());
            for (StagedImportCandidate stagedCandidate : stagedCandidates) {
                auditEvents.add(event(
                        context,
                        stagedCandidate.sourceRecordId(),
                        ImportJobStatus.RUNNING,
                        "CANDIDATE_STAGED",
                        stagedCandidate.status().name(),
                        attempt));
            }
            for (ImportSourceFailure sourceFailure : sourceFailures) {
                auditEvents.add(event(
                        context,
                        sourceFailure.sourceRecordId(),
                        ImportJobStatus.RUNNING,
                        "SOURCE_FAILURE",
                        sourceFailure.failure().errorCode().name(),
                        attempt));
            }

            if (isPolicyBlocked(lifecycleResult.failures())) {
                auditEvents.add(event(context, null, ImportJobStatus.POLICY_BLOCKED, "POLICY_DECISION", "connector-blocked", attempt));
                return new ImportOrchestrationResult(new ImportJobRecord(
                        context.importBatchId(), context.runId(), ImportJobStatus.POLICY_BLOCKED, attempt,
                        stagedCandidates, sourceFailures, auditEvents));
            }

            if (lifecycleResult.failures().isEmpty()) {
                auditEvents.add(event(context, null, ImportJobStatus.SUCCEEDED, "JOB_COMPLETED", "succeeded", attempt));
                return new ImportOrchestrationResult(new ImportJobRecord(
                        context.importBatchId(), context.runId(), ImportJobStatus.SUCCEEDED, attempt,
                        stagedCandidates, sourceFailures, auditEvents));
            }

            if (!stagedCandidates.isEmpty()) {
                auditEvents.add(event(context, null, ImportJobStatus.PARTIALLY_SUCCEEDED, "JOB_COMPLETED", "partially-succeeded", attempt));
                return new ImportOrchestrationResult(new ImportJobRecord(
                        context.importBatchId(), context.runId(), ImportJobStatus.PARTIALLY_SUCCEEDED, attempt,
                        stagedCandidates, sourceFailures, auditEvents));
            }

            if (isRetryable(lifecycleResult.failures()) && attempt < maxAttempts) {
                auditEvents.add(event(context, null, ImportJobStatus.RETRY_SCHEDULED, "RETRY_DECISION", "retry-scheduled", attempt));
                continue;
            }

            auditEvents.add(event(context, null, ImportJobStatus.FAILED, "JOB_COMPLETED", "failed", attempt));
            return new ImportOrchestrationResult(new ImportJobRecord(
                    context.importBatchId(), context.runId(), ImportJobStatus.FAILED, attempt,
                    stagedCandidates, sourceFailures, auditEvents));
        }

        auditEvents.add(event(context, null, ImportJobStatus.FAILED, "JOB_COMPLETED", "failed-retries-exhausted", attempt));
        return new ImportOrchestrationResult(new ImportJobRecord(
                context.importBatchId(), context.runId(), ImportJobStatus.FAILED, attempt,
                stagedCandidates, sourceFailures, auditEvents));
    }

    private boolean isPolicyBlocked(List<ConnectorFailure> failures) {
        return failures.stream().anyMatch(failure -> failure.errorCode() == ConnectorErrorCode.POLICY_BLOCKED);
    }

    private boolean isRetryable(List<ConnectorFailure> failures) {
        return failures.stream().allMatch(ConnectorFailure::retryable);
    }

    private List<ImportSourceFailure> toSourceFailures(List<ConnectorFailure> failures) {
        List<ImportSourceFailure> sourceFailures = new ArrayList<>();
        for (int i = 0; i < failures.size(); i++) {
            sourceFailures.add(new ImportSourceFailure("source-" + (i + 1), failures.get(i)));
        }
        return sourceFailures;
    }

    private ImportJobAuditEvent event(
            ConnectorExecutionContext context,
            String sourceRecordId,
            ImportJobStatus status,
            String action,
            String result,
            int attempt) {
        return new ImportJobAuditEvent(
                clock.instant(),
                context.initiatedBy(),
                context.importBatchId(),
                sourceRecordId,
                status,
                action,
                result,
                attempt);
    }
}
