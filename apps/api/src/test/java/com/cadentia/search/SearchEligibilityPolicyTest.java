package com.cadentia.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.SearchProjectionEventReason;
import com.cadentia.search.ApprovedSearchModels.SearchResult;
import com.cadentia.search.ApprovedSearchModels.SearchVisibilityPolicy;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchEligibilityPolicyTest {

    private static final UUID INSTANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_INSTANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID SONG_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final SearchActor WORSHIP_LEADER = new SearchActor(
            "leader", INSTANCE_ID, Set.of("role.worship_leader"), Set.of(), true);

    @Test
    void blocksEverySearchSurfaceBeforeCountsFacetsSuggestionsSnippetsOrExplanationsAreBuilt() {
        // Arrange
        ApprovedSearchDocument eligible = document("Amazing Grace", SONG_ID, INSTANCE_ID, true, true, true, true, true);
        ApprovedSearchDocument unapproved = document("Hidden Unapproved", UUID.randomUUID(), INSTANCE_ID, true, false, true, true, true);
        ApprovedSearchDocument inactive = document("Hidden Inactive", UUID.randomUUID(), INSTANCE_ID, false, true, true, true, true);
        ApprovedSearchDocument unlicensed = document("Hidden Unlicensed", UUID.randomUUID(), INSTANCE_ID, true, true, true, false, true);
        ApprovedSearchDocument packageHidden = document("Hidden Package", UUID.randomUUID(), INSTANCE_ID, true, true, true, true, false);
        ApprovedSearchDocument otherInstance = document("Visible Other Instance", UUID.randomUUID(), OTHER_INSTANCE_ID, true, true, true, true, true);
        ApprovedSearchDocument unauthorized = restricted("Hidden Unauthorized", UUID.randomUUID(), Set.of("role.admin"), Set.of());
        ApprovedSearchDocument governed = restricted("Hidden Governed", UUID.randomUUID(), Set.of("role.worship_leader"), Set.of("LOCAL_ONLY"));
        ApprovedLexicalSearchService service = new ApprovedLexicalSearchService(List.of(
                eligible, unapproved, inactive, unlicensed, packageHidden, otherInstance, unauthorized, governed));

        // Act
        var query = new ApprovedSearchModels.SearchQuery(INSTANCE_ID, "Hidden", null, null, null, null, null, null, null, null);
        var allQuery = new ApprovedSearchModels.SearchQuery(INSTANCE_ID, "Amazing", null, null, null, null, null, null, null, null);
        var otherInstanceQuery = new ApprovedSearchModels.SearchQuery(INSTANCE_ID, "Visible Other", null, null, null, null, null, null, null, null);
        var results = service.search(WORSHIP_LEADER, query);
        var counts = service.search(WORSHIP_LEADER, query).size();
        var facets = service.facets(WORSHIP_LEADER, query);
        var suggestions = service.autocomplete(WORSHIP_LEADER, "hid", 20);
        var spellingCorrections = service.spellingCorrections(WORSHIP_LEADER, "hid", 20);
        var semantic = service.semanticNeighbors(WORSHIP_LEADER, List.of(
                unapproved.songId(), inactive.songId(), unlicensed.songId(), otherInstance.songId(), eligible.songId()), 10);
        var hydrated = service.hydrate(WORSHIP_LEADER, List.of(
                new SearchResult(unapproved.songId(), unapproved.arrangementId(), unapproved.title(), null, 99),
                new SearchResult(eligible.songId(), eligible.arrangementId(), eligible.title(), null, 1)));
        var explanations = service.explanations(WORSHIP_LEADER, hydrated);

        // Assert
        assertThat(results).isEmpty();
        assertThat(counts).isZero();
        assertThat(facets).isEmpty();
        assertThat(suggestions).isEmpty();
        assertThat(spellingCorrections).isEmpty();
        assertThat(semantic).extracting("title").containsExactly("Amazing Grace", "Visible Other Instance");
        assertThat(hydrated).extracting("title").containsExactly("Amazing Grace");
        assertThat(explanations).extracting("songId").containsExactly(SONG_ID);
        assertThat(service.search(WORSHIP_LEADER, allQuery)).hasSize(1);
        assertThat(service.search(WORSHIP_LEADER, otherInstanceQuery)).extracting("title").containsExactly("Visible Other Instance");
    }

    @Test
    void privilegedAdministrativeSearchRequiresSeparatePolicyPath() {
        // Arrange
        SearchEligibilityPolicy policy = new SearchEligibilityPolicy();
        SearchActor admin = new SearchActor("admin", INSTANCE_ID, Set.of("role.admin"), Set.of("LOCAL_ONLY"), true);
        ApprovedSearchDocument restricted = restricted("Admin Visible", SONG_ID, Set.of("role.admin"), Set.of("LOCAL_ONLY"));

        // Act / Assert
        assertThat(policy.canReturn(WORSHIP_LEADER, restricted)).isFalse();
        assertThat(policy.canReturn(admin, restricted)).isTrue();
    }

    @Test
    void eligibilityChangesAreQueuedWithinRefreshTargetForProjectionInvalidation() {
        // Arrange
        Clock clock = Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);
        SearchProjectionInvalidationService service = new SearchProjectionInvalidationService(clock);

        // Act / Assert
        for (SearchProjectionEventReason reason : List.of(
                SearchProjectionEventReason.APPROVAL_STATE_CHANGED,
                SearchProjectionEventReason.ACTIVE_STATUS_CHANGED,
                SearchProjectionEventReason.LICENSING_CHANGED,
                SearchProjectionEventReason.PACKAGE_VISIBILITY_CHANGED,
                SearchProjectionEventReason.INSTANCE_VISIBILITY_CHANGED)) {
            assertThat(service.recordEligibilityChange(SONG_ID, reason).dueAt())
                    .isEqualTo(Instant.parse("2026-07-07T00:05:00Z"));
        }
        assertThat(service.pendingInvalidations()).hasSize(5);
    }

    private static ApprovedSearchDocument document(
            String title,
            UUID songId,
            UUID instanceId,
            boolean active,
            boolean approved,
            boolean visible,
            boolean licensed,
            boolean packageVisible) {
        return new ApprovedSearchDocument(
                songId,
                UUID.randomUUID(),
                instanceId,
                title,
                List.of(),
                List.of(new NormalizedScriptureReference("psalms", 23, null, null)),
                List.of(new TagFacet("VISIBLE", "Visible")),
                List.of("Contributor"),
                "G",
                80,
                "Arrangement",
                List.of("arrangement metadata"),
                List.of("safe metadata"),
                active,
                approved,
                visible,
                licensed,
                packageVisible,
                SearchVisibilityPolicy.PUBLIC,
                Set.of(),
                Set.of());
    }

    private static ApprovedSearchDocument restricted(String title, UUID songId, Set<String> authorizedRoles, Set<String> governanceCodes) {
        return new ApprovedSearchDocument(
                songId,
                UUID.randomUUID(),
                INSTANCE_ID,
                title,
                List.of(),
                List.of(),
                List.of(new TagFacet("LOCAL_ONLY", "Local Only")),
                List.of(),
                "G",
                80,
                "Arrangement",
                List.of(),
                List.of(),
                true,
                true,
                true,
                true,
                true,
                SearchVisibilityPolicy.RESTRICTED,
                authorizedRoles,
                governanceCodes);
    }
}
