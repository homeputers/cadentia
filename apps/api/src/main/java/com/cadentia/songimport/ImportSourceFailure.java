package com.cadentia.songimport;

public record ImportSourceFailure(String sourceRecordId, ConnectorFailure failure) {

    public ImportSourceFailure {
        sourceRecordId = ImportConnectorValidation.requireText(sourceRecordId, "sourceRecordId");
        failure = ImportConnectorValidation.requireNonNull(failure, "failure");
    }
}
