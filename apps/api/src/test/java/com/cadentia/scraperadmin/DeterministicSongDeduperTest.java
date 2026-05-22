package com.cadentia.scraperadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicSongDeduperTest {

    private final TitleNormalizer titleNormalizer = new TitleNormalizer();
    private final LyricsHasher lyricsHasher = new LyricsHasher();
    private final SongDeduper deduper = new DeterministicSongDeduper();

    @Test
    void normalizesTitleVariantsByCasePunctuationWhitespaceAndParentheticalText() {
        // Arrange
        String firstVariant = "  Holy, Holy, Holy! (Live)  ";
        String secondVariant = "holy-holy-holy";
        String thirdVariant = "Hóly & Mighty (Acoustic Version)";

        // Act / Assert
        assertThat(titleNormalizer.normalize(firstVariant)).isEqualTo("holy-holy-holy");
        assertThat(titleNormalizer.normalize(secondVariant)).isEqualTo("holy-holy-holy");
        assertThat(titleNormalizer.normalize(thirdVariant)).isEqualTo("holy-and-mighty");
    }

    @Test
    void refusesToNormalizeBlankTitles() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> titleNormalizer.normalize(" () "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title must contain alphanumeric text");
    }

    @Test
    void hashesLyricsOnlyWhenSourceTextIsAllowed() {
        // Arrange
        String fixtureText = "alpha beta\n gamma";

        // Act / Assert
        assertThat(lyricsHasher.hashAllowedSourceText(fixtureText, false)).isEmpty();
        assertThat(lyricsHasher.hashAllowedSourceText("  ALPHA   beta gamma  ", true))
                .contains(lyricsHasher.hashAllowedSourceText(fixtureText, true).orElseThrow());
        assertThat(lyricsHasher.hashAllowedSourceText(fixtureText, true).orElseThrow())
                .startsWith("sha256:");
    }

    @Test
    void ccliExactMatchProducesStrongExplainableSuggestionWithoutAutomaticMerge() {
        // Arrange
        UUID songId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ImportSongCandidate candidate = new ImportSongCandidate(
                "Great Is Thy Faithfulness (Live)",
                titleNormalizer.normalize("Great Is Thy Faithfulness (Live)"),
                "Fixture Worship",
                " 18723 ",
                null);
        CatalogSongCandidate catalogSong = new CatalogSongCandidate(
                songId,
                "Great Is Thy Faithfulness",
                "great-is-thy-faithfulness",
                "Fixture Worship Band",
                "18723",
                null);

        // Act
        List<DuplicateSuggestion> suggestions = deduper.suggestDuplicates(candidate, List.of(catalogSong));

        // Assert
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().songId()).isEqualTo(songId);
        assertThat(suggestions.getFirst().automaticMergeAllowed()).isFalse();
        assertThat(suggestions.getFirst().signals())
                .anySatisfy(signal -> {
                    assertThat(signal.name()).isEqualTo("ccliNumber");
                    assertThat(signal.matched()).isTrue();
                    assertThat(signal.explanation()).contains("Exact CCLI");
                })
                .anySatisfy(signal -> {
                    assertThat(signal.name()).isEqualTo("normalizedTitle");
                    assertThat(signal.matched()).isTrue();
                });
    }

    @Test
    void lyricsHashExactMatchSuggestsDuplicateWhenTitleMetadataIsSparse() {
        // Arrange
        String lyricsHash = lyricsHasher.hashAllowedSourceText("fixture refrain words", true).orElseThrow();
        UUID songId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ImportSongCandidate candidate = new ImportSongCandidate(
                "Unknown Source Title",
                null,
                null,
                null,
                lyricsHash);
        CatalogSongCandidate catalogSong = new CatalogSongCandidate(
                songId,
                "Canonical Fixture Song",
                "canonical-fixture-song",
                null,
                null,
                lyricsHash);

        // Act
        List<DuplicateSuggestion> suggestions = deduper.suggestDuplicates(candidate, List.of(catalogSong));

        // Assert
        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.songId()).isEqualTo(songId);
            assertThat(suggestion.signals())
                    .anySatisfy(signal -> {
                        assertThat(signal.name()).isEqualTo("lyricsHash");
                        assertThat(signal.matched()).isTrue();
                        assertThat(signal.explanation()).contains("allowed-source lyrics hash");
                        assertThat(signal.fingerprintSupportSignal()).isNotNull();
                        assertThat(signal.fingerprintSupportSignal().signalCode()).isEqualTo("FP_LYRICS_HASH_EXACT");
                    });
        });
    }

    @Test
    void missingMetadataDoesNotPreventTitleAndArtistSuggestion() {
        // Arrange
        UUID songId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        ImportSongCandidate candidate = new ImportSongCandidate(
                "Blessed Assurance!!!",
                null,
                "Fanny Crosby",
                null,
                null);
        CatalogSongCandidate catalogSong = new CatalogSongCandidate(
                songId,
                "Blessed Assurance",
                "blessed-assurance",
                "Fanny Crosby",
                null,
                null);

        // Act
        List<DuplicateSuggestion> suggestions = deduper.suggestDuplicates(candidate, List.of(catalogSong));

        // Assert
        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.score()).isEqualByComparingTo("0.4000");
            assertThat(suggestion.signals())
                    .anySatisfy(signal -> assertThat(signal.name()).isEqualTo("artistSimilarity"));
        });
    }

    @Test
    void nearMissDoesNotReturnDuplicateSuggestion() {
        // Arrange
        ImportSongCandidate candidate = new ImportSongCandidate(
                "Holy Forever",
                null,
                "Fixture Worship Collective",
                null,
                null);
        CatalogSongCandidate catalogSong = new CatalogSongCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "Forever Holy Are You",
                "forever-holy-are-you",
                "Different Artist",
                null,
                null);

        // Act
        List<DuplicateSuggestion> suggestions = deduper.suggestDuplicates(candidate, List.of(catalogSong));

        // Assert
        assertThat(suggestions).isEmpty();
    }

    @Test
    void suggestionsAreDeterministicallyOrderedByScoreThenSongId() {
        // Arrange
        ImportSongCandidate candidate = new ImportSongCandidate(
                "Same Song",
                null,
                "Fixture Artist",
                "123",
                null);
        CatalogSongCandidate lowerId = new CatalogSongCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "Same Song",
                "same-song",
                "Fixture Artist",
                "123",
                null);
        CatalogSongCandidate higherId = new CatalogSongCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                "Same Song",
                "same-song",
                "Fixture Artist",
                "123",
                null);

        // Act
        List<DuplicateSuggestion> suggestions = deduper.suggestDuplicates(candidate, List.of(higherId, lowerId));

        // Assert
        assertThat(suggestions).extracting(DuplicateSuggestion::songId)
                .containsExactly(lowerId.songId(), higherId.songId());
    }
}
