package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetExplanationFactory {

    public List<RecommendationExplanationFact> build(
            ScoringRequest request,
            List<CandidateFeatureScorer.CandidateFeatureScore> selected,
            List<CandidateFeatureScorer.CandidateFeatureScore> availableCandidates,
            List<OrderedSetItem> orderedItems) {
        RecommendationExplanationSubject setSubject = new RecommendationExplanationSubject("set", "recommended-set");
        List<RecommendationExplanationFact> facts = new ArrayList<>();

        int targetSize = request.praiseCount() + request.worshipCount();
        facts.add(new RecommendationExplanationFact(
                "COUNT_TARGET_MET",
                selected.size() == targetSize ? "info" : "warning",
                "set",
                setSubject,
                "set.count_target",
                Map.of("selected", selected.size(), "target", targetSize),
                List.of(new RecommendationExplanationEvidence("request", "slots.counts", null, 1.0d)),
                null));

        if (selected.size() < targetSize) {
            facts.add(new RecommendationExplanationFact(
                    "INSUFFICIENT_CANDIDATES",
                    "warning",
                    "set",
                    setSubject,
                    "warning.insufficient_candidates",
                    Map.of("selected", selected.size(), "target", targetSize, "availableCandidates", availableCandidates.size()),
                    List.of(new RecommendationExplanationEvidence("score", "candidate_pool", "size", 1.0d)),
                    null));
        }

        Set<String> keyCenters = new HashSet<>();
        selected.stream().map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                .map(RecommendableArrangement::musicalKey)
                .filter(key -> key != null && !key.isBlank())
                .map(String::toLowerCase)
                .forEach(keyCenters::add);
        facts.add(new RecommendationExplanationFact(
                "KEY_CENTER_POLICY_MET",
                keyCenters.size() <= request.keyPolicy().maxKeyCenters() ? "info" : "warning",
                "set",
                setSubject,
                "set.key_centers",
                Map.of("distinctKeyCenters", keyCenters.size(), "maxKeyCenters", request.keyPolicy().maxKeyCenters()),
                List.of(new RecommendationExplanationEvidence("request", "slots.keyPolicy.maxKeyCenters", null, 1.0d)),
                null));

        if (request.energyArc() != null && !request.energyArc().isBlank() && orderedItems.size() >= 2) {
            OrderedSetItem first = orderedItems.get(0);
            OrderedSetItem last = orderedItems.get(orderedItems.size() - 1);
            facts.add(new RecommendationExplanationFact(
                    "SET_ENERGY_ARC_MATCH",
                    "info",
                    "set",
                    setSubject,
                    "set.energy_arc",
                    Map.of("requestedArc", request.energyArc(), "firstPosition", first.position(), "lastPosition", last.position()),
                    List.of(new RecommendationExplanationEvidence("request", "slots.energyArc", null, 1.0d)),
                    null));
        }

        if (!request.themeHints().isEmpty()) {
            long covered = selected.stream()
                    .map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                    .filter(candidate -> candidate.matchedTags().stream().anyMatch(tag -> request.themeHints().contains(tag.slug())))
                    .count();
            facts.add(new RecommendationExplanationFact(
                    "THEME_COVERAGE",
                    covered > 0 ? "info" : "warning",
                    "set",
                    setSubject,
                    "set.theme_coverage",
                    Map.of("coveredItems", covered, "selectedItems", selected.size(), "requestedThemes", String.join(",", request.themeHints())),
                    List.of(new RecommendationExplanationEvidence("request", "slots.themeHints", null, 1.0d)),
                    null));
        }

        if (request.defaultsApplied() != null) {
            ScoringRequest.DefaultsApplied defaults = request.defaultsApplied();
            if (defaults.countsDefaulted()
                    || defaults.keyPolicyDefaulted()
                    || defaults.tempoPolicyDefaulted()
                    || defaults.languageDefaulted()) {
                facts.add(new RecommendationExplanationFact(
                        "REQUEST_DEFAULTS_APPLIED",
                        "info",
                        "set",
                        setSubject,
                        "set.defaults_applied",
                        Map.of(
                                "countsDefaulted", defaults.countsDefaulted(),
                                "keyPolicyDefaulted", defaults.keyPolicyDefaulted(),
                                "tempoPolicyDefaulted", defaults.tempoPolicyDefaulted(),
                                "languageDefaulted", defaults.languageDefaulted()),
                        List.of(new RecommendationExplanationEvidence("request", "slots.defaultsApplied", null, 1.0d)),
                        null));
            }
        }

        boolean hasLowConfidenceMetadata = orderedItems.stream()
                .flatMap(item -> item.explanationFacts().stream())
                .anyMatch(fact -> "METADATA_LOW_CONFIDENCE".equals(fact.code()));
        if (hasLowConfidenceMetadata) {
            facts.add(new RecommendationExplanationFact(
                    "LOW_CONFIDENCE_METADATA_PRESENT",
                    "warning",
                    "set",
                    setSubject,
                    "warning.low_confidence_metadata",
                    Map.of("reason", "metadata_confidence_below_one"),
                    List.of(new RecommendationExplanationEvidence("catalog", "arrangement", "metadata", 0.8d)),
                    null));
        }

        return List.copyOf(facts);
    }
}
