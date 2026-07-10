package com.cadentia.search;

import com.cadentia.generated.model.AutocompleteSuggestion;
import com.cadentia.generated.model.CatalogAutocompleteRequest;
import com.cadentia.generated.model.CatalogAutocompleteResponse;
import com.cadentia.generated.model.CatalogSearchFilters;
import com.cadentia.generated.model.CatalogSearchRequest;
import com.cadentia.generated.model.CatalogSearchResponse;
import com.cadentia.generated.model.CatalogSearchResultSummary;
import com.cadentia.generated.model.SearchDiagnosticsSummary;
import com.cadentia.generated.model.SearchEmptyState;
import com.cadentia.generated.model.SearchFacetBucket;
import com.cadentia.generated.model.SearchFacetGroup;
import com.cadentia.generated.model.SearchMatchedField;
import com.cadentia.generated.model.SearchMatchedField.FieldEnum;
import com.cadentia.generated.model.SearchPaginationResponse;
import com.cadentia.generated.model.SearchRankingFactor;
import com.cadentia.generated.model.SearchResultHydration;
import com.cadentia.generated.model.SearchResultType;
import com.cadentia.generated.model.SearchSemanticMode;
import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SearchResult;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogSearchApplicationService {

    private static final UUID INSTANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ApprovedLexicalSearchService lexicalSearchService;

    public CatalogSearchApplicationService() {
        this(new ApprovedLexicalSearchService(seedDocuments()));
    }

    CatalogSearchApplicationService(ApprovedLexicalSearchService lexicalSearchService) {
        this.lexicalSearchService = lexicalSearchService;
    }

    public CatalogSearchResponse search(CatalogSearchRequest request) {
        validateSearch(request);
        SearchActor actor = actor();
        SearchQuery query = toQuery(request);
        List<SearchResult> allResults = lexicalSearchService.hydrate(actor, lexicalSearchService.search(actor, query));
        int pageSize = pageSize(request);
        int offset = cursorOffset(request == null || request.getPagination() == null ? null : request.getPagination().getCursor());
        List<SearchResult> page = allResults.stream().skip(offset).limit(pageSize).toList();
        boolean hasMore = offset + page.size() < allResults.size();

        CatalogSearchResponse response = new CatalogSearchResponse()
                .results(page.stream().map(result -> toSummary(result, Boolean.TRUE.equals(request.getIncludeExplanations()))).toList())
                .pagination(new SearchPaginationResponse().pageSize(pageSize).hasMore(hasMore)
                        .nextCursor(hasMore ? String.valueOf(offset + page.size()) : null))
                .emptyState(emptyState(page.isEmpty(), page.isEmpty() ? "NO_MATCHES" : null));
        if (Boolean.TRUE.equals(request.getIncludeFacets())) {
            response.facets(facets(actor, query));
        }
        if (Boolean.TRUE.equals(request.getIncludeDiagnostics())) {
            var diagnostics = lexicalSearchService.diagnostics(actor, query);
            response.diagnostics(new SearchDiagnosticsSummary()
                    .rankingVersion(diagnostics.rankingVersion())
                    .eligibleCandidateCount(diagnostics.eligibleCandidateCount())
                    .returnedResultCount(diagnostics.returnedResultCount())
                    .queryTextRedacted(diagnostics.queryTextRedacted()));
        }
        return response;
    }

    public CatalogAutocompleteResponse autocomplete(CatalogAutocompleteRequest request) {
        int limit = request.getLimit() == null ? 10 : request.getLimit();
        List<AutocompleteSuggestion> suggestions = lexicalSearchService.autocomplete(actor(), request.getPrefix(), limit).stream()
                .map(suggestion -> new AutocompleteSuggestion()
                        .type(AutocompleteSuggestion.TypeEnum.fromValue(suggestion.type().name().equals("TITLE") ? "SONG" : suggestion.type().name()))
                        .value(suggestion.value())
                        .matchedText(suggestion.matchedText())
                        .songId(suggestion.songId())
                        .arrangementId(suggestion.arrangementId()))
                .toList();
        return new CatalogAutocompleteResponse().suggestions(suggestions)
                .emptyState(emptyState(suggestions.isEmpty(), suggestions.isEmpty() ? "NO_SUGGESTIONS" : null));
    }

    private void validateSearch(CatalogSearchRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestRequired");
        }
        if (request.getSemanticMode() != null && request.getSemanticMode() != SearchSemanticMode.DISABLED
                && request.getSemanticMode() != SearchSemanticMode.ENABLED && request.getSemanticMode() != SearchSemanticMode.HYBRID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupportedSemanticMode");
        }
        if (Boolean.TRUE.equals(request.getIncludeDiagnostics())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "searchDiagnosticsRequiresSupportRole");
        }
        if (request.getFilters() != null && request.getFilters().getBpm() != null
                && request.getFilters().getBpm().getMin() != null && request.getFilters().getBpm().getMax() != null
                && request.getFilters().getBpm().getMin() > request.getFilters().getBpm().getMax()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalidBpmRange");
        }
    }

    private SearchQuery toQuery(CatalogSearchRequest request) {
        CatalogSearchFilters filters = request.getFilters();
        String scripture = first(filters == null ? null : filters.getScriptureReferences());
        String tag = first(filters == null ? null : filters.getTags());
        String contributor = first(filters == null ? null : filters.getContributors());
        String key = first(filters == null ? null : filters.getKeys());
        String arrangement = first(filters == null ? null : filters.getArrangements());
        Integer minBpm = filters == null || filters.getBpm() == null ? null : filters.getBpm().getMin();
        Integer maxBpm = filters == null || filters.getBpm() == null ? null : filters.getBpm().getMax();
        return new SearchQuery(INSTANCE_ID, request.getQuery(), null, scripture, tag, contributor, key, minBpm, maxBpm, arrangement);
    }

    private CatalogSearchResultSummary toSummary(SearchResult result, boolean includeExplanations) {
        CatalogSearchResultSummary summary = new CatalogSearchResultSummary(result.arrangementId(), SearchResultType.ARRANGEMENT,
                result.title(), result.score())
                .songId(result.songId())
                .arrangementId(result.arrangementId())
                .subtitle(result.arrangementLabel())
                .hydration(new SearchResultHydration().available(true).href("/v1/catalog/arrangements/" + result.arrangementId()))
                .matchedFields(List.of(new SearchMatchedField().field(FieldEnum.TITLE).value(result.title())));
        if (includeExplanations) {
            summary.rankingFactors(result.rankingFactors().stream()
                    .map(factor -> new SearchRankingFactor().code(factor.code()).contribution(factor.contribution()).visibility(factor.visibility()))
                    .toList());
        }
        return summary;
    }

    private List<SearchFacetGroup> facets(SearchActor actor, SearchQuery query) {
        List<SearchFacetBucket> buckets = lexicalSearchService.facets(actor, query).entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().label()))
                .map(entry -> new SearchFacetBucket().value(entry.getKey().code()).label(entry.getKey().label()).count(entry.getValue()))
                .toList();
        return List.of(new SearchFacetGroup().name("tags").buckets(buckets));
    }

    private int pageSize(CatalogSearchRequest request) {
        return request.getPagination() == null || request.getPagination().getPageSize() == null ? 20 : request.getPagination().getPageSize();
    }

    private int cursorOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(cursor);
            return Math.max(0, offset);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalidCursor");
        }
    }

    private SearchEmptyState emptyState(boolean empty, String reason) {
        return new SearchEmptyState().empty(empty).reason(reason).message(empty ? "No approved catalog discovery candidates matched." : null);
    }

    private SearchActor actor() {
        return new SearchActor("api-catalog-search", INSTANCE_ID, java.util.Set.of("role.admin", "role.worship_leader"), java.util.Set.of(), true);
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static List<ApprovedSearchDocument> seedDocuments() {
        UUID songOne = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID arrangementOne = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID songTwo = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID arrangementTwo = UUID.fromString("44444444-4444-4444-4444-444444444444");
        List<ApprovedSearchDocument> documents = new ArrayList<>();
        documents.add(new ApprovedSearchDocument(songOne, arrangementOne, INSTANCE_ID, "Amazing Grace", List.of("My Chains Are Gone"),
                List.of(new NormalizedScriptureReference("Ephesians", 2, 8, 8)), List.of(new TagFacet("grace", "Grace")),
                List.of("John Newton", "Chris Tomlin"), "G", 76, "Acoustic arrangement", List.of("acoustic", "capo"), List.of(),
                true, true, true, true));
        documents.add(new ApprovedSearchDocument(songTwo, arrangementTwo, INSTANCE_ID, "King of Kings", List.of(),
                List.of(new NormalizedScriptureReference("Psalm", 24, null, null)), List.of(new TagFacet("praise", "Praise")),
                List.of("Hillsong Worship"), "D", 68, "Full band arrangement", List.of("full band"), List.of(),
                true, true, true, true));
        return documents;
    }
}
