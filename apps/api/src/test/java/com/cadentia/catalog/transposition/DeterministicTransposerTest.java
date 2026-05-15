package com.cadentia.catalog.transposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.model.KeyMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicTransposerTest {

    private final DeterministicTransposer transposer = new DeterministicTransposer();

    @Test
    void transposesAcrossAllDocumentedMajorKeysWithTargetEnharmonicSpelling() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("C", KeyMode.MAJOR);
        List<String> targetTonics = List.of(
                "C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#",
                "Bb", "B");

        // Act / Assert
        for (String targetTonic : targetTonics) {
            MusicalKey targetKey = new MusicalKey(targetTonic, KeyMode.MAJOR);

            assertThat(transposer.transposeChord(baseKey, targetKey, "C")).isEqualTo(targetTonic);
        }
    }

    @Test
    void preservesChordQualitiesExtensionsAndBassNotes() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("G", KeyMode.MAJOR);
        MusicalKey targetKey = new MusicalKey("Ab", KeyMode.MAJOR);

        // Act
        List<String> transposed = transposer.transposeChordSymbols(
                baseKey,
                targetKey,
                List.of("G/B", "Cadd9", "D/F#", "F#m7b5", "Ebmaj7/G"));

        // Assert
        assertThat(transposed).containsExactly("Ab/C", "Dbadd9", "Eb/G", "Gm7b5", "Emaj7/Ab");
    }

    @Test
    void transposesMinorKeysWithoutChangingMinorChordQuality() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("A", KeyMode.MINOR);
        MusicalKey targetKey = new MusicalKey("C", KeyMode.MINOR);

        // Act
        List<String> transposed = transposer.transposeChordSymbols(
                baseKey,
                targetKey,
                List.of("Am", "Dm", "E7", "Am"));

        // Assert
        assertThat(transposed).containsExactly("Cm", "Fm", "G7", "Cm");
    }

    @Test
    void noOpTranspositionValidatesButPreservesOriginalChordSpelling() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("C", KeyMode.MAJOR);
        MusicalKey targetKey = new MusicalKey("C", KeyMode.MAJOR);

        // Act
        String transposed = transposer.transposeChord(baseKey, targetKey, "Bbmaj7/G");

        // Assert
        assertThat(transposed).isEqualTo("Bbmaj7/G");
    }

    @Test
    void transposesBracketedChordsAndChordOnlyLinesWhilePreservingLyricsAndSections() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("C", KeyMode.MAJOR);
        MusicalKey targetKey = new MusicalKey("D", KeyMode.MAJOR);
        String content = "[Verse]\n[C]Rise with hope [F]walk in light [G]home again [C]\n\n[Intro]\nC F G C\n";

        // Act
        String transposed = transposer.transposeChordSheet(baseKey, targetKey, content);

        // Assert
        assertThat(transposed).isEqualTo(
                "[Verse]\n[D]Rise with hope [G]walk in light [A]home again [D]\n\n[Intro]\nD G A D\n");
    }

    @Test
    void transposesParsedChordMapJsonWithoutDroppingStructure() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("C", KeyMode.MAJOR);
        MusicalKey targetKey = new MusicalKey("Bb", KeyMode.MAJOR);
        String chordMapJson = "["
                + "{\"chord\":\"C\",\"line\":2,\"characterOffset\":0},"
                + "{\"chord\":\"Am7\",\"line\":2,\"characterOffset\":15}"
                + "]";

        // Act
        String transposed = transposer.transposeChordMapJson(baseKey, targetKey, chordMapJson);

        // Assert
        assertThat(transposed)
                .contains("\"chord\":\"Bb\"")
                .contains("\"chord\":\"Gm7\"")
                .contains("\"line\":2")
                .contains("\"characterOffset\":15");
    }

    @Test
    void rejectsUnsupportedKeysAndNotationWithClearErrors() {
        // Arrange
        MusicalKey baseKey = new MusicalKey("C", KeyMode.MAJOR);
        MusicalKey targetKey = new MusicalKey("D", KeyMode.MAJOR);

        // Act / Assert
        assertThatThrownBy(() -> transposer.transposeChord(new MusicalKey("Cb", KeyMode.MAJOR), targetKey, "C"))
                .isInstanceOf(TranspositionException.class)
                .hasMessageContaining("base key tonic Cb is not supported");
        assertThatThrownBy(() -> transposer.transposeChord(baseKey, new MusicalKey("D", KeyMode.MODAL), "C"))
                .isInstanceOf(TranspositionException.class)
                .hasMessageContaining("target key mode MODAL is not transposable");
        assertThatThrownBy(() -> transposer.transposeChord(baseKey, targetKey, "B#"))
                .isInstanceOf(TranspositionException.class)
                .hasMessageContaining("B#")
                .hasMessageContaining("unsupported pitch token");
        assertThatThrownBy(() -> transposer.transposeChord(baseKey, targetKey, "C/G/B"))
                .isInstanceOf(TranspositionException.class)
                .hasMessageContaining("multiple slash bass notes");
        assertThatThrownBy(() -> transposer.transposeChord(baseKey, targetKey, "1"))
                .isInstanceOf(TranspositionException.class)
                .hasMessageContaining("Nashville-style notation is not supported");
    }
}
