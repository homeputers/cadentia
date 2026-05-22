package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ScoringDiagnostics(
        boolean enabled,
        int retrievedCandidateCount,
        int eligibleCandidateCount,
        int excludedCandidateCount,
        Map<HardFilterReasonCode, Integer> exclusionReasonCounts,
        ScoreRange candidateScoreRange,
        ScoreRange transitionScoreRange,
        List<ConstraintRelaxationStep> constraintRelaxationSequence,
        List<SearchPruningDecision> searchPruningDecisions,
        List<String> transitionTradeoffCodes,
        SelectedSetSummary selectedSetSummary) {

    public ScoringDiagnostics {
        exclusionReasonCounts = exclusionReasonCounts == null ? Map.of() : Map.copyOf(exclusionReasonCounts);
        constraintRelaxationSequence = constraintRelaxationSequence == null ? List.of() : List.copyOf(constraintRelaxationSequence);
        searchPruningDecisions = searchPruningDecisions == null ? List.of() : List.copyOf(searchPruningDecisions);
        transitionTradeoffCodes = transitionTradeoffCodes == null ? List.of() : List.copyOf(transitionTradeoffCodes);
    }

    public static ScoringDiagnostics disabled() {
        return new ScoringDiagnostics(false, 0, 0, 0, Map.of(), ScoreRange.empty(), ScoreRange.empty(), List.of(), List.of(), List.of(),
                SelectedSetSummary.empty());
    }

    public ScoringDiagnostics forAudience(DiagnosticsAudience audience) {
        if (!enabled || audience == DiagnosticsAudience.ADMIN) {
            return this;
        }
        return new ScoringDiagnostics(
                true,
                retrievedCandidateCount,
                eligibleCandidateCount,
                excludedCandidateCount,
                Map.of(),
                candidateScoreRange,
                transitionScoreRange,
                List.of(),
                List.of(),
                transitionTradeoffCodes,
                selectedSetSummary);
    }

    public static ScoringDiagnostics from(
            List<RecommendableArrangement> retrieved,
            HardFilterResult hardFilterResult,
            List<CandidateFeatureScorer.CandidateFeatureScore> candidateScores,
            List<TransitionScore> transitionScores,
            OrderedSetResponse selectedSet,
            boolean enabled) {
        if (!enabled) {
            return disabled();
        }
        Map<HardFilterReasonCode, Integer> reasonCounts = new EnumMap<>(HardFilterReasonCode.class);
        for (HardFilterResult.ExcludedCandidate excluded : hardFilterResult.excludedCandidates()) {
            for (HardFilterReasonCode reason : excluded.reasonCodes()) {
                reasonCounts.merge(reason, 1, Integer::sum);
            }
        }
        List<String> tradeoffs = transitionScores.stream()
                .flatMap(score -> score.components().stream())
                .filter(component -> component.weightedContribution() < 0)
                .map(ScoringComponentScore::componentCode)
                .distinct()
                .sorted()
                .toList();
        return new ScoringDiagnostics(
                true,
                retrieved.size(),
                hardFilterResult.eligibleCandidates().size(),
                hardFilterResult.excludedCandidates().size(),
                reasonCounts,
                ScoreRange.from(candidateScores.stream().mapToDouble(CandidateFeatureScorer.CandidateFeatureScore::totalScore)
                        .toArray()),
                ScoreRange.from(transitionScores.stream().mapToDouble(TransitionScore::totalScore).toArray()),
                List.of(),
                hardFilterResult.excludedCandidates().stream()
                        .map(excluded -> new SearchPruningDecision(
                                excluded.candidate().arrangementId().toString(),
                                excluded.reasonCodes().isEmpty() ? "UNKNOWN" : excluded.reasonCodes().get(0).name(),
                                "hard_constraint_filter"))
                        .toList(),
                tradeoffs,
                SelectedSetSummary.from(selectedSet));
    }

    public record ScoreRange(double min, double max, double average) {
        public static ScoreRange empty() {return new ScoreRange(0d, 0d, 0d);} 
        private static ScoreRange from(double[] values) {
            if (values.length == 0) {
                return empty();
            }
            double min = values[0];
            double max = values[0];
            double sum = 0;
            for (double value : values) {
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
            }
            return new ScoreRange(min, max, sum / values.length);
        }
    }

    public record SelectedSetSummary(int itemCount, double totalScore) {
        public static SelectedSetSummary empty() {return new SelectedSetSummary(0, 0d);} 
        private static SelectedSetSummary from(OrderedSetResponse response) {
            return new SelectedSetSummary(response.items().size(), response.totalScore());
        }
    }
}
