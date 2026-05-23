package com.cadentia.intent;

public record IntentValidationError(
        IntentValidationErrorCode code,
        String path,
        String message) {

    public IntentValidationCategory category() {
        return code.category();
    }

    public IntentOutcomeClass outcomeClass() {
        return code.outcomeClass();
    }

    public boolean retryable() {
        return code.retryable();
    }
}
