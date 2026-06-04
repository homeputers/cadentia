package com.cadentia.scraperadmin;

import java.util.List;
import java.util.Map;

public record StarterCatalogSeedSong(
        String externalSongId,
        String title,
        String artistName,
        Map<String, Object> artistMetadata,
        String ccliNumber,
        String lyricsHash,
        String sourceReference,
        String licenseType,
        String licenseEvidence,
        List<String> tagSlugs,
        List<StarterCatalogSeedArrangement> arrangements,
        Map<String, Object> sourceMetadata) {

    public StarterCatalogSeedSong {
        artistMetadata = Map.copyOf(artistMetadata == null ? Map.of() : artistMetadata);
        tagSlugs = List.copyOf(tagSlugs == null ? List.of() : tagSlugs);
        arrangements = List.copyOf(arrangements == null ? List.of() : arrangements);
        sourceMetadata = Map.copyOf(sourceMetadata == null ? Map.of() : sourceMetadata);
    }
}
