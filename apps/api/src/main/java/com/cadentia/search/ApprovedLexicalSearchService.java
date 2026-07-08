package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.AutocompleteSuggestion;
import com.cadentia.search.ApprovedSearchModels.SearchActor;
import com.cadentia.search.ApprovedSearchModels.RankingFactor;
import com.cadentia.search.ApprovedSearchModels.SearchDiagnostics;
import com.cadentia.search.ApprovedSearchModels.SearchExplanation;
import com.cadentia.search.ApprovedSearchModels.SearchRankingProfile;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SearchResult;
import com.cadentia.search.ApprovedSearchModels.SuggestionType;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApprovedLexicalSearchService {

    private static final int MAX_DISTANCE = 2;
    public static final SearchRankingProfile DEFAULT_RANKING_PROFILE = new SearchRankingProfile(
            "search-ranking-v1", false, false, false, Map.ofEntries(
            Map.entry("exactTitleMatch", 30.0d),
            Map.entry("approvedAlternateTitleMatch", 27.0d),
            Map.entry("prefixTitleMatch", 20.0d),
            Map.entry("fuzzyTitleMatch", 10.0d),
            Map.entry("scriptureProximity", 24.0d),
            Map.entry("curatedTagMatch", 22.0d),
            Map.entry("contributorMatch", 9.0d),
            Map.entry("musicalFeatureMatch", 8.0d),
            Map.entry("arrangementMatch", 8.0d),
            Map.entry("semanticSimilarity", 7.0d),
            Map.entry("familiaritySignal", 5.0d),
            Map.entry("recencyPreference", 1.5d),
            Map.entry("packagePreference", 2.0d)));
    private final List<ApprovedSearchDocument> documents;
    private final SearchEligibilityPolicy eligibilityPolicy;
    private final SearchRankingProfile rankingProfile;

    public ApprovedLexicalSearchService(List<ApprovedSearchDocument> documents) {
        this(documents, new SearchEligibilityPolicy());
    }

    public ApprovedLexicalSearchService(List<ApprovedSearchDocument> documents, SearchEligibilityPolicy eligibilityPolicy) {
        this(documents, eligibilityPolicy, DEFAULT_RANKING_PROFILE);
    }

    public ApprovedLexicalSearchService(
            List<ApprovedSearchDocument> documents, SearchEligibilityPolicy eligibilityPolicy, SearchRankingProfile rankingProfile) {
        this.documents = documents == null ? List.of() : List.copyOf(documents);
        this.eligibilityPolicy = eligibilityPolicy == null ? new SearchEligibilityPolicy() : eligibilityPolicy;
        this.rankingProfile = rankingProfile == null ? DEFAULT_RANKING_PROFILE : rankingProfile;
    }

    public List<SearchResult> search(SearchQuery query) {
        return search(defaultActor(query.instanceId()), query);
    }

    public List<SearchResult> search(SearchActor actor, SearchQuery query) {
        return eligible(actor)
                .map(document -> scored(document, query))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed()
                        .thenComparing(SearchResult::title)
                        .thenComparing(result -> result.songId().toString())
                        .thenComparing(result -> result.arrangementId().toString()))
                .toList();
    }

    public List<AutocompleteSuggestion> autocomplete(UUID instanceId, String prefix, int limit) {
        return autocomplete(defaultActor(instanceId), prefix, limit);
    }

    public List<AutocompleteSuggestion> autocomplete(SearchActor actor, String prefix, int limit) {
        String normalizedPrefix = SearchNormalizer.normalizeText(prefix);
        if (normalizedPrefix.length() < 2 || limit <= 0) {
            return List.of();
        }
        return eligible(actor)
                .flatMap(document -> suggestions(document, normalizedPrefix))
                .distinct()
                .sorted(Comparator.comparing(AutocompleteSuggestion::type).thenComparing(AutocompleteSuggestion::value))
                .limit(limit)
                .toList();
    }

    private Stream<AutocompleteSuggestion> suggestions(ApprovedSearchDocument document, String prefix) {
        Stream<AutocompleteSuggestion> titles = Stream.concat(Stream.of(document.title()), document.alternateTitles().stream())
                .map(value -> suggestion(SuggestionType.TITLE, value, prefix, document));
        Stream<AutocompleteSuggestion> tags = document.tags().stream()
                .flatMap(tag -> Stream.of(tag.code(), tag.label()))
                .map(value -> suggestion(SuggestionType.TAG, value, prefix, document));
        Stream<AutocompleteSuggestion> scripture = document.scriptureReferences().stream()
                .map(NormalizedScriptureReference::display)
                .map(value -> suggestion(SuggestionType.SCRIPTURE, value, prefix, document));
        Stream<AutocompleteSuggestion> contributors = document.contributors().stream()
                .map(value -> suggestion(SuggestionType.CONTRIBUTOR, value, prefix, document));
        Stream<AutocompleteSuggestion> arrangements = Stream.concat(
                        Stream.of(document.arrangementLabel()), document.arrangementMetadata().stream())
                .map(value -> suggestion(SuggestionType.ARRANGEMENT, value, prefix, document));
        return Stream.of(titles, tags, scripture, contributors, arrangements)
                .flatMap(stream -> stream)
                .filter(suggestion -> suggestion != null);
    }

    private AutocompleteSuggestion suggestion(
            SuggestionType type, String value, String prefix, ApprovedSearchDocument document) {
        if (value == null || SearchNormalizer.normalizeText(value).isBlank()) {
            return null;
        }
        String normalized = SearchNormalizer.normalizeText(value);
        boolean matches = normalized.startsWith(prefix) || SearchNormalizer.tokens(value).stream().anyMatch(token -> token.startsWith(prefix));
        return matches ? new AutocompleteSuggestion(type, value, prefix, document.songId(), document.arrangementId()) : null;
    }

    private SearchResult scored(ApprovedSearchDocument document, SearchQuery query) {
        List<RankingFactor> factors = Stream.of(
                        titleFactor("exactTitleMatch", document.title(), query.text(), query.title(), score -> score == 10),
                        bestAlternateTitleFactor(document, query),
                        titleFactor("prefixTitleMatch", document.title(), query.text(), query.title(), score -> score >= 5 && score < 10),
                        titleFactor("fuzzyTitleMatch", document.title(), query.text(), query.title(), score -> score > 0 && score < 5),
                        scriptureFactor(document, query.scripture()),
                        tagFactor(document, query.tag()),
                        contributorFactor(document, query.contributor()),
                        musicalFeatureFactor(document, query),
                        arrangementFactor(document, query.arrangement()),
                        semanticFactor(document),
                        familiarityFactor(document),
                        recencyFactor(document),
                        packagePreferenceFactor(document))
                .filter(factor -> factor != null && factor.contribution() > 0)
                .toList();
        double score = factors.stream().mapToDouble(RankingFactor::contribution).sum();
        return new SearchResult(
                document.songId(), document.arrangementId(), document.title(), document.arrangementLabel(), score, factors, rankingProfile.version());
    }

    private RankingFactor titleFactor(
            String code, String value, String text, String title, Predicate<Double> matcher) {
        double rawScore = Math.max(matchScore(SearchNormalizer.normalizeText(text), value), matchScore(SearchNormalizer.normalizeText(title), value));
        return matcher.test(rawScore) ? factor(code, rawScore / 10.0d) : null;
    }

    private RankingFactor bestAlternateTitleFactor(ApprovedSearchDocument document, SearchQuery query) {
        boolean match = Stream.of(query.text(), query.title())
                .map(SearchNormalizer::normalizeText)
                .filter(value -> !value.isBlank())
                .anyMatch(value -> document.alternateTitles().stream()
                        .map(SearchNormalizer::normalizeText)
                        .anyMatch(value::equals));
        return match ? factor("approvedAlternateTitleMatch", 1.0d) : null;
    }

    private RankingFactor scriptureFactor(ApprovedSearchDocument document, String scripture) {
        return scoreScripture(document, scripture) > 0 ? factor("scriptureProximity", 1.0d) : null;
    }

    private RankingFactor tagFactor(ApprovedSearchDocument document, String tag) {
        return scoreTag(document, tag) > 0 ? factor("curatedTagMatch", 1.0d) : null;
    }

    private RankingFactor contributorFactor(ApprovedSearchDocument document, String contributor) {
        return scoreContributor(document, contributor) > 0 ? factor("contributorMatch", 1.0d) : null;
    }

    private RankingFactor musicalFeatureFactor(ApprovedSearchDocument document, SearchQuery query) {
        boolean keyMatch = scoreKey(document, query.musicalKey()) > 0;
        boolean bpmMatch = scoreBpm(document, query.minBpm(), query.maxBpm()) > 0;
        return keyMatch || bpmMatch ? factor("musicalFeatureMatch", keyMatch && bpmMatch ? 1.0d : 0.5d) : null;
    }

    private RankingFactor arrangementFactor(ApprovedSearchDocument document, String arrangement) {
        return scoreArrangement(document, arrangement) > 0 ? factor("arrangementMatch", 1.0d) : null;
    }

    private RankingFactor semanticFactor(ApprovedSearchDocument document) {
        return document.semanticSimilarity() == null ? null : factor("semanticSimilarity", document.semanticSimilarity());
    }

    private RankingFactor familiarityFactor(ApprovedSearchDocument document) {
        if (!rankingProfile.familiarityEnabled()) {
            return null;
        }
        double aggregate = Math.max(document.familiaritySignal() == null ? 0 : document.familiaritySignal(),
                document.popularitySignal() == null ? 0 : document.popularitySignal());
        return aggregate > 0 ? factor("familiaritySignal", aggregate) : null;
    }

    private RankingFactor recencyFactor(ApprovedSearchDocument document) {
        return rankingProfile.recencyPreferenceEnabled() && document.catalogUpdatedAt() != null ? factor("recencyPreference", 1.0d) : null;
    }

    private RankingFactor packagePreferenceFactor(ApprovedSearchDocument document) {
        return rankingProfile.packagePreferenceEnabled() && document.starterPackagePreferred() ? factor("packagePreference", 1.0d) : null;
    }

    private RankingFactor factor(String code, double boundedValue) {
        return new RankingFactor(code, weight(code) * Math.max(0.0d, Math.min(1.0d, boundedValue)), "publicSafe");
    }

    private double weight(String code) {
        return rankingProfile.weights().getOrDefault(code, DEFAULT_RANKING_PROFILE.weights().getOrDefault(code, 0.0d));
    }

    private double matchScore(String normalizedNeedle, String value) {
        if (normalizedNeedle == null || normalizedNeedle.isBlank()) {
            return 0;
        }
        String normalizedValue = SearchNormalizer.normalizeText(value);
        if (normalizedValue.equals(normalizedNeedle)) {
            return 10;
        }
        if (normalizedValue.startsWith(normalizedNeedle)) {
            return 7;
        }
        if (normalizedValue.contains(normalizedNeedle)) {
            return 5;
        }
        int distance = levenshtein(normalizedNeedle, normalizedValue);
        int threshold = normalizedNeedle.length() < 5 ? 1 : MAX_DISTANCE;
        return distance <= threshold ? 3 - (distance * 0.5d) : 0;
    }

    private double scoreScripture(ApprovedSearchDocument document, String scripture) {
        return SearchNormalizer.parseScriptureReferences(scripture).stream()
                .anyMatch(queryRef -> document.scriptureReferences().stream().anyMatch(ref -> ref.matches(queryRef))) ? 8 : 0;
    }

    private double scoreTag(ApprovedSearchDocument document, String tag) {
        String normalized = SearchNormalizer.normalizeText(tag);
        if (normalized.isBlank()) {
            return 0;
        }
        return document.tags().stream().anyMatch(t -> SearchNormalizer.normalizeText(t.code()).equals(normalized)
                || SearchNormalizer.normalizeText(t.label()).contains(normalized)) ? 7 : 0;
    }

    private double scoreContributor(ApprovedSearchDocument document, String contributor) {
        String normalized = SearchNormalizer.normalizeText(contributor);
        return !normalized.isBlank() && document.contributors().stream()
                .anyMatch(value -> SearchNormalizer.normalizeText(value).contains(normalized)) ? 5 : 0;
    }

    private double scoreKey(ApprovedSearchDocument document, String musicalKey) {
        String normalized = SearchNormalizer.normalizeKey(musicalKey);
        return !normalized.isBlank() && normalized.equals(SearchNormalizer.normalizeKey(document.musicalKey())) ? 4 : 0;
    }

    private double scoreBpm(ApprovedSearchDocument document, Integer minBpm, Integer maxBpm) {
        if (document.bpm() == null || (minBpm == null && maxBpm == null)) {
            return 0;
        }
        return (minBpm == null || document.bpm() >= minBpm) && (maxBpm == null || document.bpm() <= maxBpm) ? 4 : 0;
    }

    private double scoreArrangement(ApprovedSearchDocument document, String arrangement) {
        String normalized = SearchNormalizer.normalizeText(arrangement);
        if (normalized.isBlank()) {
            return 0;
        }
        return Stream.concat(Stream.of(document.arrangementLabel()), document.arrangementMetadata().stream())
                .anyMatch(value -> SearchNormalizer.normalizeText(value).contains(normalized)) ? 4 : 0;
    }

    public Map<TagFacet, Long> facets(SearchActor actor, SearchQuery query) {
        return eligible(actor)
                .map(document -> scored(document, query))
                .filter(result -> result.score() > 0)
                .flatMap(result -> documents.stream()
                        .filter(document -> document.songId().equals(result.songId()))
                        .filter(document -> eligibilityPolicy.canReturn(actor, document))
                        .flatMap(document -> document.tags().stream()))
                .collect(java.util.stream.Collectors.groupingBy(tag -> tag, java.util.stream.Collectors.counting()));
    }

    public List<SearchResult> semanticNeighbors(SearchActor actor, List<UUID> candidateSongIds, int limit) {
        if (candidateSongIds == null || limit <= 0) {
            return List.of();
        }
        return documents.stream()
                .filter(document -> candidateSongIds.contains(document.songId()))
                .filter(document -> eligibilityPolicy.canReturn(actor, document))
                .limit(limit)
                .map(document -> new SearchResult(document.songId(), document.arrangementId(), document.title(), document.arrangementLabel(), 1.0d))
                .toList();
    }

    public List<SearchResult> hydrate(SearchActor actor, List<SearchResult> candidates) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> documents.stream()
                        .filter(document -> document.songId().equals(candidate.songId()))
                        .anyMatch(document -> eligibilityPolicy.canReturn(actor, document)))
                .toList();
    }

    public List<SearchExplanation> explanations(SearchActor actor, List<SearchResult> results) {
        return hydrate(actor, results).stream()
                .map(result -> new SearchExplanation(result.songId(), result.arrangementId(),
                        result.rankingFactors().stream().map(RankingFactor::code).toList()))
                .toList();
    }


    public SearchDiagnostics diagnostics(SearchActor actor, SearchQuery query) {
        if (actor == null || actor.roles().stream().noneMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPPORT")
                || role.equals("role.admin") || role.equals("role.support"))) {
            throw new SecurityException("searchDiagnosticsRequiresSupportRole");
        }
        List<SearchResult> results = search(actor, query);
        Map<String, Long> factorCounts = results.stream()
                .flatMap(result -> result.rankingFactors().stream())
                .collect(Collectors.groupingBy(RankingFactor::code, Collectors.counting()));
        return new SearchDiagnostics(rankingProfile.version(), (int) eligible(actor).count(), results.size(), factorCounts, true);
    }

    public List<String> spellingCorrections(SearchActor actor, String prefix, int limit) {
        return autocomplete(actor, prefix, limit).stream()
                .map(AutocompleteSuggestion::value)
                .distinct()
                .toList();
    }

    private Stream<ApprovedSearchDocument> eligible(SearchActor actor) {
        return documents.stream().filter(document -> eligibilityPolicy.canReturn(actor, document));
    }

    private SearchActor defaultActor(UUID instanceId) {
        return new SearchActor("system-search", instanceId, java.util.Set.of("role.worship_leader"), java.util.Set.of(), true);
    }

    static int levenshtein(String left, String right) {
        int[] costs = new int[right.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            costs[0] = i;
            int northwest = i - 1;
            for (int j = 1; j <= right.length(); j++) {
                int current = costs[j];
                costs[j] = Math.min(1 + Math.min(costs[j], costs[j - 1]), left.charAt(i - 1) == right.charAt(j - 1) ? northwest : northwest + 1);
                northwest = current;
            }
        }
        return costs[right.length()];
    }
}
