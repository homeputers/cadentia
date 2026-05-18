package com.cadentia.llm;

import com.cadentia.intent.IntentValidationError;
import com.cadentia.intent.ValidatedIntent;
import java.util.List;
import java.util.Objects;

public record IntentParseResult(
        IntentParseStatus status,
        ValidatedIntent intent,
        boolean retryAttempted,
        List<IntentValidationError> errors) {

    public IntentParseResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(intent, "intent is required");
        errors = List.copyOf(errors == null ? List.of() : errors);
    }

    public static IntentParseResult accepted(ValidatedIntent intent, boolean retryAttempted) {
        return new IntentParseResult(IntentParseStatus.ACCEPTED, intent, retryAttempted, List.of());
    }

    public static IntentParseResult safeFailure(ValidatedIntent intent, List<IntentValidationError> errors) {
        return new IntentParseResult(IntentParseStatus.SAFE_FAILURE, intent, true, errors);
    }
}
