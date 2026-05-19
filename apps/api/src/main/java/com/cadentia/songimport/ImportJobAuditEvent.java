package com.cadentia.songimport;

import java.time.Instant;
import java.util.UUID;

public record ImportJobAuditEvent(
        Instant occurredAt,
        String actor,
        UUID importBatchId,
        String sourceRecordId,
        ImportJobStatus status,
        String action,
        String result,
        int attemptNumber) {

    public ImportJobAuditEvent {
        occurredAt = ImportConnectorValidation.requireNonNull(occurredAt, "occurredAt");
        actor = ImportConnectorValidation.requireText(actor, "actor");
        importBatchId = ImportConnectorValidation.requireNonNull(importBatchId, "importBatchId");
        status = ImportConnectorValidation.requireNonNull(status, "status");
        action = ImportConnectorValidation.requireText(action, "action");
        result = ImportConnectorValidation.requireText(result, "result");
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber must be non-negative");
        }
    }
}
