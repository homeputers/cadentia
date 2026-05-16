package com.cadentia.catalog.model;

public record UpdateTagCommand(
        String name,
        String slug,
        String description,
        int sortOrder,
        boolean active) {

    public UpdateTagCommand(
            String name,
            String slug,
            String description,
            boolean active) {
        this(name, slug, description, 0, active);
    }

    public UpdateTagCommand {
        name = CatalogValidation.requireText(name, "name");
        slug = CatalogValidation.requireText(slug, "slug");
        CatalogValidation.requireAtLeast(sortOrder, 0, "sortOrder");
    }
}
