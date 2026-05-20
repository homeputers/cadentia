package com.cadentia.reng.scoring;

import java.util.List;

public record OrderedSetResponse(
        String scoringProfileVersion,
        String candidateSnapshotVersion,
        List<OrderedSetItem> items,
        List<String> deterministicOrderingRules,
        double totalScore) {

    public OrderedSetResponse {
        items = items == null ? List.of() : List.copyOf(items);
        deterministicOrderingRules = deterministicOrderingRules == null ? List.of() : List.copyOf(deterministicOrderingRules);
    }

    public static OrderedSetResponse of(
            ScoringProfile profile,
            String candidateSnapshotVersion,
            List<OrderedSetItem> items,
            double totalScore) {
        return new OrderedSetResponse(
                profile.version(),
                candidateSnapshotVersion,
                items,
                profile.deterministicTieBreakOrder(),
                totalScore);
    }
}
