package com.cadentia.scraperadmin;

import java.math.BigDecimal;

public record ArtistMatch(BigDecimal score, ArtistSimilarityTier tier) {
}
