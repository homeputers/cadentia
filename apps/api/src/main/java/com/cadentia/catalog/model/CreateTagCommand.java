package com.cadentia.catalog.model;

public record CreateTagCommand(
        TagType tagType,
        String name,
        String slug,
        String description,
        int sortOrder,
        boolean active) {

    public CreateTagCommand(
            TagType tagType,
            String name,
            String slug,
            String description,
            boolean active) {
        this(tagType, name, slug, description, 0, active);
    }

    public CreateTagCommand {
        tagType = CatalogValidation.requireEnum(tagType, "tagType");
        name = CatalogValidation.requireText(name, "name");
        slug = CatalogValidation.requireText(slug, "slug");
        CatalogValidation.requireAtLeast(sortOrder, 0, "sortOrder");
    }
}
