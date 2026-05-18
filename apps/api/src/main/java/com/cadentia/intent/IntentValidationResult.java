package com.cadentia.intent;

import java.util.List;
import java.util.Objects;

public final class IntentValidationResult {

    private final ValidatedIntent intent;
    private final List<IntentValidationError> errors;

    private IntentValidationResult(ValidatedIntent intent, List<IntentValidationError> errors) {
        this.intent = intent;
        this.errors = List.copyOf(errors);
    }

    public static IntentValidationResult accepted(ValidatedIntent intent) {
        return new IntentValidationResult(Objects.requireNonNull(intent), List.of());
    }

    public static IntentValidationResult rejected(List<IntentValidationError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Rejected validation results require at least one error");
        }
        return new IntentValidationResult(null, errors);
    }

    public boolean isAccepted() {
        return intent != null;
    }

    public ValidatedIntent intent() {
        if (!isAccepted()) {
            throw new IllegalStateException("Rejected validation results do not contain an intent");
        }
        return intent;
    }

    public List<IntentValidationError> errors() {
        return errors;
    }
}
