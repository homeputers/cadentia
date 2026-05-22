package com.cadentia.reng.scoring;

import java.util.List;

public record ExplanationRenderResult(
        String text,
        String templateKey,
        List<String> validationErrors) {

    public ExplanationRenderResult {
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
    }

    public boolean hasValidationErrors() {
        return !validationErrors.isEmpty();
    }
}
