package com.cadentia.plugin.policy;

public class PluginPolicyException extends RuntimeException {
    private final String safeCode;

    public PluginPolicyException(String safeCode, String safeMessage) {
        super(safeMessage);
        this.safeCode = safeCode;
    }

    public String safeCode() {
        return safeCode;
    }
}
