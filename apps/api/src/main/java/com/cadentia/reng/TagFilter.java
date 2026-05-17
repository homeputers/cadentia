package com.cadentia.reng;

import com.cadentia.catalog.model.TagType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TagFilter(TagType tagType, UUID tagId, String slug) {

    public TagFilter {
        Objects.requireNonNull(tagType, "tagType must not be null");
        boolean hasTagId = tagId != null;
        boolean hasSlug = slug != null && !slug.isBlank();
        if (hasTagId == hasSlug) {
            throw new IllegalArgumentException("Exactly one of tagId or slug must be provided");
        }
        if (slug != null) {
            slug = slug.trim();
        }
    }

    public static TagFilter byId(TagType tagType, UUID tagId) {
        return new TagFilter(tagType, tagId, null);
    }

    public static TagFilter bySlug(TagType tagType, String slug) {
        return new TagFilter(tagType, null, slug);
    }

    boolean matches(RecommendationTag tag) {
        return tagType == tag.tagType()
                && Optional.ofNullable(tagId).map(tag.id()::equals).orElseGet(() -> slug.equals(tag.slug()));
    }
}
