package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import com.cadentia.reng.ScriptureTagMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SetExplanationFactory {

    public List<RecommendationExplanationFact> build(
            ScoringRequest request,
            List<CandidateFeatureScorer.CandidateFeatureScore> selected,
            List<CandidateFeatureScorer.CandidateFeatureScore> availableCandidates,
            List<OrderedSetItem> orderedItems,
            EnergyArcEvaluation energyArcEvaluation) {
        RecommendationExplanationSubject setSubject = new RecommendationExplanationSubject("set", "recommended-set");
        List<RecommendationExplanationFact> facts = new ArrayList<>();

        int targetSize = request.praiseCount() + request.worshipCount();
        RoleCounts roleCounts = selectedRoleCounts(selected);
        facts.add(new RecommendationExplanationFact(
                "COUNT_TARGET_MET",
                selected.size() == targetSize ? "info" : "warning",
                "set",
                setSubject,
                "set.count_target",
                Map.ofEntries(
                        Map.entry("selected", selected.size()),
                        Map.entry("target", targetSize),
                        Map.entry("selectedPraise", roleCounts.praise()),
                        Map.entry("requestedPraise", request.praiseCount()),
                        Map.entry("selectedWorship", roleCounts.worship()),
                        Map.entry("requestedWorship", request.worshipCount())),
                List.of(new RecommendationExplanationEvidence("request", "slots.counts", null, 1.0d)),
                null));

        if (selected.size() < targetSize) {
            String limitation = availableCandidates.size() < targetSize
                    ? "insufficient_eligible_catalog"
                    : "policy_limited_selection";
            facts.add(new RecommendationExplanationFact(
                    "INSUFFICIENT_CANDIDATES",
                    "warning",
                    "set",
                    setSubject,
                    "warning.insufficient_candidates",
                    Map.of(
                            "selected", selected.size(),
                            "target", targetSize,
                            "availableCandidates", availableCandidates.size(),
                            "limitation", limitation),
                    List.of(new RecommendationExplanationEvidence("score", "candidate_pool", "size", 1.0d)),
                    null));
        }

        Set<String> keyCenters = selectedKeyCenters(selected);
        facts.add(new RecommendationExplanationFact(
                "KEY_CENTER_POLICY_MET",
                keyCenters.size() <= request.keyPolicy().maxKeyCenters() ? "info" : "warning",
                "set",
                setSubject,
                "set.key_centers",
                Map.of(
                        "distinctKeyCenters", keyCenters.size(),
                        "maxKeyCenters", request.keyPolicy().maxKeyCenters(),
                        "preferSameKey", request.keyPolicy().preferSameKey(),
                        "allowRelativeMajorMinor", request.keyPolicy().allowRelativeMajorMinor(),
                        "keyCenters", String.join(",", keyCenters)),
                List.of(new RecommendationExplanationEvidence("request", "slots.keyPolicy", null, 1.0d)),
                null));

        TempoPolicyOutcome tempoOutcome = tempoPolicyOutcome(orderedItems, request.tempoPolicy().maxJumpBpm());
        facts.add(new RecommendationExplanationFact(
                "TEMPO_POLICY_MET",
                tempoOutcome.exceededJumps() == 0 ? "info" : "warning",
                "set",
                setSubject,
                "set.tempo_policy",
                Map.of(
                        "maxJumpBpm", request.tempoPolicy().maxJumpBpm(),
                        "evaluatedTransitions", tempoOutcome.evaluatedTransitions(),
                        "maxObservedJumpBpm", tempoOutcome.maxObservedJumpBpm(),
                        "exceededJumps", tempoOutcome.exceededJumps()),
                List.of(new RecommendationExplanationEvidence("request", "slots.tempoPolicy.maxJumpBpm", null, 1.0d)),
                null));

        if (energyArcEvaluation != null && orderedItems.size() >= 2) {
            OrderedSetItem first = orderedItems.get(0);
            OrderedSetItem last = orderedItems.get(orderedItems.size() - 1);
            List<String> tradeoffs = energyArcEvaluation.tradeoffCodes();
            facts.add(new RecommendationExplanationFact(
                    "SET_ENERGY_ARC_MATCH",
                    tradeoffs.isEmpty() ? "info" : "warning",
                    "set",
                    setSubject,
                    "set.energy_arc",
                    Map.of(
                            "requestedArc", energyArcEvaluation.arc().name().toLowerCase(Locale.ROOT),
                            "arcVersion", energyArcEvaluation.arcVersion(),
                            "firstPosition", first.position(),
                            "lastPosition", last.position(),
                            "firstEnergy", selected.get(first.position() - 1).candidate().energy(),
                            "lastEnergy", selected.get(last.position() - 1).candidate().energy(),
                            "shapeScore", componentScore(energyArcEvaluation, EnergyArcEvaluator.ARC_SHAPE_MATCH),
                            "sectionBalanceScore", componentScore(energyArcEvaluation, EnergyArcEvaluator.ARC_SECTION_BALANCE),
                            "tradeoffs", String.join(",", tradeoffs)),
                    List.of(new RecommendationExplanationEvidence("score", "energy_arc", energyArcEvaluation.arcVersion(), energyArcEvaluation.totalScore())),
                    energyArcEvaluation.totalScore()));
        }

        if (!request.themeHints().isEmpty()) {
            Set<String> coveredThemes = coveredTagSlugs(selected, request.themeHints(), TagType.THEME);
            facts.add(new RecommendationExplanationFact(
                    "THEME_COVERAGE",
                    coveredThemes.isEmpty() ? "warning" : "info",
                    "set",
                    setSubject,
                    "set.theme_coverage",
                    Map.of(
                            "coveredItems", coveredItemCount(selected, request.themeHints(), TagType.THEME),
                            "selectedItems", selected.size(),
                            "requestedThemes", String.join(",", request.themeHints()),
                            "coveredThemes", String.join(",", coveredThemes)),
                    List.of(new RecommendationExplanationEvidence("catalog", "approved_taxonomy.theme", null, 1.0d)),
                    null));
        }

        if (!ScriptureTagMatcher.rawScriptureValues(request).isEmpty()) {
            Set<String> coveredScripture = coveredScriptureSlugs(selected);
            facts.add(new RecommendationExplanationFact(
                    "SCRIPTURE_COVERAGE",
                    coveredScripture.isEmpty() ? "warning" : "info",
                    "set",
                    setSubject,
                    "set.scripture_coverage",
                    Map.of(
                            "coveredItems", coveredScriptureItemCount(selected),
                            "selectedItems", selected.size(),
                            "coveredScripture", String.join(",", coveredScripture)),
                    List.of(new RecommendationExplanationEvidence("catalog", "curated_mapping.scripture", null, 1.0d)),
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
                        Map.ofEntries(
                                Map.entry("countsDefaulted", defaults.countsDefaulted()),
                                Map.entry("keyPolicyDefaulted", defaults.keyPolicyDefaulted()),
                                Map.entry("tempoPolicyDefaulted", defaults.tempoPolicyDefaulted()),
                                Map.entry("languageDefaulted", defaults.languageDefaulted()),
                                Map.entry("defaultPraise", request.praiseCount()),
                                Map.entry("defaultWorship", request.worshipCount()),
                                Map.entry("preferSameKey", request.keyPolicy().preferSameKey()),
                                Map.entry("allowRelativeMajorMinor", request.keyPolicy().allowRelativeMajorMinor()),
                                Map.entry("maxKeyCenters", request.keyPolicy().maxKeyCenters()),
                                Map.entry("maxJumpBpm", request.tempoPolicy().maxJumpBpm())),
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

    public List<RecommendationExplanationFact> build(
            ScoringRequest request,
            List<CandidateFeatureScorer.CandidateFeatureScore> selected,
            List<CandidateFeatureScorer.CandidateFeatureScore> availableCandidates,
            List<OrderedSetItem> orderedItems) {
        return build(request, selected, availableCandidates, orderedItems, null);
    }

    private static RoleCounts selectedRoleCounts(List<CandidateFeatureScorer.CandidateFeatureScore> selected) {
        int praise = 0;
        int worship = 0;
        for (CandidateFeatureScorer.CandidateFeatureScore score : selected) {
            Set<String> roles = normalizedRoles(score.candidate());
            if (roles.contains("praise")) {
                praise++;
            }
            if (roles.contains("worship")) {
                worship++;
            }
        }
        return new RoleCounts(praise, worship);
    }

    private static Set<String> normalizedRoles(RecommendableArrangement candidate) {
        Set<String> roles = new HashSet<>();
        candidate.tags().stream().map(SetExplanationFactory::normalize).forEach(roles::add);
        candidate.controlledTags().stream()
                .map(RecommendationTag::slug)
                .map(SetExplanationFactory::normalize)
                .forEach(roles::add);
        return roles;
    }

    private static Set<String> selectedKeyCenters(List<CandidateFeatureScorer.CandidateFeatureScore> selected) {
        Set<String> keyCenters = new TreeSet<>();
        selected.stream().map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                .map(RecommendableArrangement::musicalKey)
                .filter(key -> key != null && !key.isBlank())
                .map(SetExplanationFactory::normalize)
                .forEach(keyCenters::add);
        return keyCenters;
    }

    private static TempoPolicyOutcome tempoPolicyOutcome(List<OrderedSetItem> orderedItems, int maxJumpBpm) {
        int evaluatedTransitions = 0;
        int maxObservedJumpBpm = 0;
        int exceededJumps = 0;
        for (OrderedSetItem item : orderedItems) {
            TransitionScore transitionScore = item.transitionFromPrevious();
            if (transitionScore == null) {
                continue;
            }
            Integer jump = item.explanationFacts().stream()
                    .filter(fact -> "TEMPO_POLICY_OK".equals(fact.code())
                            || "TEMPO_TRADEOFF_ACCEPTED".equals(fact.code()))
                    .map(fact -> fact.values().get("jumpBpm"))
                    .filter(Integer.class::isInstance)
                    .map(Integer.class::cast)
                    .findFirst()
                    .orElse(null);
            if (jump == null) {
                jump = item.explanationFacts().stream()
                        .filter(fact -> "TEMPO_POLICY_OK".equals(fact.code()))
                        .map(fact -> Math.abs(((Number) fact.values().get("toBpm")).intValue()
                                - ((Number) fact.values().get("fromBpm")).intValue()))
                        .findFirst()
                        .orElse(null);
            }
            if (jump == null) {
                continue;
            }
            evaluatedTransitions++;
            maxObservedJumpBpm = Math.max(maxObservedJumpBpm, jump);
            if (jump > maxJumpBpm) {
                exceededJumps++;
            }
        }
        return new TempoPolicyOutcome(evaluatedTransitions, maxObservedJumpBpm, exceededJumps);
    }

    private static Set<String> coveredTagSlugs(
            List<CandidateFeatureScorer.CandidateFeatureScore> selected,
            List<String> requestedSlugs,
            TagType tagType) {
        Set<String> requested = new HashSet<>();
        requestedSlugs.stream().map(SetExplanationFactory::normalize).forEach(requested::add);
        Set<String> covered = new TreeSet<>();
        selected.stream()
                .map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                .flatMap(candidate -> candidate.matchedTags().stream())
                .filter(tag -> tag.tagType() == tagType)
                .map(RecommendationTag::slug)
                .map(SetExplanationFactory::normalize)
                .filter(requested::contains)
                .forEach(covered::add);
        return covered;
    }

    private static long coveredItemCount(
            List<CandidateFeatureScorer.CandidateFeatureScore> selected,
            List<String> requestedSlugs,
            TagType tagType) {
        Set<String> requested = new HashSet<>();
        requestedSlugs.stream().map(SetExplanationFactory::normalize).forEach(requested::add);
        return selected.stream()
                .map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                .filter(candidate -> candidate.matchedTags().stream()
                        .filter(tag -> tag.tagType() == tagType)
                        .map(RecommendationTag::slug)
                        .map(SetExplanationFactory::normalize)
                        .anyMatch(requested::contains))
                .count();
    }

    private static Set<String> coveredScriptureSlugs(List<CandidateFeatureScorer.CandidateFeatureScore> selected) {
        Set<String> covered = new TreeSet<>();
        selected.stream()
                .map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                .flatMap(candidate -> candidate.matchedTags().stream())
                .filter(tag -> tag.tagType() == TagType.SCRIPTURE)
                .map(RecommendationTag::slug)
                .map(SetExplanationFactory::normalize)
                .forEach(covered::add);
        return covered;
    }

    private static long coveredScriptureItemCount(List<CandidateFeatureScorer.CandidateFeatureScore> selected) {
        return selected.stream()
                .map(CandidateFeatureScorer.CandidateFeatureScore::candidate)
                .filter(candidate -> candidate.matchedTags().stream()
                        .anyMatch(tag -> tag.tagType() == TagType.SCRIPTURE))
                .count();
    }

    private static double componentScore(EnergyArcEvaluation evaluation, String componentCode) {
        return evaluation.setLevelComponents().stream()
                .filter(component -> componentCode.equals(component.componentCode()))
                .mapToDouble(ScoringComponentScore::rawScore)
                .findFirst()
                .orElse(0.0d);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RoleCounts(int praise, int worship) {}

    private record TempoPolicyOutcome(int evaluatedTransitions, int maxObservedJumpBpm, int exceededJumps) {}
}
