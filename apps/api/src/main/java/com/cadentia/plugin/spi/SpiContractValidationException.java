package com.cadentia.plugin.spi;

import java.util.List;

public class SpiContractValidationException extends RuntimeException {
    private final List<String> errors;

    public SpiContractValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
