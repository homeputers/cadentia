package com.cadentia.scraperadmin;

import java.util.List;

public record StarterCatalogSeedArrangement(
        String externalArrangementId,
        String name,
        String language,
        String musicalKey,
        String keyMode,
        Integer tempoBpm,
        String timeSignature,
        Integer durationSeconds,
        Integer energyLevel,
        Integer difficultyLevel,
        List<String> tagSlugs,
        String sourceReference) {

    public StarterCatalogSeedArrangement {
        tagSlugs = List.copyOf(tagSlugs == null ? List.of() : tagSlugs);
    }
}
