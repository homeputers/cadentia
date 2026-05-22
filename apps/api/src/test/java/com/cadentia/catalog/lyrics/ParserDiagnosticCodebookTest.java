package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParserDiagnosticCodebookTest {

    @Test
    void resolvesRegisteredCodeWithSeverityAndRemediationHint() {
        ParserDiagnosticCodebook codebook = new ParserDiagnosticCodebook();

        ParserDiagnosticCode code = codebook.require("PARSER_UNSUPPORTED_FORMAT");

        assertThat(code.code()).isEqualTo("PARSER_UNSUPPORTED_FORMAT");
        assertThat(code.severity()).isEqualTo(ParserDiagnosticSeverity.ERROR);
        assertThat(code.remediationHint()).contains("declares support");
    }

    @Test
    void resolvesLegacyWarningAliasesForBackwardCompatibility() {
        ParserDiagnosticCodebook codebook = new ParserDiagnosticCodebook();

        ParserDiagnosticCode unknownChord = codebook.require("UNKNOWN_CHORD");
        ParserDiagnosticCode malformedMarker = codebook.require("MALFORMED_MARKER");

        assertThat(unknownChord.severity()).isEqualTo(ParserDiagnosticSeverity.WARNING);
        assertThat(malformedMarker.severity()).isEqualTo(ParserDiagnosticSeverity.WARNING);
    }

    @Test
    void throwsForUnregisteredCode() {
        ParserDiagnosticCodebook codebook = new ParserDiagnosticCodebook();

        assertThatThrownBy(() -> codebook.require("NOT_REGISTERED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unregistered parser diagnostic code");
    }
}
