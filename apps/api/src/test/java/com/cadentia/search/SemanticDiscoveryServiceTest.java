package com.cadentia.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.SearchProjectionEventReason;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SearchRankingProfile;
import com.cadentia.search.ApprovedSearchModels.SearchVisibilityPolicy;
import com.cadentia.search.ApprovedSearchModels.SemanticDiscoveryMode;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SemanticDiscoveryServiceTest {

    private static final UUID INSTANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final SearchActor ACTOR = new SearchActor("leader", INSTANCE_ID, Set.of("role.worship_leader"), Set.of(), true);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-08T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void embeddingSourceIncludesOnlyApprovedSemanticMetadataAndRejectsHiddenDocuments() {
        // Arrange
        var service = new SemanticDiscoveryService(new SearchEligibilityPolicy(), CLOCK, "provider", "model-v2", 3,
                SemanticDiscoveryMode.ENABLED, Set.of());
        var document = document("Living Hope", true, true, true, true, true);
        var hidden = document("Hidden Hope", true, true, false, true, true);

        // Act
        var source = service.sourceDocument(ACTOR, document);

        // Assert
        assertThat(source.allowedFields()).contains(
                "title: living hope",
                "alternateTitle: approved alternate",
                "tag: theme resurrection",
                "tag: resurrection",
                "scripture: psalms 23",
                "contributor: phil wickham",
                "approvedLyricsFeature: approved summary resurrection hope");
        assertThat(String.join(" ", source.allowedFields()))
                .doesNotContain("private", "rehearsal", "feedback", "full lyrics");
        assertThatThrownBy(() -> service.sourceDocument(ACTOR, hidden))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("semanticSourceRequiresEligibleApprovedDocument");
    }

    @Test
    void embeddingRecordTracksModelVersionSourceHashProjectionScopeAndEligibility() {
        // Arrange
        var service = new SemanticDiscoveryService(new SearchEligibilityPolicy(), CLOCK, "openai-residency-us", "text-embedding-3-small@2026-06", 7,
                SemanticDiscoveryMode.ENABLED, Set.of());

        // Act
        var record = service.embeddingRecord(ACTOR, document("Living Hope", true, true, true, true, true));

        // Assert
        assertThat(record.providerIdentifier()).isEqualTo("openai-residency-us");
        assertThat(record.modelVersion()).isEqualTo("text-embedding-3-small@2026-06");
        assertThat(record.sourceHash()).hasSize(64);
        assertThat(record.generatedAt()).isEqualTo(Instant.parse("2026-07-08T12:00:00Z"));
        assertThat(record.projectionVersion()).isEqualTo(7);
        assertThat(record.instanceId()).isEqualTo(INSTANCE_ID);
        assertThat(record.approved()).isTrue();
        assertThat(record.visible()).isTrue();
    }

    @Test
    void invalidatesApprovalVisibilityLicensingAndSourceMetadataWithinRefreshTarget() {
        // Arrange
        var service = new SemanticDiscoveryService(new SearchEligibilityPolicy(), CLOCK, "provider", "model", 1,
                SemanticDiscoveryMode.ENABLED, Set.of());

        // Act / Assert
        assertThat(service.invalidation(UUID.randomUUID(), SearchProjectionEventReason.APPROVAL_STATE_CHANGED).dueAt())
                .isEqualTo(Instant.parse("2026-07-08T12:05:00Z"));
        assertThat(service.invalidates(SearchProjectionEventReason.ACTIVE_STATUS_CHANGED)).isTrue();
        assertThat(service.invalidates(SearchProjectionEventReason.INSTANCE_VISIBILITY_CHANGED)).isTrue();
        assertThat(service.invalidates(SearchProjectionEventReason.LICENSING_CHANGED)).isTrue();
        assertThat(service.invalidates(SearchProjectionEventReason.SOURCE_METADATA_CHANGED)).isTrue();
    }

    @Test
    void disabledSemanticModeDoesNotBreakLexicalSearchOrAddSemanticFactor() {
        // Arrange
        var document = document("Doxology", true, true, true, true, true);
        var semantic = new SemanticDiscoveryService(new SearchEligibilityPolicy(), CLOCK, "provider", "model", 1,
                SemanticDiscoveryMode.DISABLED, Set.of());
        var lexical = new ApprovedLexicalSearchService(List.of(document), new SearchEligibilityPolicy(),
                new SearchRankingProfile("disabled-semantic", false, false, false, Map.of("exactTitleMatch", 30.0d, "semanticSimilarity", 7.0d)), false);

        // Act / Assert
        assertThat(semantic.search(ACTOR, new SearchQuery(INSTANCE_ID, "resurrection hope", null, null, null, null, null, null, null, null),
                List.of(document), List.of(), 10)).isEmpty();
        assertThat(lexical.search(ACTOR, new SearchQuery(INSTANCE_ID, "Doxology", null, null, null, null, null, null, null, null)))
                .singleElement()
                .satisfies(result -> assertThat(result.rankingFactors()).extracting("code").containsExactly("exactTitleMatch"));
    }

    @Test
    void semanticAndLexicalCandidatesMergeOnlyAfterEligibilityAndExcludeHiddenRecords() {
        // Arrange
        var lexicalDocument = document("Doxology", true, true, true, true, true);
        var semanticDocument = document("Resurrection Hope", true, true, true, true, true);
        var hiddenDocument = document("Hidden Resurrection", true, true, false, true, true);
        var semantic = new SemanticDiscoveryService(new SearchEligibilityPolicy(), CLOCK, "provider", "model", 1,
                SemanticDiscoveryMode.ENABLED, Set.of());
        var embeddings = List.of(
                semantic.embeddingRecord(ACTOR, semanticDocument),
                semantic.embeddingRecord(ACTOR, lexicalDocument));
        var lexicalResults = new ApprovedLexicalSearchService(List.of(lexicalDocument, semanticDocument, hiddenDocument))
                .search(ACTOR, new SearchQuery(INSTANCE_ID, "Doxology", null, null, null, null, null, null, null, null));

        // Act
        var semanticResults = semantic.search(ACTOR, new SearchQuery(INSTANCE_ID, "resurrection hope", null, null, null, null, null, null, null, null),
                List.of(lexicalDocument, semanticDocument, hiddenDocument), embeddings, 10);

        // Assert
        assertThat(lexicalResults).extracting("title").contains("Doxology");
        assertThat(semanticResults).extracting("title").contains("Resurrection Hope").doesNotContain("Hidden Resurrection");
        assertThat(semanticResults).allSatisfy(result -> assertThat(result.rankingFactors()).extracting("code").containsExactly("semanticSimilarity"));
    }

    private static ApprovedSearchDocument document(String title, boolean active, boolean approved, boolean visible, boolean licensed, boolean packageVisible) {
        return new ApprovedSearchDocument(
                UUID.nameUUIDFromBytes(title.getBytes()),
                UUID.nameUUIDFromBytes((title + " arrangement").getBytes()),
                INSTANCE_ID,
                title,
                List.of("Approved Alternate"),
                List.of(new NormalizedScriptureReference("psalms", 23, null, null)),
                List.of(new TagFacet("THEME_RESURRECTION", "Resurrection")),
                List.of("Phil Wickham"),
                "G",
                74,
                "Acoustic",
                List.of("acoustic"),
                List.of("approved summary resurrection hope"),
                active,
                approved,
                visible,
                licensed,
                packageVisible,
                SearchVisibilityPolicy.PUBLIC,
                Set.of(),
                Set.of(),
                0.9d,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                false);
    }
}
