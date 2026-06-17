package com.cadentia.plugin;

import java.util.List;

public class PluginRegistryException extends RuntimeException {
    private final List<String> errors;

    public PluginRegistryException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
