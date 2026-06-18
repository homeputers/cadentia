package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic recommendation extension-point DTOs for ADR-030.
 *
 * <p>Constraint plugins may only describe bounded request-scoped effects for candidates that core
 * approval, licensing, instance, role, and read-model filters have already made visible. Hard rejects
 * remove a candidate from scoring; soft penalties, soft boosts, and informational signals are surfaced
 * to scoring/explanations. When multiple plugins target the same candidate, hard reject wins and soft
 * effects are summed then clamped to {@link #MAX_CONSTRAINT_DELTA}. Scoring adjustments are summed per
 * candidate, clamped to {@link #MAX_SCORING_DELTA}, and applied before Cadentia's deterministic
 * song/arrangement tie-breakers.
 */
public final class RecommendationPluginContributionModels {
    public static final double MAX_CONSTRAINT_DELTA = 0.10d;
    public static final double MAX_SCORING_DELTA = 0.20d;
    public static final String PLUGIN_COMPONENT_CODE = "plugin_policy_adjustment";

    private RecommendationPluginContributionModels() {
    }

    public enum ConstraintContributionType {
        HARD_REJECT,
        SOFT_PENALTY,
        SOFT_BOOST,
        INFORMATIONAL_SIGNAL
    }

    public record PluginContributionRequest(
            ScoringRequest request,
            ScoringProfile profile,
            String churchInstanceId,
            String policySnapshotId,
            List<RecommendableArrangement> approvedCandidates) {
        public PluginContributionRequest {
            approvedCandidates = approvedCandidates == null ? List.of() : List.copyOf(approvedCandidates);
        }

        public Set<UUID> approvedArrangementIds() {
            return approvedCandidates.stream().map(RecommendableArrangement::arrangementId).collect(java.util.stream.Collectors.toSet());
        }
    }

    public record PluginContributionMetadata(
            String pluginId,
            String pluginVersion,
            String configurationVersion) {
    }

    public record EvidenceSummary(String referenceType, String referenceId, String summary, boolean displayAllowed) {
    }

    public record ConstraintContribution(
            UUID arrangementId,
            ConstraintContributionType type,
            double scoreDelta,
            String reasonCode,
            PluginContributionMetadata metadata,
            List<EvidenceSummary> evidence) {
        public ConstraintContribution {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record ScoringAdjustment(
            UUID arrangementId,
            double scoreDelta,
            String componentCode,
            String reasonCode,
            PluginContributionMetadata metadata,
            List<EvidenceSummary> evidence) {
        public ScoringAdjustment {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record PluginContributionSet(
            List<ConstraintContribution> constraints,
            List<ScoringAdjustment> scoringAdjustments,
            List<String> safeErrors) {
        public PluginContributionSet {
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
            scoringAdjustments = scoringAdjustments == null ? List.of() : List.copyOf(scoringAdjustments);
            safeErrors = safeErrors == null ? List.of() : List.copyOf(safeErrors);
        }

        public static PluginContributionSet empty() {
            return new PluginContributionSet(List.of(), List.of(), List.of());
        }
    }

    public interface RecommendationConstraintPlugin {
        List<ConstraintContribution> constraints(PluginContributionRequest request);
    }

    public interface RecommendationScoringPlugin {
        List<ScoringAdjustment> scoreAdjustments(PluginContributionRequest request);
    }

    public record ValidatedPluginContributions(
            Map<UUID, List<ConstraintContribution>> constraintsByArrangement,
            Map<UUID, Double> scoringDeltaByArrangement,
            Map<UUID, List<ScoringAdjustment>> scoringAdjustmentsByArrangement,
            List<String> safeErrors) {
    }
}
