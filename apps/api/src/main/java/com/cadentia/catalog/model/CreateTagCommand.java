package com.cadentia.catalog.model;

public record CreateTagCommand(
        TagType tagType,
        String name,
        String slug,
        String description,
        boolean active) {

    public CreateTagCommand {
        tagType = CatalogValidation.requireEnum(tagType, "tagType");
        name = CatalogValidation.requireText(name, "name");
        slug = CatalogValidation.requireText(slug, "slug");
    }
}
