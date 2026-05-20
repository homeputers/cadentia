package com.cadentia.reng.scoring;

import com.cadentia.reng.RecommendableArrangement;
import java.util.List;

public record HardFilterResult(
        List<RecommendableArrangement> eligibleCandidates,
        List<ExcludedCandidate> excludedCandidates,
        CountRequirement countRequirement) {

    public HardFilterResult {
        eligibleCandidates = eligibleCandidates == null ? List.of() : List.copyOf(eligibleCandidates);
        excludedCandidates = excludedCandidates == null ? List.of() : List.copyOf(excludedCandidates);
    }

    public record ExcludedCandidate(RecommendableArrangement candidate, List<HardFilterReasonCode> reasonCodes) {
        public ExcludedCandidate {
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        }
    }

    public record CountRequirement(int requiredPraise, int requiredWorship) {}
}
