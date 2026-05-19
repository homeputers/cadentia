package com.cadentia.songimport;

import java.time.Instant;
import java.util.UUID;

public record ConnectorExecutionContext(
        UUID runId,
        UUID importBatchId,
        String initiatedBy,
        Instant startedAt) {

    public ConnectorExecutionContext {
        runId = ImportConnectorValidation.requireNonNull(runId, "runId");
        importBatchId = ImportConnectorValidation.requireNonNull(importBatchId, "importBatchId");
        initiatedBy = ImportConnectorValidation.requireText(initiatedBy, "initiatedBy");
        startedAt = ImportConnectorValidation.requireNonNull(startedAt, "startedAt");
    }
}
