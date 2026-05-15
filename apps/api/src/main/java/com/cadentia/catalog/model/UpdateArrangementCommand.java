package com.cadentia.catalog.model;

public record UpdateArrangementCommand(
        String name,
        String normalizedName,
        ArrangementSourceType sourceType,
        String language,
        String musicalKey,
        KeyMode keyMode,
        Integer tempoBpm,
        String timeSignature,
        Integer durationSeconds,
        Integer energyLevel,
        Integer difficultyLevel,
        boolean defaultForSong,
        boolean active) {

    public UpdateArrangementCommand {
        name = CatalogValidation.requireText(name, "name");
        normalizedName = CatalogValidation.requireText(normalizedName, "normalizedName");
        sourceType = CatalogValidation.requireEnum(sourceType, "sourceType");
        language = CatalogValidation.requireText(language, "language");
        musicalKey = CatalogValidation.requireOptionalTextIfPresent(musicalKey, "musicalKey");
        tempoBpm = CatalogValidation.requirePositiveIfPresent(tempoBpm, "tempoBpm");
        durationSeconds = CatalogValidation.requirePositiveIfPresent(durationSeconds, "durationSeconds");
        energyLevel = CatalogValidation.requireRangeIfPresent(energyLevel, 1, 5, "energyLevel");
        difficultyLevel = CatalogValidation.requireRangeIfPresent(difficultyLevel, 1, 5, "difficultyLevel");
    }
}
