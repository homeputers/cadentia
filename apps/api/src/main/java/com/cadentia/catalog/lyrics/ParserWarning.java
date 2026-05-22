package com.cadentia.catalog.lyrics;

public record ParserWarning(String code, String message, Integer lineNumber) {

    public ParserWarning {
        code = ParserOutputValidation.requireText(code, "code");
        message = ParserOutputValidation.requireText(message, "message");
        new ParserDiagnosticCodebook().require(code);
    }
}
