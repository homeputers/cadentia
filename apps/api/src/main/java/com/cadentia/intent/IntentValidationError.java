package com.cadentia.intent;

public record IntentValidationError(
        IntentValidationErrorCode code,
        String path,
        String message) {
}
