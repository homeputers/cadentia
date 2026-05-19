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
        auditEvents.add(event(ImportJobStatus.QUEUED, "Job queued", 0));

        if (cancellationRequested) {
            auditEvents.add(event(ImportJobStatus.CANCELED, "Job canceled before execution", 0));
            return new ImportOrchestrationResult(new ImportJobRecord(
                    context.importBatchId(), context.runId(), ImportJobStatus.CANCELED, 0, List.of(), List.of(), auditEvents));
        }

        int attempt = 0;
        List<ImportSourceFailure> sourceFailures = List.of();
        List<StagedImportCandidate> stagedCandidates = List.of();
        while (attempt < maxAttempts) {
            attempt++;
            auditEvents.add(event(ImportJobStatus.RUNNING, "Job running", attempt));
            ConnectorLifecycleResult lifecycleResult = lifecycleRunner.run(providerAdapter, context, configuration);
            stagedCandidates = lifecycleResult.stagedCandidates();
            sourceFailures = toSourceFailures(lifecycleResult.failures());

            if (isPolicyBlocked(lifecycleResult.failures())) {
                auditEvents.add(event(ImportJobStatus.POLICY_BLOCKED, "Connector blocked by policy", attempt));
                return new ImportOrchestrationResult(new ImportJobRecord(
                        context.importBatchId(), context.runId(), ImportJobStatus.POLICY_BLOCKED, attempt,
                        stagedCandidates, sourceFailures, auditEvents));
            }

            if (lifecycleResult.failures().isEmpty()) {
                auditEvents.add(event(ImportJobStatus.SUCCEEDED, "Job completed successfully", attempt));
                return new ImportOrchestrationResult(new ImportJobRecord(
                        context.importBatchId(), context.runId(), ImportJobStatus.SUCCEEDED, attempt,
                        stagedCandidates, sourceFailures, auditEvents));
            }

            if (!stagedCandidates.isEmpty()) {
                auditEvents.add(event(ImportJobStatus.PARTIALLY_SUCCEEDED, "Job completed with partial failures", attempt));
                return new ImportOrchestrationResult(new ImportJobRecord(
                        context.importBatchId(), context.runId(), ImportJobStatus.PARTIALLY_SUCCEEDED, attempt,
                        stagedCandidates, sourceFailures, auditEvents));
            }

            if (isRetryable(lifecycleResult.failures()) && attempt < maxAttempts) {
                auditEvents.add(event(ImportJobStatus.RETRY_SCHEDULED, "Retry scheduled after retryable failure", attempt));
                continue;
            }

            auditEvents.add(event(ImportJobStatus.FAILED, "Job failed", attempt));
            return new ImportOrchestrationResult(new ImportJobRecord(
                    context.importBatchId(), context.runId(), ImportJobStatus.FAILED, attempt,
                    stagedCandidates, sourceFailures, auditEvents));
        }

        auditEvents.add(event(ImportJobStatus.FAILED, "Job failed after retries exhausted", attempt));
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

    private ImportJobAuditEvent event(ImportJobStatus status, String message, int attempt) {
        return new ImportJobAuditEvent(clock.instant(), status, message, attempt);
    }
}
