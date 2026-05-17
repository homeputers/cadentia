package com.cadentia.reng;

import com.cadentia.catalog.model.TagType;
import java.util.List;

public record CandidateSearchCriteria(
        String language,
        List<String> allowedKeys,
        Integer minBpm,
        Integer maxBpm,
        List<TagFilter> includeAnyTags,
        List<TagFilter> includeAllTags) {

    public CandidateSearchCriteria {
        allowedKeys = allowedKeys == null ? List.of() : List.copyOf(allowedKeys);
        includeAnyTags = includeAnyTags == null ? List.of() : List.copyOf(includeAnyTags);
        includeAllTags = includeAllTags == null ? List.of() : List.copyOf(includeAllTags);
    }

    @Deprecated(forRemoval = false)
    public CandidateSearchCriteria(
            String language,
            List<String> allowedKeys,
            Integer minBpm,
            Integer maxBpm,
            List<String> requiredTagSlugs) {
        this(language, allowedKeys, minBpm, maxBpm, List.of(), themeSlugFilters(requiredTagSlugs));
    }

    private static List<TagFilter> themeSlugFilters(List<String> requiredTagSlugs) {
        if (requiredTagSlugs == null || requiredTagSlugs.isEmpty()) {
            return List.of();
        }
        return requiredTagSlugs.stream()
                .map(slug -> TagFilter.bySlug(TagType.THEME, slug))
                .toList();
    }
}
