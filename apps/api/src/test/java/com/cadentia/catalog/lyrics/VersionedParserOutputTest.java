package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class VersionedParserOutputTest {

    @Test
    void createsVersionedParserOutputWithQueryableWarningsAndConfidence() {
        // Arrange
        ParserWarning warning = new ParserWarning("UNKNOWN_CHORD", "Chord not recognized", 12);
        ParserConfidence confidence = new ParserConfidence("estimatedKey", 0.62, "derived_from_chords");

        // Act
        VersionedParserOutput result = new VersionedParserOutput(
                "deterministic-chordpro-parser",
                "adr-009-v1",
                "lyrics-parse-output-v1",
                SourceContentHash.sha256("[A]Amazing grace"),
                "[]",
                "[]",
                "[]",
                List.of(warning),
                List.of(confidence));

        // Then
        assertThat(result.outputSchemaVersion()).isEqualTo("lyrics-parse-output-v1");
        assertThat(result.warnings()).extracting(ParserWarning::code).containsExactly("UNKNOWN_CHORD");
        assertThat(result.confidence()).extracting(ParserConfidence::field).containsExactly("estimatedKey");
    }

    @Test
    void sourceHashChangesWhenRawContentChanges() {
        // Arrange
        String first = "[A]Amazing grace";
        String second = "[A]Amazing grace\n";

        // Act
        String firstHash = SourceContentHash.sha256(first);
        String secondHash = SourceContentHash.sha256(second);

        // Then
        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void confidenceScoreMustBeBounded() {
        // Act / Assert
        assertThatThrownBy(() -> new ParserConfidence("estimatedBpm", 1.2, "weak_signal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 1");
    }
}
