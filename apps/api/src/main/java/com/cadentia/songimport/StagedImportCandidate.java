package com.cadentia.songimport;

import com.cadentia.catalog.model.ImportCandidateStatus;
import java.util.UUID;

public record StagedImportCandidate(
        UUID importBatchId,
        UUID connectorRunId,
        String sourceRecordId,
        String normalizedTitle,
        ImportCandidateStatus status) {

    public StagedImportCandidate {
        importBatchId = ImportConnectorValidation.requireNonNull(importBatchId, "importBatchId");
        connectorRunId = ImportConnectorValidation.requireNonNull(connectorRunId, "connectorRunId");
        sourceRecordId = ImportConnectorValidation.requireText(sourceRecordId, "sourceRecordId");
        normalizedTitle = ImportConnectorValidation.requireText(normalizedTitle, "normalizedTitle");
        status = ImportConnectorValidation.requireNonNull(status, "status");
    }
}
