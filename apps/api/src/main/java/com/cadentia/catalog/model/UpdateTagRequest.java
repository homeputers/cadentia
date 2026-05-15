package com.cadentia.catalog.model;

public record UpdateTagRequest(
        String name,
        String slug,
        String description,
        boolean active) {

    public UpdateTagRequest {
        name = CatalogValidation.requireText(name, "name");
        slug = CatalogValidation.requireText(slug, "slug");
    }
}
