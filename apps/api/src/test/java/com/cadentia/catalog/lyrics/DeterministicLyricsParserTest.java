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
        String content = "{title: Fixture}\n{key: E}\n{tempo: 74}\n{time: 4/4}\n{start_of_verse: Verse 1}\n[A]Alpha [D/F#]Beta\n{end_of_verse}\n";

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
                .contains("\"type\":\"section_end\"")
                .contains("\"type\":\"fingerprint\"")
                .contains("\"field\":\"raw_source\"")
                .contains("\"field\":\"lyrics_normalized\"")
                .contains("\"field\":\"chord_progression\"")
                .contains("\"field\":\"section_sequence\"")
                .contains("\"field\":\"key_independent_chord_movement\"")
                .contains("\"type\":\"duplicate_support\"")
                .contains("\"field\":\"key\"")
                .contains("\"value\":\"E\"")
                .contains("\"field\":\"bpm\"")
                .contains("\"value\":74")
                .contains("\"field\":\"meter\"")
                .contains("\"value\":\"4/4\"")
                .contains("\"evidence\":\"explicit_metadata\"");
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

    @Test
    void infersKeyFromChordsWithLowerConfidenceWhenMetadataMissing() {
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.MARKDOWN);

        LyricsParseResult result = parser.parse("## Verse\n[C]One [G]Two [Am]Three");

        assertThat(result.status()).isEqualTo(LyricsParseStatus.PARSED);
        assertThat(result.structuralMarkersJson())
                .contains("\"field\":\"key\"")
                .contains("\"value\":\"C\"")
                .contains("\"evidence\":\"ambiguous_chord_distribution\"")
                .contains("\"field\":\"bpm\"")
                .contains("\"value\":\"unknown\"");
    }

    @Test
    void emitsWarningsForConflictingMetadataValues() {
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO);

        LyricsParseResult result = parser.parse("{key: C}\n{key: D}\n{tempo: 68}\n{tempo: 120}\n[A]Line");

        assertThat(result.status()).isEqualTo(LyricsParseStatus.PARSED);
        assertThat(result.structuralMarkersJson())
                .contains("\"type\":\"warning_conflicting_key_metadata\"")
                .contains("\"type\":\"warning_conflicting_bpm_metadata\"");
    }

    @Test
    void addsNashvilleNumbersForKnownMajorAndSlashChords() {
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO);

        LyricsParseResult result = parser.parse("{key: C}\n[C]One [G/B]Two [Am]Three");

        assertThat(result.chordMapJson())
                .contains("\"sourceChord\":\"C\"")
                .contains("\"nashvilleNumber\":\"1\"")
                .contains("\"sourceChord\":\"G/B\"")
                .contains("\"nashvilleNumber\":\"5/7\"")
                .contains("\"sourceChord\":\"Am\"")
                .contains("\"nashvilleNumber\":\"6\"");
    }

    @Test
    void addsNashvilleNumbersForMinorRelativeKey() {
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO);

        LyricsParseResult result = parser.parse("{key: Am}\n[Am]One [C]Two [G]Three");

        assertThat(result.chordMapJson())
                .contains("\"nashvilleNumber\":\"1m\"")
                .contains("\"nashvilleNumber\":\"b3\"")
                .contains("\"nashvilleNumber\":\"b7\"");
    }

    @Test
    void skipsNashvilleWhenKeyIsAmbiguousAndWarnsForUnsupportedChords() {
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.MARKDOWN);
        LyricsParser chordProParser = DeterministicLyricsParser.forFormat(LyricsFormat.CHORDPRO);

        LyricsParseResult ambiguous = parser.parse("## Verse\n[C]One [G]Two [Am]Three");
        LyricsParseResult unsupported = chordProParser.parse("{key: C}\n[F#]Sharp");

        assertThat(ambiguous.structuralMarkersJson()).contains("\"type\":\"warning_nashville_conversion_skipped\"");
        assertThat(unsupported.structuralMarkersJson()).contains("\"type\":\"warning_unsupported_nashville_chord\"");
        assertThat(unsupported.chordMapJson()).contains("\"sourceChord\":\"F#\"").doesNotContain("\"nashvilleNumber\"");
    }

    @Test
    void fingerprintSignalsAreStableForEquivalentNormalizedInput() {
        LyricsParser parser = DeterministicLyricsParser.forFormat(LyricsFormat.MARKDOWN);

        LyricsParseResult first = parser.parse("## Verse\n[A]Alpha   line\n## Chorus\n[D]Beta");
        LyricsParseResult second = parser.parse("## Verse\n[A]alpha line\n## Chorus\n[D]beta");

        assertThat(first.structuralMarkersJson()).contains("\"field\":\"lyrics_normalized\"");
        assertThat(extractFingerprint(first.structuralMarkersJson(), "lyrics_normalized"))
                .isEqualTo(extractFingerprint(second.structuralMarkersJson(), "lyrics_normalized"));
        assertThat(extractFingerprint(first.structuralMarkersJson(), "section_sequence"))
                .isEqualTo(extractFingerprint(second.structuralMarkersJson(), "section_sequence"));
    }

    private String extractFingerprint(String markerJson, String field) {
        String fieldToken = "\"field\":\"" + field + "\"";
        int fieldIndex = markerJson.indexOf(fieldToken);
        assertThat(fieldIndex).isGreaterThanOrEqualTo(0);
        int hashIndex = markerJson.indexOf("sha256:", fieldIndex);
        assertThat(hashIndex).isGreaterThanOrEqualTo(0);
        int hashEnd = markerJson.indexOf('"', hashIndex);
        return markerJson.substring(hashIndex, hashEnd);
    }
}
