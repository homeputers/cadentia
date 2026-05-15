package com.cadentia.catalog.entity;

import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.KeyMode;
import java.time.Instant;
import java.util.UUID;

public record Arrangement(
        UUID id,
        UUID songId,
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
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
