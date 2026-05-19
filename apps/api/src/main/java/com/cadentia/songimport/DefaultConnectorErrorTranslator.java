package com.cadentia.songimport;

public final class DefaultConnectorErrorTranslator implements ConnectorErrorTranslator {

    @Override
    public ConnectorFailure translate(ConnectorLifecycleStage stage, RuntimeException exception) {
        return new ConnectorFailure(stage, ConnectorErrorCode.UNKNOWN, exception.getMessage(), false);
    }
}
