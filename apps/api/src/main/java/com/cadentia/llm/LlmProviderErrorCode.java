package com.cadentia.llm;

public enum LlmProviderErrorCode {
    DISABLED,
    TIMEOUT,
    CONNECTION_FAILURE,
    NON_SUCCESS_STATUS,
    UNSUPPORTED_RESPONSE_SHAPE,
    CANCELLED
}
