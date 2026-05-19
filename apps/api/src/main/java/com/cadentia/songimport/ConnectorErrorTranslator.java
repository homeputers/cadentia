package com.cadentia.songimport;

public interface ConnectorErrorTranslator {

    ConnectorFailure translate(ConnectorLifecycleStage stage, RuntimeException exception);
}
