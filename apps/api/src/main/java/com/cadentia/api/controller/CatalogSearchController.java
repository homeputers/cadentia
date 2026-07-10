package com.cadentia.api.controller;

import com.cadentia.generated.api.CatalogSearchApi;
import com.cadentia.generated.model.CatalogAutocompleteRequest;
import com.cadentia.generated.model.CatalogAutocompleteResponse;
import com.cadentia.generated.model.CatalogSearchRequest;
import com.cadentia.generated.model.CatalogSearchResponse;
import com.cadentia.search.CatalogSearchApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogSearchController implements CatalogSearchApi {

    private final CatalogSearchApplicationService searchService;

    public CatalogSearchController(CatalogSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    @Override
    public ResponseEntity<CatalogSearchResponse> searchCatalog(CatalogSearchRequest catalogSearchRequest) {
        return ResponseEntity.ok(searchService.search(catalogSearchRequest));
    }

    @Override
    public ResponseEntity<CatalogAutocompleteResponse> autocompleteCatalog(CatalogAutocompleteRequest catalogAutocompleteRequest) {
        return ResponseEntity.ok(searchService.autocomplete(catalogAutocompleteRequest));
    }
}
