package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.generated.model.BpmRangeFilter;
import com.cadentia.generated.model.CatalogAutocompleteRequest;
import com.cadentia.generated.model.CatalogSearchFilters;
import com.cadentia.generated.model.CatalogSearchRequest;
import com.cadentia.generated.model.SearchPaginationRequest;
import com.cadentia.search.CatalogSearchApplicationService;
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
