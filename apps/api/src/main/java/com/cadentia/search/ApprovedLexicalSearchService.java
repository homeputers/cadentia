package com.cadentia.search;

import com.cadentia.search.ApprovedSearchModels.ApprovedSearchDocument;
import com.cadentia.search.ApprovedSearchModels.AutocompleteSuggestion;
import com.cadentia.search.ApprovedSearchModels.SearchQuery;
import com.cadentia.search.ApprovedSearchModels.SearchResult;
import com.cadentia.search.ApprovedSearchModels.SuggestionType;
import com.cadentia.search.ApprovedSearchModels.TagFacet;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class ApprovedLexicalSearchService {

    private static final int MAX_DISTANCE = 2;
    private final List<ApprovedSearchDocument> documents;

    public ApprovedLexicalSearchService(List<ApprovedSearchDocument> documents) {
        this.documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public List<SearchResult> search(SearchQuery query) {
        return visible(query.instanceId())
                .map(document -> scored(document, query))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed().thenComparing(SearchResult::title))
                .toList();
    }

    public List<AutocompleteSuggestion> autocomplete(UUID instanceId, String prefix, int limit) {
        String normalizedPrefix = SearchNormalizer.normalizeText(prefix);
        if (normalizedPrefix.length() < 2 || limit <= 0) {
            return List.of();
        }
        return visible(instanceId)
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
        double score = scoreText(document, query.text(), 1.0d)
                + scoreText(document, query.title(), 4.0d)
                + scoreScripture(document, query.scripture())
                + scoreTag(document, query.tag())
                + scoreContributor(document, query.contributor())
                + scoreKey(document, query.musicalKey())
                + scoreBpm(document, query.minBpm(), query.maxBpm())
                + scoreArrangement(document, query.arrangement());
        return new SearchResult(document.songId(), document.arrangementId(), document.title(), document.arrangementLabel(), score);
    }

    private double scoreText(ApprovedSearchDocument document, String text, double weight) {
        String normalized = SearchNormalizer.normalizeText(text);
        if (normalized.isBlank()) {
            return 0;
        }
        double score = matchScore(normalized, document.title()) * weight;
        for (String alternateTitle : document.alternateTitles()) {
            score = Math.max(score, matchScore(normalized, alternateTitle) * (weight - 0.5d));
        }
        String haystack = SearchNormalizer.normalizeText(String.join(" ", document.lyricsMetadata()) + " "
                + String.join(" ", document.arrangementMetadata()));
        if (haystack.contains(normalized)) {
            score = Math.max(score, weight);
        }
        return score;
    }

    private double matchScore(String normalizedNeedle, String value) {
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

    private Stream<ApprovedSearchDocument> visible(UUID instanceId) {
        return documents.stream().filter(document -> document.safeFor(instanceId));
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
