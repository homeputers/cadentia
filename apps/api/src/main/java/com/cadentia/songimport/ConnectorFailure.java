package com.cadentia.songimport;

public record ConnectorFailure(
        ConnectorLifecycleStage stage,
        ConnectorErrorCode errorCode,
        String message,
        boolean retryable) {

    public ConnectorFailure {
        stage = ImportConnectorValidation.requireNonNull(stage, "stage");
        errorCode = ImportConnectorValidation.requireNonNull(errorCode, "errorCode");
        message = ImportConnectorValidation.requireText(message, "message");
    }
}
