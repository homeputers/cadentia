package com.cadentia.llm;

public class LlmProviderException extends RuntimeException {

    private final LlmProviderErrorCode errorCode;

    public LlmProviderException(LlmProviderErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LlmProviderException(LlmProviderErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public LlmProviderErrorCode errorCode() {
        return errorCode;
    }
}
