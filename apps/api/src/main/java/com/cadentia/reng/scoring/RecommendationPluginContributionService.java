package com.cadentia.reng.scoring;

import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ConstraintContribution;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.PluginContributionRequest;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.PluginContributionSet;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.RecommendationConstraintPlugin;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.RecommendationScoringPlugin;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ScoringAdjustment;
import com.cadentia.reng.scoring.RecommendationPluginContributionModels.ValidatedPluginContributions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RecommendationPluginContributionService {
    private final List<RecommendationConstraintPlugin> constraintPlugins;
    private final List<RecommendationScoringPlugin> scoringPlugins;
    private final boolean failClosed;

    public RecommendationPluginContributionService(
            List<RecommendationConstraintPlugin> constraintPlugins,
            List<RecommendationScoringPlugin> scoringPlugins,
            boolean failClosed) {
        this.constraintPlugins = constraintPlugins == null ? List.of() : List.copyOf(constraintPlugins);
        this.scoringPlugins = scoringPlugins == null ? List.of() : List.copyOf(scoringPlugins);
        this.failClosed = failClosed;
    }

    public PluginContributionSet collect(PluginContributionRequest request) {
        List<ConstraintContribution> constraints = new ArrayList<>();
        List<ScoringAdjustment> adjustments = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (RecommendationConstraintPlugin plugin : constraintPlugins) {
            try {
                constraints.addAll(nullToEmpty(plugin.constraints(request)));
            } catch (RuntimeException ex) {
                errors.add("PLUGIN_CONSTRAINT_FAILED");
                if (failClosed) {
                    throw ex;
                }
            }
        }
        for (RecommendationScoringPlugin plugin : scoringPlugins) {
            try {
                adjustments.addAll(nullToEmpty(plugin.scoreAdjustments(request)));
            } catch (RuntimeException ex) {
                errors.add("PLUGIN_SCORING_FAILED");
                if (failClosed) {
                    throw ex;
                }
            }
        }
        return validate(request, new PluginContributionSet(constraints, adjustments, errors));
    }

    public PluginContributionSet validate(PluginContributionRequest request, PluginContributionSet contributions) {
        ValidatedPluginContributions validated = validated(request, contributions);
        List<ConstraintContribution> constraints = validated.constraintsByArrangement().values().stream().flatMap(List::stream).toList();
        List<ScoringAdjustment> adjustments = validated.scoringAdjustmentsByArrangement().values().stream().flatMap(List::stream).toList();
        return new PluginContributionSet(constraints, adjustments, validated.safeErrors());
    }

    public ValidatedPluginContributions validated(PluginContributionRequest request, PluginContributionSet contributions) {
        var approved = request.approvedArrangementIds();
        Map<UUID, List<ConstraintContribution>> constraints = new LinkedHashMap<>();
        Map<UUID, List<ScoringAdjustment>> adjustments = new LinkedHashMap<>();
        Map<UUID, Double> totals = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>(contributions.safeErrors());
        for (ConstraintContribution contribution : contributions.constraints()) {
            if (contribution == null || !approved.contains(contribution.arrangementId()) || contribution.metadata() == null
                    || contribution.reasonCode() == null || contribution.type() == null || !bounded(contribution.scoreDelta(), 0.10d)) {
                errors.add("PLUGIN_CONSTRAINT_INVALID");
                continue;
            }
            constraints.computeIfAbsent(contribution.arrangementId(), ignored -> new ArrayList<>()).add(contribution);
        }
        for (ScoringAdjustment adjustment : contributions.scoringAdjustments()) {
            if (adjustment == null || !approved.contains(adjustment.arrangementId()) || adjustment.metadata() == null
                    || adjustment.reasonCode() == null || adjustment.componentCode() == null
                    || !bounded(adjustment.scoreDelta(), 0.20d)) {
                errors.add("PLUGIN_SCORING_INVALID");
                continue;
            }
            adjustments.computeIfAbsent(adjustment.arrangementId(), ignored -> new ArrayList<>()).add(adjustment);
            totals.merge(adjustment.arrangementId(), adjustment.scoreDelta(), Double::sum);
        }
        totals.replaceAll((ignored, value) -> clamp(value, 0.20d));
        return new ValidatedPluginContributions(copy(constraints), Map.copyOf(totals), copy(adjustments), List.copyOf(errors));
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> Map<UUID, List<T>> copy(Map<UUID, List<T>> source) {
        Map<UUID, List<T>> copied = new LinkedHashMap<>();
        source.forEach((key, value) -> copied.put(key, List.copyOf(value)));
        return Map.copyOf(copied);
    }

    private static boolean bounded(double value, double maxAbs) {
        return Double.isFinite(value) && Math.abs(value) <= maxAbs;
    }

    private static double clamp(double value, double maxAbs) {
        return Math.max(-maxAbs, Math.min(maxAbs, value));
    }
}
