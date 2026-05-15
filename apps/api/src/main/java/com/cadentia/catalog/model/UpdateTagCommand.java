package com.cadentia.catalog.model;

public record UpdateTagCommand(
        String name,
        String slug,
        String description,
        boolean active) {

    public UpdateTagCommand {
        name = CatalogValidation.requireText(name, "name");
        slug = CatalogValidation.requireText(slug, "slug");
    }
}
