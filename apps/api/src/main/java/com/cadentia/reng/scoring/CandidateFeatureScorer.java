package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CandidateFeatureScorer {

    public static final String THEME_MATCH = "theme_match";
    public static final String SCRIPTURE_MATCH = "scripture_match";
    public static final String ROLE_FIT = "role_fit";
    public static final String MUSICAL_FIT = "musical_fit";
    public static final String ENERGY_FIT = "energy_fit";
    public static final String METADATA_CONFIDENCE = "metadata_confidence";

    public List<CandidateFeatureScore> scoreCandidates(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile) {
        return candidates.stream()
                .map(candidate -> scoreCandidate(candidate, request, profile))
                .sorted(Comparator
                        .comparingDouble(CandidateFeatureScore::totalScore)
                        .reversed()
                        .thenComparing(score -> score.candidate().songId())
                        .thenComparing(score -> score.candidate().arrangementId()))
                .toList();
    }

    public CandidateFeatureScore scoreCandidate(
            RecommendableArrangement candidate,
            ScoringRequest request,
            ScoringProfile profile) {
        List<ScoringComponentScore> componentScores = new ArrayList<>();
        componentScores.add(componentScore(THEME_MATCH, themeMatch(candidate, request), profile.componentWeights()));
        componentScores.add(componentScore(SCRIPTURE_MATCH, scriptureMatch(candidate, request), profile.componentWeights()));
        componentScores.add(componentScore(ROLE_FIT, roleFit(candidate), profile.componentWeights()));
        componentScores.add(componentScore(MUSICAL_FIT, musicalFit(candidate), profile.componentWeights()));
        componentScores.add(componentScore(ENERGY_FIT, energyFit(candidate), profile.componentWeights()));
        componentScores.add(componentScore(
                METADATA_CONFIDENCE,
                metadataConfidence(candidate),
                profile.componentWeights()));

        double total = componentScores.stream().mapToDouble(ScoringComponentScore::weightedContribution).sum();
        return new CandidateFeatureScore(candidate, List.copyOf(componentScores), total);
    }

    private static ScoringComponentScore componentScore(String code, double raw, Map<String, Double> weights) {
        double weight = weights.getOrDefault(code, 0.0d);
        return new ScoringComponentScore(code, raw, raw * weight);
    }

    private static double themeMatch(RecommendableArrangement candidate, ScoringRequest request) {
        if (request.themeHints().isEmpty()) {
            return 0.5d;
        }
        long matched = request.themeHints().stream()
                .map(CandidateFeatureScorer::normalize)
                .distinct()
                .filter(theme -> candidate.matchedTags().stream()
                        .filter(tag -> tag.tagType() == TagType.THEME)
                        .map(RecommendationTag::slug)
                        .map(CandidateFeatureScorer::normalize)
                        .anyMatch(theme::equals))
                .count();
        return matched / (double) request.themeHints().stream().map(CandidateFeatureScorer::normalize).distinct().count();
    }

    private static double scriptureMatch(RecommendableArrangement candidate, ScoringRequest request) {
        if (request.verseText() == null || request.verseText().isBlank()) {
            return 0.5d;
        }
        String verse = normalize(request.verseText());
        boolean matched = candidate.matchedTags().stream()
                .filter(tag -> tag.tagType() == TagType.SCRIPTURE)
                .map(tag -> normalize(tag.name() + " " + tag.slug()))
                .anyMatch(value -> value.contains(verse) || verse.contains(value));
        return matched ? 1.0d : 0.0d;
    }

    private static double roleFit(RecommendableArrangement candidate) {
        boolean praise = candidate.tags().stream().map(CandidateFeatureScorer::normalize).anyMatch("praise"::equals);
        boolean worship = candidate.tags().stream().map(CandidateFeatureScorer::normalize).anyMatch("worship"::equals);
        if (praise && worship) {
            return 1.0d;
        }
        if (praise || worship) {
            return 0.8d;
        }
        return 0.3d;
    }

    private static double musicalFit(RecommendableArrangement candidate) {
        if (candidate.musicalKey() == null || candidate.musicalKey().isBlank() || candidate.bpm() <= 0) {
            return 0.4d;
        }
        return 1.0d;
    }

    private static double energyFit(RecommendableArrangement candidate) {
        int energy = candidate.energy();
        if (energy <= 0) {
            return 0.5d;
        }
        double normalized = Math.max(0d, Math.min(energy, 100)) / 100d;
        return 1.0d - Math.abs(0.7d - normalized);
    }

    private static double metadataConfidence(RecommendableArrangement candidate) {
        boolean hasKey = candidate.musicalKey() != null && !candidate.musicalKey().isBlank();
        boolean hasTempo = candidate.bpm() > 0;
        boolean hasMeter = candidate.timeSignature() != null && !candidate.timeSignature().isBlank();
        int present = (hasKey ? 1 : 0) + (hasTempo ? 1 : 0) + (hasMeter ? 1 : 0);
        return present / 3.0d;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CandidateFeatureScore(
            RecommendableArrangement candidate,
            List<ScoringComponentScore> componentScores,
            double totalScore) {

        public CandidateFeatureScore {
            componentScores = componentScores == null ? List.of() : List.copyOf(componentScores);
        }
    }
}
