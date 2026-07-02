package com.cadentia.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SuggestionType;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovedLexicalSearchServiceTest {

    private static final UUID INSTANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID APPROVED_SONG_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID APPROVED_ARRANGEMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void normalizesDiacriticsPunctuationWhitespaceAndScriptureAbbreviations() {
        // Arrange / Act / Assert
        assertThat(SearchNormalizer.normalizeText("  Café—Hallelujah!! ")).isEqualTo("cafe hallelujah");
        assertThat(SearchNormalizer.parseScriptureReferences("Jn. 3:16-17")).containsExactly(
                new NormalizedScriptureReference("john", 3, 16, 17));
        assertThat(SearchNormalizer.parseScriptureReferences("Ps 23")).containsExactly(
                new NormalizedScriptureReference("psalms", 23, null, null));
    }

    @Test
    void searchesStableFieldsAndRanksExactTitleAboveFuzzyTypoMatch() {
        // Arrange
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(
                approved("King of Kings", List.of("Praise the King"), "C", 72, "Acoustic", true),
                approved("Kingdom Come", List.of(), "G", 96, "Full Band", true)));

        // Act
        var titleResults = service.search(new SearchQuery(INSTANCE_ID, "king of kings", null, null, null, null, null, null, null, null));
        var typoResults = service.search(new SearchQuery(INSTANCE_ID, "king of kngs", null, null, null, null, null, null, null, null));

        // Assert
        assertThat(titleResults).extracting("title").containsExactly("King of Kings");
        assertThat(typoResults).extracting("title").contains("King of Kings");
        assertThat(typoResults).extracting("title").doesNotContain("Kingdom Come");
    }

    @Test
    void supportsScriptureTagsContributorsKeysBpmRangesAndArrangementMetadata() {
        // Arrange
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(
                approved("Living Hope", List.of(), "Bb", 74, "Capo 3 Acoustic", true)));

        // Act / Assert
        assertThat(service.search(new SearchQuery(INSTANCE_ID, null, null, "Psalm 23", null, null, null, null, null, null))).hasSize(1);
        assertThat(service.search(new SearchQuery(INSTANCE_ID, null, null, null, "resurrection", null, null, null, null, null))).hasSize(1);
        assertThat(service.search(new SearchQuery(INSTANCE_ID, null, null, null, null, "Phil Wickham", null, null, null, null))).hasSize(1);
        assertThat(service.search(new SearchQuery(INSTANCE_ID, null, null, null, null, null, "B flat", 70, 80, "capo"))).hasSize(1);
        assertThat(service.search(new SearchQuery(INSTANCE_ID, null, null, null, null, null, null, 90, 100, null))).isEmpty();
    }

    @Test
    void autocompleteOnlyReturnsAuthorizedApprovedActiveVisibleLicensedSuggestionsWithContext() {
        // Arrange
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(
                approved("Amazing Grace", List.of("My Chains Are Gone"), "G", 82, "Hymn Arrangement", true),
                approved("Amazing Private", List.of(), "G", 82, "Private", false)));

        // Act
        var suggestions = service.autocomplete(INSTANCE_ID, "ama", 10);

        // Assert
        assertThat(suggestions).extracting("value").contains("Amazing Grace").doesNotContain("Amazing Private");
        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.type()).isEqualTo(SuggestionType.TITLE);
            assertThat(suggestion.matchedText()).isEqualTo("ama");
            assertThat(suggestion.songId()).isEqualTo(APPROVED_SONG_ID);
        });
        assertThat(service.autocomplete(INSTANCE_ID, "psa", 10)).extracting("type").contains(SuggestionType.SCRIPTURE);
        assertThat(service.autocomplete(INSTANCE_ID, "res", 10)).extracting("type").contains(SuggestionType.TAG);
    }

    private static ApprovedSearchDocument approved(
            String title, List<String> alternates, String key, Integer bpm, String arrangementLabel, boolean safe) {
        return new ApprovedSearchDocument(
                safe ? APPROVED_SONG_ID : UUID.randomUUID(),
                safe ? APPROVED_ARRANGEMENT_ID : UUID.randomUUID(),
                INSTANCE_ID,
                title,
                alternates,
                List.of(new NormalizedScriptureReference("psalms", 23, null, null)),
                List.of(new TagFacet("THEME_RESURRECTION", "Resurrection")),
                List.of("Phil Wickham"),
                key,
                bpm,
                arrangementLabel,
                List.of("capo", arrangementLabel),
                List.of("approved sections only"),
                safe,
                safe,
                safe,
                safe);
    }
}
