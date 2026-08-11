package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.generated.model.BpmRangeFilter;
import com.cadentia.generated.model.CatalogAutocompleteRequest;
import com.cadentia.generated.model.CatalogSearchFilters;
import com.cadentia.generated.model.CatalogSearchRequest;
import com.cadentia.generated.model.SearchPaginationRequest;
import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.CatalogSearchApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CatalogSearchControllerTest {

    private final CatalogSearchController controller = new CatalogSearchController(new CatalogSearchApplicationService());

    @Test
    void returnsPaginatedSearchResultsWithFacetsAndExplanations() {
        CatalogSearchRequest request = new CatalogSearchRequest()
                .query("Amazing")
                .includeFacets(true)
                .includeExplanations(true)
                .pagination(new SearchPaginationRequest().pageSize(1));

        var response = controller.searchCatalog(request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getResultType().getValue()).isEqualTo("ARRANGEMENT");
        assertThat(response.getResults().get(0).getRankingFactors()).isNotEmpty();
        assertThat(response.getFacets()).hasSize(1);
        assertThat(response.getPagination().getPageSize()).isEqualTo(1);
        assertThat(response.getEmptyState().getEmpty()).isFalse();
    }

    @Test
    void supportsAutocompleteSuggestions() {
        CatalogAutocompleteRequest request = new CatalogAutocompleteRequest("Ama").limit(5);

        var response = controller.autocompleteCatalog(request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSuggestions()).extracting("value").contains("Amazing Grace");
        assertThat(response.getEmptyState().getEmpty()).isFalse();
    }

    @Test
    void searchesCurrentProviderDocumentsInsteadOfOnlyStaticSeeds() {
        UUID songId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        UUID arrangementId = UUID.fromString("77777777-7777-4777-8777-777777777777");
        CatalogSearchController dynamicController = new CatalogSearchController(new CatalogSearchApplicationService(() -> List.of(
                new ApprovedSearchDocument(
                        songId,
                        arrangementId,
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "Porque mayor",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("Imported Artist"),
                        "G",
                        96,
                        "Imported arrangement",
                        List.of("Imported arrangement"),
                        List.of(),
                        true,
                        true,
                        true,
                        true))));

        var response = dynamicController.searchCatalog(new CatalogSearchRequest().query("mayor")).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getResults()).extracting("songId").contains(songId);
        assertThat(response.getResults()).extracting("title").contains("Porque mayor");
    }

    @Test
    void appliesStructuredFiltersAndSafeEmptyResponses() {
        CatalogSearchRequest request = new CatalogSearchRequest()
                .filters(new CatalogSearchFilters().tags(java.util.List.of("resurrection")));

        var response = controller.searchCatalog(request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getEmptyState().getEmpty()).isTrue();
        assertThat(response.getEmptyState().getReason()).isEqualTo("NO_MATCHES");
    }

    @Test
    void rejectsInvalidBpmRanges() {
        CatalogSearchRequest request = new CatalogSearchRequest()
                .filters(new CatalogSearchFilters().bpm(new BpmRangeFilter().min(140).max(70)));

        assertThatThrownBy(() -> controller.searchCatalog(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnauthorizedDiagnostics() {
        CatalogSearchRequest request = new CatalogSearchRequest().query("Amazing").includeDiagnostics(true);

        assertThatThrownBy(() -> controller.searchCatalog(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
