package com.cadentia.scraperadmin;

import java.time.Instant;
import java.util.List;

public record StarterCatalogPackageImportCommand(
        StarterCatalogPackageScope scope,
        String packageName,
        String packageVersion,
        String packageSourceUri,
        String denomination,
        String initiatedBy,
        Instant collectedAt,
        List<StarterCatalogSeedSong> songs) {

    public StarterCatalogPackageImportCommand {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        packageName = requireText(packageName, "packageName");
        packageVersion = requireText(packageVersion, "packageVersion");
        packageSourceUri = requireText(packageSourceUri, "packageSourceUri");
        denomination = denomination == null || denomination.isBlank() ? null : denomination.trim();
        initiatedBy = requireText(initiatedBy, "initiatedBy");
        collectedAt = collectedAt == null ? Instant.now() : collectedAt;
        songs = List.copyOf(songs == null ? List.of() : songs);
        if (songs.isEmpty()) {
            throw new IllegalArgumentException("songs are required");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
