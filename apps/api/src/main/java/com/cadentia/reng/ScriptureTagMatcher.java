package com.cadentia.reng;

import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.scripture.CanonicalScriptureReference;
import com.cadentia.catalog.scripture.CanonicalScriptureReference.MatchTier;
import com.cadentia.catalog.scripture.ScriptureReferenceParser;
import com.cadentia.reng.scoring.ScoringRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic matcher between requested scripture references (free text or normalized
 * references) and curated SCRIPTURE tags. When the request parses into canonical references,
 * matching is tier-aware; otherwise it falls back to the legacy normalized-substring comparison.
 */
public final class ScriptureTagMatcher {

    private final List<String> rawQueries;
    private final List<CanonicalScriptureReference> parsedQueries;

    private ScriptureTagMatcher(List<String> rawQueries, List<CanonicalScriptureReference> parsedQueries) {
        this.rawQueries = rawQueries;
        this.parsedQueries = parsedQueries;
    }

    public static ScriptureTagMatcher fromRawValues(List<String> rawValues) {
        List<String> raw = rawValues == null
                ? List.of()
                : rawValues.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
        List<CanonicalScriptureReference> parsed = raw.stream()
                .flatMap(value -> ScriptureReferenceParser.parse(value).stream())
                .toList();
        return new ScriptureTagMatcher(raw, parsed);
    }

    public static ScriptureTagMatcher fromRequest(ScoringRequest request) {
        return fromRawValues(rawScriptureValues(request));
    }

    public static List<String> rawScriptureValues(ScoringRequest request) {
        List<String> values = new ArrayList<>();
        if (request.verseText() != null && !request.verseText().isBlank()) {
            values.add(request.verseText());
        }
        values.addAll(request.scriptureReferences());
        return values;
    }

    public boolean requested() {
        return !rawQueries.isEmpty();
    }

    public boolean hasParsedQueries() {
        return !parsedQueries.isEmpty();
    }

    public MatchTier bestTier(RecommendationTag tag) {
        if (tag.tagType() != TagType.SCRIPTURE || parsedQueries.isEmpty()) {
            return MatchTier.NONE;
        }
        List<CanonicalScriptureReference> tagReferences = tagReferences(tag);
        if (tagReferences.isEmpty()) {
            return MatchTier.NONE;
        }
        return parsedQueries.stream()
                .flatMap(query -> tagReferences.stream().map(tagReference -> tagReference.matchTier(query)))
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(MatchTier.NONE);
    }

    public boolean matches(RecommendationTag tag) {
        if (tag.tagType() != TagType.SCRIPTURE) {
            return false;
        }
        if (hasParsedQueries()) {
            return bestTier(tag) != MatchTier.NONE;
        }
        String tagValue = normalize(tag.name() + " " + tag.slug());
        if (tagValue.isEmpty()) {
            return false;
        }
        return rawQueries.stream()
                .map(ScriptureTagMatcher::normalize)
                .filter(query -> !query.isEmpty())
                .anyMatch(query -> tagValue.contains(query) || query.contains(tagValue));
    }

    private static List<CanonicalScriptureReference> tagReferences(RecommendationTag tag) {
        List<CanonicalScriptureReference> fromName = ScriptureReferenceParser.parse(tag.name());
        return fromName.isEmpty() ? ScriptureReferenceParser.parse(tag.slug()) : fromName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
