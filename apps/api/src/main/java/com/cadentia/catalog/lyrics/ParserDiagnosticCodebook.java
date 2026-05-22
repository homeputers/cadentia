package com.cadentia.catalog.lyrics;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ParserDiagnosticCodebook {

    private final Map<String, ParserDiagnosticCode> codes;

    public ParserDiagnosticCodebook() {
        this.codes = Map.of(
                "PARSER_UNSUPPORTED_FORMAT",
                new ParserDiagnosticCode(
                        "PARSER_UNSUPPORTED_FORMAT",
                        ParserDiagnosticSeverity.ERROR,
                        "Use a parser that declares support for this lyrics format."),
                "PARSER_CONTENT_REQUIRED",
                new ParserDiagnosticCode(
                        "PARSER_CONTENT_REQUIRED",
                        ParserDiagnosticSeverity.ERROR,
                        "Provide non-empty lyrics content before parsing."),
                "PARSER_MALFORMED_MARKER",
                new ParserDiagnosticCode(
                        "PARSER_MALFORMED_MARKER",
                        ParserDiagnosticSeverity.WARNING,
                        "Fix malformed structural markers such as unclosed brackets."),
                "PARSER_UNKNOWN_CHORD",
                new ParserDiagnosticCode(
                        "PARSER_UNKNOWN_CHORD",
                        ParserDiagnosticSeverity.WARNING,
                        "Normalize unsupported chord tokens to supported notation."),
                "UNKNOWN_CHORD",
                new ParserDiagnosticCode(
                        "UNKNOWN_CHORD",
                        ParserDiagnosticSeverity.WARNING,
                        "Normalize unsupported chord tokens to supported notation."),
                "MALFORMED_MARKER",
                new ParserDiagnosticCode(
                        "MALFORMED_MARKER",
                        ParserDiagnosticSeverity.WARNING,
                        "Fix malformed structural markers such as unclosed brackets."));
    }

    public ParserDiagnosticCode require(String code) {
        return Optional.ofNullable(codes.get(code))
                .orElseThrow(() -> new IllegalArgumentException("unregistered parser diagnostic code: " + code));
    }

    public Map<String, ParserDiagnosticCode> all() {
        return codes;
    }
}
