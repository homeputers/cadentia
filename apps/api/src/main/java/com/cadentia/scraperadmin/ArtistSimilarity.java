package com.cadentia.scraperadmin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Deterministic artist-name similarity with explicit thresholds. */
public final class ArtistSimilarity {

    public static final BigDecimal EXACT_THRESHOLD = new BigDecimal("1.0000");
    public static final BigDecimal STRONG_THRESHOLD = new BigDecimal("0.8000");
    public static final BigDecimal POSSIBLE_THRESHOLD = new BigDecimal("0.6000");

    private static final Pattern DIACRITIC_MARK = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> IGNORED_TOKENS = Set.of("and", "band", "feat", "featuring", "the");

    public ArtistMatch compare(String candidateArtistName, String catalogArtistName) {
        if (isBlank(candidateArtistName) || isBlank(catalogArtistName)) {
            return new ArtistMatch(BigDecimal.ZERO.setScale(4), ArtistSimilarityTier.MISSING);
        }

        Set<String> candidateTokens = tokenize(candidateArtistName);
        Set<String> catalogTokens = tokenize(catalogArtistName);
        if (candidateTokens.isEmpty() || catalogTokens.isEmpty()) {
            return new ArtistMatch(BigDecimal.ZERO.setScale(4), ArtistSimilarityTier.MISSING);
        }
        if (candidateTokens.equals(catalogTokens)) {
            return new ArtistMatch(EXACT_THRESHOLD, ArtistSimilarityTier.EXACT);
        }

        Set<String> intersection = new TreeSet<>(candidateTokens);
        intersection.retainAll(catalogTokens);
        Set<String> union = new TreeSet<>(candidateTokens);
        union.addAll(catalogTokens);
        BigDecimal score = BigDecimal.valueOf(intersection.size())
                .divide(BigDecimal.valueOf(union.size()), 4, RoundingMode.HALF_UP);
        return new ArtistMatch(score, tierFor(score));
    }

    private ArtistSimilarityTier tierFor(BigDecimal score) {
        if (score.compareTo(STRONG_THRESHOLD) >= 0) {
            return ArtistSimilarityTier.STRONG;
        }
        if (score.compareTo(POSSIBLE_THRESHOLD) >= 0) {
            return ArtistSimilarityTier.POSSIBLE;
        }
        return ArtistSimilarityTier.WEAK;
    }

    private Set<String> tokenize(String value) {
        String withoutDiacritics = DIACRITIC_MARK.matcher(
                        Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("");
        String normalized = NON_ALPHANUMERIC.matcher(withoutDiacritics.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
        if (normalized.isEmpty()) {
            return Set.of();
        }
        Set<String> tokens = new TreeSet<>();
        Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !IGNORED_TOKENS.contains(token))
                .forEach(tokens::add);
        return tokens;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
