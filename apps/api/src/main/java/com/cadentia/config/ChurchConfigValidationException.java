package com.cadentia.config;

import java.util.List;

public class ChurchConfigValidationException extends RuntimeException {
    private final List<String> errors;

    public ChurchConfigValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
