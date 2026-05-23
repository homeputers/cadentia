package com.cadentia.intent;

public enum IntentValidationErrorCode {
    MALFORMED_JSON(IntentValidationCategory.PARSE_ERROR, IntentOutcomeClass.RETRYABLE, true),
    UNKNOWN_INTENT(IntentValidationCategory.INTENT_ERROR, IntentOutcomeClass.UNSUPPORTED, false),
    MISSING_REQUIRED_FIELD(IntentValidationCategory.SCHEMA_ERROR, IntentOutcomeClass.HARD_FAIL, false),
    UNSUPPORTED_FIELD(IntentValidationCategory.SCHEMA_ERROR, IntentOutcomeClass.HARD_FAIL, false),
    INVALID_TYPE(IntentValidationCategory.SCHEMA_ERROR, IntentOutcomeClass.HARD_FAIL, false),
    OUT_OF_RANGE(IntentValidationCategory.SCHEMA_ERROR, IntentOutcomeClass.HARD_FAIL, false),
    UNSUPPORTED_ENUM(IntentValidationCategory.SCHEMA_ERROR, IntentOutcomeClass.HARD_FAIL, false),
    UNSUPPORTED_SCHEMA_NAME(IntentValidationCategory.INTENT_ERROR, IntentOutcomeClass.UNSUPPORTED, false),
    UNSUPPORTED_SCHEMA_VERSION(IntentValidationCategory.INTENT_ERROR, IntentOutcomeClass.UNSUPPORTED, false),
    BOUNDARY_VIOLATION(IntentValidationCategory.BOUNDARY_VIOLATION, IntentOutcomeClass.HARD_FAIL, false);

    private final IntentValidationCategory category;
    private final IntentOutcomeClass outcomeClass;
    private final boolean retryable;

    IntentValidationErrorCode(IntentValidationCategory category, IntentOutcomeClass outcomeClass, boolean retryable) {
        this.category = category;
        this.outcomeClass = outcomeClass;
        this.retryable = retryable;
    }

    public IntentValidationCategory category() {
        return category;
    }

    public IntentOutcomeClass outcomeClass() {
        return outcomeClass;
    }

    public boolean retryable() {
        return retryable;
    }
}
