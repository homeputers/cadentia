package com.cadentia.reng.scoring;

import java.util.List;

public record OrderedSetResponse(
        String scoringProfileVersion,
        String candidateSnapshotVersion,
        List<OrderedSetItem> items,
        List<RecommendationExplanationFact> setExplanationFacts,
        List<RecommendationExplanationFact> adminCandidateExplanationFacts,
        List<TransitionExplanationEntry> adjacentTransitionExplanations,
        ScoringProfileLifecycle profileLifecycle,
        List<String> deterministicOrderingRules,
        double totalScore) {

    public OrderedSetResponse {
        items = items == null ? List.of() : List.copyOf(items);
        setExplanationFacts = setExplanationFacts == null ? List.of() : List.copyOf(setExplanationFacts);
        adminCandidateExplanationFacts = adminCandidateExplanationFacts == null ? List.of() : List.copyOf(adminCandidateExplanationFacts);
        adjacentTransitionExplanations = adjacentTransitionExplanations == null ? List.of() : List.copyOf(adjacentTransitionExplanations);
        profileLifecycle = profileLifecycle == null ? ScoringProfileLifecycle.active() : profileLifecycle;
        deterministicOrderingRules = deterministicOrderingRules == null ? List.of() : List.copyOf(deterministicOrderingRules);
    }

    public static OrderedSetResponse of(
            ScoringProfile profile,
            String candidateSnapshotVersion,
            List<OrderedSetItem> items,
            List<RecommendationExplanationFact> setExplanationFacts,
            double totalScore) {
        return new OrderedSetResponse(
                profile.version(),
                candidateSnapshotVersion,
                items,
                setExplanationFacts,
                List.of(),
                List.of(),
                profile.lifecycle(),
                profile.deterministicTieBreakOrder(),
                totalScore);
    }

    public static OrderedSetResponse of(
            ScoringProfile profile,
            String candidateSnapshotVersion,
            List<OrderedSetItem> items,
            double totalScore) {
        return of(profile, candidateSnapshotVersion, items, List.of(), totalScore);
    }

    public OrderedSetResponse asPublicResponse() {
        return forAudience(DiagnosticsAudience.PUBLIC);
    }

    public OrderedSetResponse forAudience(DiagnosticsAudience audience) {
        DiagnosticsAudience effectiveAudience = audience == null ? DiagnosticsAudience.PUBLIC : audience;
        return new OrderedSetResponse(
                scoringProfileVersion,
                candidateSnapshotVersion,
                items.stream().map(item -> item.forAudience(effectiveAudience)).toList(),
                RecommendationExplanationRedactor.filterFacts(setExplanationFacts, effectiveAudience),
                effectiveAudience == DiagnosticsAudience.ADMIN
                        ? RecommendationExplanationRedactor.filterFacts(adminCandidateExplanationFacts, effectiveAudience)
                        : List.of(),
                adjacentTransitionExplanations.stream()
                        .map(entry -> entry.forAudience(effectiveAudience))
                        .toList(),
                profileLifecycle,
                deterministicOrderingRules,
                totalScore);
    }
}
