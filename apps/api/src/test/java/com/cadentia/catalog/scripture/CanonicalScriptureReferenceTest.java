package com.cadentia.catalog.scripture;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.scripture.CanonicalScriptureReference.MatchTier;
import org.junit.jupiter.api.Test;

class CanonicalScriptureReferenceTest {

    @Test
    void matchesExactVerseReferences() {
        // Arrange
        CanonicalScriptureReference tag = new CanonicalScriptureReference("philippians", 4, 13, 13);

        // Act / Assert
        assertThat(tag.matchTier(new CanonicalScriptureReference("philippians", 4, 13, 13)))
                .isEqualTo(MatchTier.EXACT_OR_OVERLAP);
    }

    @Test
    void matchesVerseContainedInTagRange() {
        // Arrange
        CanonicalScriptureReference tag = new CanonicalScriptureReference("philippians", 4, 10, 20);

        // Act / Assert
        assertThat(tag.matchTier(new CanonicalScriptureReference("philippians", 4, 13, 13)))
                .isEqualTo(MatchTier.EXACT_OR_OVERLAP);
        assertThat(tag.matchTier(new CanonicalScriptureReference("philippians", 4, 13, 21)))
                .isEqualTo(MatchTier.EXACT_OR_OVERLAP);
        assertThat(tag.matchTier(new CanonicalScriptureReference("philippians", 4, 21, 21)))
                .isEqualTo(MatchTier.NONE);
    }

    @Test
    void matchesChapterLevelContainment() {
        // Arrange
        CanonicalScriptureReference chapterTag = new CanonicalScriptureReference("philippians", 4, null, null);

        // Act / Assert
        assertThat(chapterTag.matchTier(new CanonicalScriptureReference("philippians", 4, 13, 13)))
                .isEqualTo(MatchTier.CHAPTER);
        assertThat(new CanonicalScriptureReference("philippians", 4, 13, 13).matchTier(chapterTag))
                .isEqualTo(MatchTier.CHAPTER);
        assertThat(chapterTag.matchTier(chapterTag)).isEqualTo(MatchTier.EXACT_OR_OVERLAP);
    }

    @Test
    void matchesBookLevelContainmentOnlyWhenEitherSideIsBookScoped() {
        // Arrange
        CanonicalScriptureReference bookTag = new CanonicalScriptureReference("psalms", null, null, null);

        // Act / Assert
        assertThat(bookTag.matchTier(new CanonicalScriptureReference("psalms", 23, 1, 1)))
                .isEqualTo(MatchTier.BOOK);
        assertThat(new CanonicalScriptureReference("psalms", 23, null, null).matchTier(bookTag))
                .isEqualTo(MatchTier.BOOK);
        assertThat(new CanonicalScriptureReference("psalms", 23, null, null)
                        .matchTier(new CanonicalScriptureReference("psalms", 24, null, null)))
                .isEqualTo(MatchTier.NONE);
    }

    @Test
    void rejectsDifferentBooksAndChapters() {
        // Arrange
        CanonicalScriptureReference tag = new CanonicalScriptureReference("philippians", 4, 13, 13);

        // Act / Assert
        assertThat(tag.matchTier(new CanonicalScriptureReference("romans", 4, 13, 13))).isEqualTo(MatchTier.NONE);
        assertThat(tag.matchTier(new CanonicalScriptureReference("philippians", 3, 13, 13))).isEqualTo(MatchTier.NONE);
    }

    @Test
    void rendersCanonicalDisplay() {
        // Arrange / Act / Assert
        assertThat(new CanonicalScriptureReference("philippians", 4, 13, 13).display()).isEqualTo("Philippians 4:13");
        assertThat(new CanonicalScriptureReference("philippians", 4, 10, 20).display()).isEqualTo("Philippians 4:10-20");
        assertThat(new CanonicalScriptureReference("philippians", 4, null, null).display()).isEqualTo("Philippians 4");
        assertThat(new CanonicalScriptureReference("psalms", null, null, null).display()).isEqualTo("Psalms");
        assertThat(new CanonicalScriptureReference("1 corinthians", 13, 4, 7).display()).isEqualTo("1 Corinthians 13:4-7");
    }
}
