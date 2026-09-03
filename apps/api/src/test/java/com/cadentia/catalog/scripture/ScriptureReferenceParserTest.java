package com.cadentia.catalog.scripture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScriptureReferenceParserTest {

    @Test
    void parsesAbbreviatedBookWithVerseRange() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("Jn. 3:16-17")).containsExactly(
                new CanonicalScriptureReference("john", 3, 16, 17));
        assertThat(ScriptureReferenceParser.parse("Phil 4:13")).containsExactly(
                new CanonicalScriptureReference("philippians", 4, 13, 13));
        assertThat(ScriptureReferenceParser.parse("Ps 23")).containsExactly(
                new CanonicalScriptureReference("psalms", 23, null, null));
    }

    @Test
    void parsesNumberedBooks() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("1 Corinthians 13:4-7")).containsExactly(
                new CanonicalScriptureReference("1 corinthians", 13, 4, 7));
        assertThat(ScriptureReferenceParser.parse("2Cor 5:17")).containsExactly(
                new CanonicalScriptureReference("2 corinthians", 5, 17, 17));
    }

    @Test
    void parsesSlugStyleReferences() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("john-3-16")).containsExactly(
                new CanonicalScriptureReference("john", 3, 16, 16));
        assertThat(ScriptureReferenceParser.parse("philippians-4-10-20")).containsExactly(
                new CanonicalScriptureReference("philippians", 4, 10, 20));
    }

    @Test
    void parsesBookOnlyReference() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("Psalms")).containsExactly(
                new CanonicalScriptureReference("psalms", null, null, null));
    }

    @Test
    void parsesMultipleReferencesFromOneString() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("John 3:16 and Romans 8:28")).containsExactly(
                new CanonicalScriptureReference("john", 3, 16, 16),
                new CanonicalScriptureReference("romans", 8, 28, 28));
    }

    @Test
    void normalizesMultiwordBooks() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("Song of Solomon 2:1")).containsExactly(
                new CanonicalScriptureReference("song of solomon", 2, 1, 1));
    }

    @Test
    void clampsInvertedVerseRanges() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse("John 3:16-10")).containsExactly(
                new CanonicalScriptureReference("john", 3, 16, 16));
    }

    @Test
    void returnsEmptyForBlankUnparsableOrUnknownBooks() {
        // Arrange / Act / Assert
        assertThat(ScriptureReferenceParser.parse(null)).isEmpty();
        assertThat(ScriptureReferenceParser.parse("")).isEmpty();
        assertThat(ScriptureReferenceParser.parse("I can do all things through Christ")).isEmpty();
        assertThat(ScriptureReferenceParser.parse("Westeros 4:13")).isEmpty();
    }
}
