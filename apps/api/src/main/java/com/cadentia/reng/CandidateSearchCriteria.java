package com.cadentia.reng;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.TagType;
import java.util.List;

public record CandidateSearchCriteria(
        String language,
        List<String> allowedKeys,
        Integer minBpm,
        Integer maxBpm,
        Integer minEnergy,
        Integer maxEnergy,
        List<TagFilter> includeAnyTags,
        List<TagFilter> includeAllTags,
        ApprovalStatus requiredApprovalStatus,
        List<String> scriptureReferences) {

    public CandidateSearchCriteria {
        allowedKeys = allowedKeys == null ? List.of() : List.copyOf(allowedKeys);
        includeAnyTags = includeAnyTags == null ? List.of() : List.copyOf(includeAnyTags);
        includeAllTags = includeAllTags == null ? List.of() : List.copyOf(includeAllTags);
        scriptureReferences = scriptureReferences == null ? List.of() : List.copyOf(scriptureReferences);
    }

    public CandidateSearchCriteria(
            String language,
            List<String> allowedKeys,
            Integer minBpm,
            Integer maxBpm,
            List<TagFilter> includeAnyTags,
            List<TagFilter> includeAllTags) {
        this(
                language,
                allowedKeys,
                minBpm,
                maxBpm,
                null,
                null,
                includeAnyTags,
                includeAllTags,
                ApprovalStatus.APPROVED,
                List.of());
    }

    public CandidateSearchCriteria(
            String language,
            List<String> allowedKeys,
            Integer minBpm,
            Integer maxBpm,
            Integer minEnergy,
            Integer maxEnergy,
            List<TagFilter> includeAnyTags,
            List<TagFilter> includeAllTags,
            ApprovalStatus requiredApprovalStatus) {
        this(
                language,
                allowedKeys,
                minBpm,
                maxBpm,
                minEnergy,
                maxEnergy,
                includeAnyTags,
                includeAllTags,
                requiredApprovalStatus,
                List.of());
    }

    @Deprecated(forRemoval = false)
    public CandidateSearchCriteria(
            String language,
            List<String> allowedKeys,
            Integer minBpm,
            Integer maxBpm,
            List<String> requiredTagSlugs) {
        this(
                language,
                allowedKeys,
                minBpm,
                maxBpm,
                null,
                null,
                List.of(),
                themeSlugFilters(requiredTagSlugs),
                ApprovalStatus.APPROVED);
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
