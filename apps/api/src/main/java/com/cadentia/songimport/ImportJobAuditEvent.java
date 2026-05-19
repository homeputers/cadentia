package com.cadentia.songimport;

import java.time.Instant;

public record ImportJobAuditEvent(Instant occurredAt, ImportJobStatus status, String message, int attemptNumber) {

    public ImportJobAuditEvent {
        occurredAt = ImportConnectorValidation.requireNonNull(occurredAt, "occurredAt");
        status = ImportConnectorValidation.requireNonNull(status, "status");
        message = ImportConnectorValidation.requireText(message, "message");
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("attemptNumber must be non-negative");
        }
    }
}
