package com.cadentia.catalog.lyrics;

public record ParserDiagnosticCode(String code, ParserDiagnosticSeverity severity, String remediationHint) {

    public ParserDiagnosticCode {
        code = ParserOutputValidation.requireText(code, "code");
        if (severity == null) {
            throw new IllegalArgumentException("severity is required");
        }
        remediationHint = ParserOutputValidation.requireText(remediationHint, "remediationHint");
    }
}
