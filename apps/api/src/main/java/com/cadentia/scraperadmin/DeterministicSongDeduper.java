package com.cadentia.scraperadmin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Transparent ADR-003 duplicate suggestion rules.
 *
 * <p>Weights are additive and capped at 1.0: exact CCLI number = 0.55, exact lyrics hash = 0.35,
 * exact normalized title = 0.25, near title token overlap at or above 0.85 = 0.18, exact artist =
 * 0.15, strong artist = 0.10, and possible artist = 0.05. Suggestions are returned when the
 * final score is at least 0.40 or when a strong unique identifier signal, CCLI or lyrics hash,
 * matches exactly. Heuristic matches never allow automatic merges; reviewer confirmation is always
 * required.
 */
public final class DeterministicSongDeduper implements SongDeduper {

    public static final String RULESET_NAME = "deterministic-deduper-v1";
    public static final BigDecimal SUGGESTION_THRESHOLD = new BigDecimal("0.4000");

    private static final BigDecimal CCLI_EXACT_WEIGHT = new BigDecimal("0.5500");
    private static final BigDecimal LYRICS_HASH_EXACT_WEIGHT = new BigDecimal("0.3500");
    private static final BigDecimal TITLE_EXACT_WEIGHT = new BigDecimal("0.2500");
    private static final BigDecimal TITLE_NEAR_WEIGHT = new BigDecimal("0.1800");
    private static final BigDecimal ARTIST_EXACT_WEIGHT = new BigDecimal("0.1500");
    private static final BigDecimal ARTIST_STRONG_WEIGHT = new BigDecimal("0.1000");
    private static final BigDecimal ARTIST_POSSIBLE_WEIGHT = new BigDecimal("0.0500");
    private static final BigDecimal TITLE_NEAR_THRESHOLD = new BigDecimal("0.8500");
    private static final BigDecimal ONE = new BigDecimal("1.0000");

    private final TitleNormalizer titleNormalizer;
    private final ArtistSimilarity artistSimilarity;

    public DeterministicSongDeduper() {
        this(new TitleNormalizer(), new ArtistSimilarity());
    }

    DeterministicSongDeduper(TitleNormalizer titleNormalizer, ArtistSimilarity artistSimilarity) {
        this.titleNormalizer = titleNormalizer;
        this.artistSimilarity = artistSimilarity;
    }

    @Override
    public List<DuplicateSuggestion> suggestDuplicates(
            ImportSongCandidate candidate,
            Collection<CatalogSongCandidate> catalogSongs) {
        if (candidate == null) {
            throw new NullPointerException("candidate is required");
        }
        if (catalogSongs == null) {
            throw new NullPointerException("catalogSongs is required");
        }
        String candidateTitle = candidate.optionalNormalizedTitle()
                .orElseGet(() -> titleNormalizer.normalize(candidate.rawTitle()));
        return catalogSongs.stream()
                .map(catalogSong -> score(candidate, candidateTitle, catalogSong))
                .filter(this::isSuggestion)
                .sorted(Comparator.comparing(DuplicateSuggestion::score).reversed()
                        .thenComparing(suggestion -> suggestion.songId().toString()))
                .toList();
    }

    private DuplicateSuggestion score(
            ImportSongCandidate candidate,
            String candidateTitle,
            CatalogSongCandidate catalogSong) {
        List<MatchSignal> signals = new ArrayList<>();
        BigDecimal score = BigDecimal.ZERO.setScale(4);

        boolean ccliMatches = candidate.optionalCcliNumber().isPresent()
                && catalogSong.optionalCcliNumber().isPresent()
                && candidate.ccliNumber().equals(catalogSong.ccliNumber());
        signals.add(new MatchSignal(
                "ccliNumber",
                ccliMatches,
                ccliMatches ? CCLI_EXACT_WEIGHT : BigDecimal.ZERO.setScale(4),
                candidate.ccliNumber(),
                catalogSong.ccliNumber(),
                ccliMatches ? "Exact CCLI song number match." : "No exact CCLI song number match."));
        if (ccliMatches) {
            score = score.add(CCLI_EXACT_WEIGHT);
        }

        boolean lyricsHashMatches = candidate.optionalLyricsHash().isPresent()
                && catalogSong.optionalLyricsHash().isPresent()
                && candidate.lyricsHash().equals(catalogSong.lyricsHash());
        signals.add(new MatchSignal(
                "lyricsHash",
                lyricsHashMatches,
                lyricsHashMatches ? LYRICS_HASH_EXACT_WEIGHT : BigDecimal.ZERO.setScale(4),
                candidate.lyricsHash(),
                catalogSong.lyricsHash(),
                lyricsHashMatches ? "Exact allowed-source lyrics hash match." : "No lyrics hash match."));
        if (lyricsHashMatches) {
            score = score.add(LYRICS_HASH_EXACT_WEIGHT);
        }

        BigDecimal titleSimilarity = titleSimilarity(candidateTitle, catalogSong.normalizedTitle());
        boolean titleExact = titleSimilarity.compareTo(ONE) == 0;
        boolean titleNear = !titleExact && titleSimilarity.compareTo(TITLE_NEAR_THRESHOLD) >= 0;
        BigDecimal titleWeight = BigDecimal.ZERO.setScale(4);
        if (titleExact) {
            titleWeight = TITLE_EXACT_WEIGHT;
        } else if (titleNear) {
            titleWeight = TITLE_NEAR_WEIGHT;
        }
        signals.add(new MatchSignal(
                "normalizedTitle",
                titleExact || titleNear,
                titleWeight,
                candidateTitle,
                catalogSong.normalizedTitle(),
                titleExplanation(titleExact, titleNear, titleSimilarity)));
        score = score.add(titleWeight);

        ArtistMatch artistMatch = artistSimilarity.compare(
                candidate.sourceArtistName(),
                catalogSong.originalArtistDisplay());
        BigDecimal artistWeight = artistWeight(artistMatch.tier());
        signals.add(new MatchSignal(
                "artistSimilarity",
                artistWeight.compareTo(BigDecimal.ZERO) > 0,
                artistWeight,
                candidate.sourceArtistName(),
                catalogSong.originalArtistDisplay(),
                "Artist similarity tier %s with token score %s."
                        .formatted(artistMatch.tier(), artistMatch.score())));
        score = score.add(artistWeight);

        return new DuplicateSuggestion(catalogSong.songId(), score.min(ONE), signals, false);
    }

    private boolean isSuggestion(DuplicateSuggestion suggestion) {
        boolean hasIdentifierMatch = suggestion.signals().stream()
                .anyMatch(signal -> signal.matched()
                        && ("ccliNumber".equals(signal.name()) || "lyricsHash".equals(signal.name())));
        return hasIdentifierMatch || suggestion.score().compareTo(SUGGESTION_THRESHOLD) >= 0;
    }

    private BigDecimal artistWeight(ArtistSimilarityTier tier) {
        return switch (tier) {
            case EXACT -> ARTIST_EXACT_WEIGHT;
            case STRONG -> ARTIST_STRONG_WEIGHT;
            case POSSIBLE -> ARTIST_POSSIBLE_WEIGHT;
            case WEAK, MISSING -> BigDecimal.ZERO.setScale(4);
        };
    }

    private BigDecimal titleSimilarity(String candidateTitle, String catalogTitle) {
        List<String> candidateTokens = List.of(candidateTitle.split("[-\\s]+"));
        List<String> catalogTokens = List.of(catalogTitle.split("[-\\s]+"));
        long intersection = candidateTokens.stream().filter(catalogTokens::contains).distinct().count();
        long union = candidateTokens.stream().distinct().count()
                + catalogTokens.stream().filter(token -> !candidateTokens.contains(token)).distinct().count();
        return BigDecimal.valueOf(intersection).divide(BigDecimal.valueOf(union), 4, RoundingMode.HALF_UP);
    }

    private String titleExplanation(boolean titleExact, boolean titleNear, BigDecimal titleSimilarity) {
        if (titleExact) {
            return "Exact normalized title match.";
        }
        if (titleNear) {
            return "Near normalized title token match with similarity %s.".formatted(titleSimilarity);
        }
        return "Normalized title token similarity %s is below the near-match threshold %s."
                .formatted(titleSimilarity, TITLE_NEAR_THRESHOLD);
    }
}
