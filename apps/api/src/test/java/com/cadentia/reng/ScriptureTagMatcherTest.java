package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.scripture.CanonicalScriptureReference.MatchTier;
import com.cadentia.reng.scoring.ScoringRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScriptureTagMatcherTest {

    @Test
    void matchesAbbreviatedQueryAgainstWiderTagRange() {
        // Arrange
        ScriptureTagMatcher matcher = ScriptureTagMatcher.fromRawValues(List.of("Phil 4:13"));
        RecommendationTag tag = scriptureTag("Philippians 4:10-20", "philippians-4-10-20");

        // Act / Assert
        assertThat(matcher.requested()).isTrue();
        assertThat(matcher.hasParsedQueries()).isTrue();
        assertThat(matcher.bestTier(tag)).isEqualTo(MatchTier.EXACT_OR_OVERLAP);
        assertThat(matcher.matches(tag)).isTrue();
    }

    @Test
    void matchesChapterAndBookLevelTagsWithLowerTiers() {
        // Arrange
        ScriptureTagMatcher matcher = ScriptureTagMatcher.fromRawValues(List.of("Philippians 4:13"));

        // Act / Assert
        assertThat(matcher.bestTier(scriptureTag("Philippians 4", "philippians-4")))
                .isEqualTo(MatchTier.CHAPTER);
        assertThat(matcher.bestTier(scriptureTag("Philippians", "philippians")))
                .isEqualTo(MatchTier.BOOK);
        assertThat(matcher.bestTier(scriptureTag("Philippians 3", "philippians-3")))
                .isEqualTo(MatchTier.NONE);
    }

    @Test
    void doesNotMatchNonScriptureTagsOrUnrelatedReferences() {
        // Arrange
        ScriptureTagMatcher matcher = ScriptureTagMatcher.fromRawValues(List.of("Philippians 4:13"));
        RecommendationTag theme = new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Repentance", "repentance");
        RecommendationTag unrelated = scriptureTag("Romans 8:28", "romans-8-28");

        // Act / Assert
        assertThat(matcher.matches(theme)).isFalse();
        assertThat(matcher.matches(unrelated)).isFalse();
    }

    @Test
    void fallsBackToSubstringMatchingForUnparsableQueries() {
        // Arrange
        ScriptureTagMatcher matcher = ScriptureTagMatcher.fromRawValues(List.of("sweet hour of prayer"));
        RecommendationTag tag = scriptureTag("Sweet Hour of Prayer", "sweet-hour-of-prayer");

        // Act / Assert
        assertThat(matcher.requested()).isTrue();
        assertThat(matcher.hasParsedQueries()).isFalse();
        assertThat(matcher.matches(tag)).isTrue();
        assertThat(matcher.matches(scriptureTag("John 3:16", "john-3-16"))).isFalse();
    }

    @Test
    void reportsNotRequestedForBlankOrMissingValues() {
        // Arrange / Act / Assert
        assertThat(ScriptureTagMatcher.fromRawValues(null).requested()).isFalse();
        assertThat(ScriptureTagMatcher.fromRawValues(List.of(" ", "")).requested()).isFalse();
    }

    @Test
    void combinesVerseTextAndScriptureReferencesFromRequest() {
        // Arrange
        ScoringRequest request = new ScoringRequest(
                "Psalm 100",
                List.of("Ephesians 6:11"),
                List.of(),
                10,
                5,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                null,
                null,
                null);

        // Act
        ScriptureTagMatcher matcher = ScriptureTagMatcher.fromRequest(request);

        // Assert
        assertThat(matcher.matches(scriptureTag("Psalm 100:4", "psalm-100-4"))).isTrue();
        assertThat(matcher.matches(scriptureTag("Ephesians 6:10-18", "ephesians-6-10-18"))).isTrue();
        assertThat(matcher.matches(scriptureTag("Romans 8:28", "romans-8-28"))).isFalse();
    }

    private static RecommendationTag scriptureTag(String name, String slug) {
        return new RecommendationTag(UUID.randomUUID(), TagType.SCRIPTURE, name, slug);
    }
}
