package com.cadentia.catalog.model;

public record CreateTagRequest(
        TagType tagType,
        String name,
        String slug,
        String description,
        boolean active) {

    public CreateTagRequest {
        tagType = CatalogValidation.requireEnum(tagType, "tagType");
        name = CatalogValidation.requireText(name, "name");
        slug = CatalogValidation.requireText(slug, "slug");
    }
}
