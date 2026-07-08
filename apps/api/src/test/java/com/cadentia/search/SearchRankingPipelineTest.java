package com.cadentia.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SearchRankingProfile;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchRankingPipelineTest {

    private static final UUID INSTANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void exactApprovedCatalogSignalsOutrankSemanticOnlyMatches() {
        // Arrange
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(
                document("Doxology", 0.10d, null, false),
                document("Semantic Neighbor", 1.0d, null, false)));

        // Act
        var results = service.search(new SearchQuery(INSTANCE_ID, "Doxology", null, null, null, null, null, null, null, null));

        // Assert
        assertThat(results).extracting("title").containsExactly("Doxology", "Semantic Neighbor");
        assertThat(results.get(0).rankingFactors()).extracting("code").contains("exactTitleMatch", "semanticSimilarity");
        assertThat(results.get(1).rankingFactors()).extracting("code").containsExactly("semanticSimilarity");
    }

    @Test
    void familiaritySignalRequiresChurchPolicyEnabled() {
        // Arrange
        ApprovedSearchDocument familiar = document("Familiar Song", null, 1.0d, false);
        ApprovedLexicalSearchService disabled = new ApprovedLexicalSearchService(List.of(familiar));
        SearchRankingProfile enabledProfile = new SearchRankingProfile(
                "search-ranking-v1-test", true, false, false, ApprovedLexicalSearchService.DEFAULT_RANKING_PROFILE.weights());
        ApprovedLexicalSearchService enabled = new ApprovedLexicalSearchService(List.of(familiar), new SearchEligibilityPolicy(), enabledProfile);

        // Act / Assert
        assertThat(disabled.search(new SearchQuery(INSTANCE_ID, null, null, null, null, null, null, null, null, null))).isEmpty();
        assertThat(enabled.search(new SearchQuery(INSTANCE_ID, null, null, null, null, null, null, null, null, null)))
                .singleElement()
                .satisfies(result -> assertThat(result.rankingFactors()).extracting("code").containsExactly("familiaritySignal"));
    }

    @Test
    void explanationsExposeOnlySafeFactorCodesAndHandleAbsentSemanticScore() {
        // Arrange
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(document("Living Hope", null, null, false)));
        SearchActor actor = new SearchActor("leader", INSTANCE_ID, Set.of("role.worship_leader"), Set.of(), true);

        // Act
        var results = service.search(actor, new SearchQuery(INSTANCE_ID, "Living", null, null, "resurrection", "Phil", "G", 70, 80, "acoustic"));
        var explanations = service.explanations(actor, results);

        // Assert
        assertThat(results).singleElement().satisfies(result -> assertThat(result.rankingFactors())
                .extracting("code")
                .contains("prefixTitleMatch", "curatedTagMatch", "contributorMatch", "musicalFeatureMatch", "arrangementMatch")
                .doesNotContain("semanticSimilarity", "privateNotes"));
        assertThat(explanations).singleElement().satisfies(explanation -> assertThat(explanation.factors())
                .contains("prefixTitleMatch", "curatedTagMatch")
                .doesNotContain("Living", "Phil Wickham"));
    }

    @Test
    void diagnosticsRequireSupportRoleAndRedactQueryText() {
        // Arrange
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(document("Great Is Thy Faithfulness", null, null, false)));
        SearchQuery sensitiveQuery = new SearchQuery(INSTANCE_ID, "private pastoral counseling phrase", null, null, null, null, null, null, null, null);
        SearchActor leader = new SearchActor("leader", INSTANCE_ID, Set.of("role.worship_leader"), Set.of(), true);
        SearchActor support = new SearchActor("support", INSTANCE_ID, Set.of("ROLE_SUPPORT"), Set.of(), true);

        // Act / Assert
        assertThatThrownBy(() -> service.diagnostics(leader, sensitiveQuery)).isInstanceOf(SecurityException.class);
        assertThat(service.diagnostics(support, sensitiveQuery).queryTextRedacted()).isTrue();
    }

    @Test
    void deterministicTieBreakersRemainStableAcrossIndexOrder() {
        // Arrange
        ApprovedSearchDocument alpha = document("Alpha Song", null, null, false);
        ApprovedSearchDocument beta = document("Beta Song", null, null, false);
        SearchQuery tagQuery = new SearchQuery(INSTANCE_ID, null, null, null, "resurrection", null, null, null, null, null);

        // Act
        var firstOrder = new ApprovedLexicalSearchService(List.of(beta, alpha)).search(tagQuery);
        var rebuiltOrder = new ApprovedLexicalSearchService(List.of(alpha, beta)).search(tagQuery);

        // Assert
        assertThat(firstOrder).extracting("title").containsExactly("Alpha Song", "Beta Song");
        assertThat(rebuiltOrder).extracting("title").containsExactly("Alpha Song", "Beta Song");
    }

    private static ApprovedSearchDocument document(String title, Double semanticSimilarity, Double familiaritySignal, boolean starterPackage) {
        return new ApprovedSearchDocument(
                UUID.nameUUIDFromBytes(title.getBytes()),
                UUID.nameUUIDFromBytes((title + " arrangement").getBytes()),
                INSTANCE_ID,
                title,
                List.of("Approved Alternate " + title),
                List.of(new NormalizedScriptureReference("psalms", 23, null, null)),
                List.of(new TagFacet("THEME_RESURRECTION", "Resurrection")),
                List.of("Phil Wickham"),
                "G",
                74,
                "Acoustic",
                List.of("acoustic"),
                List.of("approved search metadata"),
                true,
                true,
                true,
                true,
                true,
                ApprovedSearchModels.SearchVisibilityPolicy.PUBLIC,
                Set.of(),
                Set.of(),
                semanticSimilarity,
                familiaritySignal,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                starterPackage);
    }
}
