package com.cadentia.catalog.lyrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.LyricsParseStatus;
import org.junit.jupiter.api.Test;

class DeterministicLyricsParserTest {

    @Test
    void parsesChordProSectionsChordsAndMarkersDeterministically() {
        // Arrange
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO);
        String content = "{title: Fixture}\n{start_of_verse: Verse 1}\n[A]Alpha [D/F#]Beta\n{end_of_verse}\n";

        // Act
        LyricsParseResult result = parser.parse(content);

        // Assert
        assertThat(result.status()).isEqualTo(LyricsParseStatus.PARSED);
        assertThat(result.parserName()).isEqualTo("deterministic-chordpro-parser");
        assertThat(result.parsedSectionsJson())
                .contains("\"label\":\"Verse 1\"")
                .contains("[A]Alpha [D/F#]Beta");
        assertThat(result.chordMapJson())
                .contains("\"sourceChord\":\"A\"")
                .contains("\"normalizedChord\":\"A\"")
                .contains("\"sourceChord\":\"D/F#\"");
        assertThat(result.structuralMarkersJson())
                .contains("\"type\":\"directive\"")
                .contains("\"type\":\"section_start\"")
                .contains("\"type\":\"section_end\"");
    }

    @Test
    void parsesMarkdownHeadingsAndInlineChordMarkers() {
        // Arrange
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.MARKDOWN);
        String content = "## Verse 1\nAlpha line\n\n## Chorus\n[G]Lift marker\n";

        // Act
        LyricsParseResult result = parser.parse(content);

        // Assert
        assertThat(result.status()).isEqualTo(LyricsParseStatus.PARSED);
        assertThat(result.parsedSectionsJson())
                .contains("\"label\":\"Verse 1\"")
                .contains("\"label\":\"Chorus\"");
        assertThat(result.chordMapJson()).contains("\"sourceChord\":\"G\"");
        assertThat(result.structuralMarkersJson()).contains("\"type\":\"heading\"");
    }

    @Test
    void returnsFailureForBlankContentWithoutThrowingAwayRawDocument() {
        // Arrange
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.PLAIN_TEXT);

        // Act
        LyricsParseResult result = parser.parse("   ");

        // Assert
        assertThat(result.status()).isEqualTo(LyricsParseStatus.FAILED);
        assertThat(result.error()).contains("content is required");
        assertThat(result.parsedSectionsJson()).isNull();
    }

    @Test
    void parsesOnSongSectionLabelsRepeatsAndMalformedMarkersWithWarnings() {
        // Arrange
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.ONSONG);
        String content = "[Verse 1]\n[A]Alpha\nx2\n[NotAChord lyric\n[Chorus]\n[Q13]Beta";

        // Act
        LyricsParseResult result = parser.parse(content);

        // Assert
        assertThat(result.status()).isEqualTo(LyricsParseStatus.PARSED);
        assertThat(result.parsedSectionsJson()).contains("\"label\":\"Verse 1\"").contains("\"label\":\"Chorus\"");
        assertThat(result.chordMapJson())
                .contains("\"sourceChord\":\"A\"")
                .contains("\"isNormalized\":true")
                .contains("\"sourceChord\":\"Q13\"")
                .contains("\"isNormalized\":false");
        assertThat(result.structuralMarkersJson())
                .contains("\"type\":\"repeat_hint\"")
                .contains("\"type\":\"warning_malformed_marker\"")
                .contains("\"type\":\"warning_unknown_chord\"");
    }
}
