package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParserWarningTest {

    @Test
    void allowsRegisteredWarningCode() {
        ParserWarning warning = new ParserWarning("PARSER_UNKNOWN_CHORD", "Unknown chord", 3);

        assertThat(warning.code()).isEqualTo("PARSER_UNKNOWN_CHORD");
        assertThat(warning.message()).isEqualTo("Unknown chord");
        assertThat(warning.lineNumber()).isEqualTo(3);
    }

    @Test
    void rejectsUnregisteredWarningCode() {
        assertThatThrownBy(() -> new ParserWarning("FREE_FORM_WARNING", "message", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unregistered parser diagnostic code");
    }
}
