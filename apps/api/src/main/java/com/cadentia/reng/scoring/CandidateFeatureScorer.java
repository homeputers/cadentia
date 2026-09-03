package com.cadentia.reng.scoring;

import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.scripture.CanonicalScriptureReference.MatchTier;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import com.cadentia.reng.ScriptureTagMatcher;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ScoringAdjustment;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ValidatedPluginContributions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CandidateFeatureScorer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CandidateFeatureScorer.class);

    public static final String THEME_MATCH = "theme_match";
    public static final String SCRIPTURE_MATCH = "scripture_match";
    public static final String ROLE_FIT = "role_fit";
    public static final String MUSICAL_FIT = "musical_fit";
    public static final String ENERGY_FIT = "energy_fit";
    public static final String METADATA_CONFIDENCE = "metadata_confidence";
    public static final String FEEDBACK_TUNING = "feedback_tuning";
    public static final String TEAM_SUITABILITY = "team_suitability";
    public static final String ASSET_AVAILABILITY = "asset_availability";

    private final TeamSuitabilityEvaluator teamSuitabilityEvaluator = new TeamSuitabilityEvaluator();

    public List<CandidateFeatureScore> scoreCandidates(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile) {
        return scoreCandidates(candidates, request, profile, Map.of());
    }

    public List<CandidateFeatureScore> scoreCandidates(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile,
            Map<UUID, Double> feedbackContributions) {
        return scoreCandidates(candidates, request, profile, feedbackContributions, Map.of());
    }

    public List<CandidateFeatureScore> scoreCandidates(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile,
            ValidatedPluginContributions pluginContributions) {
        return scoreCandidates(candidates, request, profile, Map.of(), Map.of(), pluginContributions);
    }

    public List<CandidateFeatureScore> scoreCandidates(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile,
            Map<UUID, Double> feedbackContributions,
            Map<UUID, Boolean> assetAvailability) {
        return scoreCandidates(candidates, request, profile, feedbackContributions, assetAvailability, null);
    }

    public List<CandidateFeatureScore> scoreCandidates(
            List<RecommendableArrangement> candidates,
            ScoringRequest request,
            ScoringProfile profile,
            Map<UUID, Double> feedbackContributions,
            Map<UUID, Boolean> assetAvailability,
            ValidatedPluginContributions pluginContributions) {
        emitFeedbackImpactDistribution(feedbackContributions);
        return candidates.stream()
                .map(candidate -> scoreCandidate(
                        candidate,
                        request,
                        profile,
                        feedbackContributions,
                        assetAvailability,
                        pluginContributions))
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
        return scoreCandidate(candidate, request, profile, Map.of());
    }

    public CandidateFeatureScore scoreCandidate(
            RecommendableArrangement candidate,
            ScoringRequest request,
            ScoringProfile profile,
            Map<UUID, Double> feedbackContributions) {
        return scoreCandidate(candidate, request, profile, feedbackContributions, Map.of());
    }

    public CandidateFeatureScore scoreCandidate(
            RecommendableArrangement candidate,
            ScoringRequest request,
            ScoringProfile profile,
            Map<UUID, Double> feedbackContributions,
            Map<UUID, Boolean> assetAvailability) {
        return scoreCandidate(candidate, request, profile, feedbackContributions, assetAvailability, null);
    }

    public CandidateFeatureScore scoreCandidate(
            RecommendableArrangement candidate,
            ScoringRequest request,
            ScoringProfile profile,
            Map<UUID, Double> feedbackContributions,
            Map<UUID, Boolean> assetAvailability,
            ValidatedPluginContributions pluginContributions) {
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
        componentScores.add(componentScore(
                FEEDBACK_TUNING,
                feedbackContributions.getOrDefault(candidate.arrangementId(), 0.0d),
                profile.componentWeights()));
        if (profile.componentWeights().containsKey(ASSET_AVAILABILITY)) {
            componentScores.add(componentScore(
                    ASSET_AVAILABILITY,
                    assetAvailability.getOrDefault(candidate.arrangementId(), false) ? 1.0d : 0.0d,
                    profile.componentWeights()));
        }
        if (hasTeamScoringInput(profile)) {
            componentScores.add(componentScore(
                    TEAM_SUITABILITY,
                    teamSuitabilityEvaluator.scoringRawScore(candidate, request, profile),
                    profile.componentWeights()));
        }
        if (pluginContributions != null) {
            componentScores.add(pluginComponentScore(candidate, pluginContributions));
        }

        double total = componentScores.stream().mapToDouble(ScoringComponentScore::weightedContribution).sum();
        return new CandidateFeatureScore(candidate, List.copyOf(componentScores), total);
    }

    private static ScoringComponentScore pluginComponentScore(
            RecommendableArrangement candidate,
            ValidatedPluginContributions pluginContributions) {
        if (pluginContributions == null) {
            return new ScoringComponentScore(
                    RecommendationPluginContributionModels.PLUGIN_COMPONENT_CODE,
                    0.0d,
                    0.0d,
                    1.0d,
                    List.of("PLUGIN_CONTRIBUTION_NONE"));
        }
        double delta = pluginContributions.scoringDeltaByArrangement().getOrDefault(candidate.arrangementId(), 0.0d);
        List<String> reasonCodes = pluginContributions.scoringAdjustmentsByArrangement()
                .getOrDefault(candidate.arrangementId(), List.of())
                .stream()
                .map(CandidateFeatureScorer::safePluginReasonCode)
                .distinct()
                .sorted()
                .toList();
        return new ScoringComponentScore(
                RecommendationPluginContributionModels.PLUGIN_COMPONENT_CODE,
                delta,
                delta,
                1.0d,
                reasonCodes.isEmpty() ? ScoringComponentScore.contributionReasonCodes(delta) : reasonCodes);
    }

    private static String safePluginReasonCode(ScoringAdjustment adjustment) {
        return "PLUGIN:"
                + normalizeReason(adjustment.metadata().pluginId())
                + ":"
                + normalizeReason(adjustment.metadata().pluginVersion())
                + ":"
                + normalizeReason(adjustment.metadata().configurationVersion())
                + ":"
                + normalizeReason(adjustment.reasonCode());
    }

    private static String normalizeReason(String value) {
        return value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private static boolean hasTeamScoringInput(ScoringProfile profile) {
        return profile.teamConstraintModes().values().stream()
                .anyMatch(mode -> mode == TeamConstraintMode.SCORING_INPUT);
    }

    private void emitFeedbackImpactDistribution(Map<UUID, Double> feedbackContributions) {
        double min = feedbackContributions.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0d);
        double max = feedbackContributions.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0d);
        double avg = feedbackContributions.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
        LOGGER.info(
                "feedback_observability event=ranking_impact_distribution candidates={} min={} max={} avg={}",
                feedbackContributions.size(),
                min,
                max,
                avg);
    }

    private static ScoringComponentScore componentScore(String code, double raw, Map<String, Double> weights) {
        double weight = weights.getOrDefault(code, 0.0d);
        return new ScoringComponentScore(code, raw, raw * weight, weight, ScoringComponentScore.contributionReasonCodes(raw * weight));
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
        ScriptureTagMatcher matcher = ScriptureTagMatcher.fromRequest(request);
        if (!matcher.requested()) {
            return 0.5d;
        }
        List<RecommendationTag> scriptureTags = candidate.matchedTags().stream()
                .filter(tag -> tag.tagType() == TagType.SCRIPTURE)
                .toList();
        if (matcher.hasParsedQueries()) {
            MatchTier bestTier = scriptureTags.stream()
                    .map(matcher::bestTier)
                    .max(Comparator.comparingInt(Enum::ordinal))
                    .orElse(MatchTier.NONE);
            return switch (bestTier) {
                case EXACT_OR_OVERLAP -> 1.0d;
                case CHAPTER -> 0.75d;
                case BOOK -> 0.5d;
                case NONE -> 0.0d;
            };
        }
        return scriptureTags.stream().anyMatch(matcher::matches) ? 1.0d : 0.0d;
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
