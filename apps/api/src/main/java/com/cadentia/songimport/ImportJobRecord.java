package com.cadentia.songimport;

import java.util.List;
import java.util.UUID;

public record ImportJobRecord(
        UUID importBatchId,
        UUID connectorRunId,
        ImportJobStatus status,
        int attempts,
        List<StagedImportCandidate> stagedCandidates,
        List<ImportSourceFailure> sourceFailures,
        List<ImportJobAuditEvent> auditEvents) {

    public ImportJobRecord {
        importBatchId = ImportConnectorValidation.requireNonNull(importBatchId, "importBatchId");
        connectorRunId = ImportConnectorValidation.requireNonNull(connectorRunId, "connectorRunId");
        status = ImportConnectorValidation.requireNonNull(status, "status");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must be non-negative");
        }
        stagedCandidates = List.copyOf(ImportConnectorValidation.requireNonNull(stagedCandidates, "stagedCandidates"));
        sourceFailures = List.copyOf(ImportConnectorValidation.requireNonNull(sourceFailures, "sourceFailures"));
        auditEvents = List.copyOf(ImportConnectorValidation.requireNonNull(auditEvents, "auditEvents"));
    }
}
