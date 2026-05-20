package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HardConstraintFilterTest {

    private final HardConstraintFilter hardConstraintFilter = new HardConstraintFilter();

    @Test
    void filterExcludesUserExcludedSongIds() {
        RecommendableArrangement candidate = candidate();

        HardFilterResult result = hardConstraintFilter.filter(
                List.of(candidate),
                new ScoringRequest(
                        "Psalm 100",
                        List.of("praise"),
                        10,
                        5,
                        new ScoringRequest.KeyPolicy(true, true, 2),
                        new ScoringRequest.TempoPolicy(12),
                        "en",
                        List.of(candidate.songId().toString()),
                        new ScoringRequest.DefaultsApplied(false, false, false, false)));

        assertThat(result.eligibleCandidates()).isEmpty();
        assertThat(result.excludedCandidates())
                .singleElement()
                .extracting(HardFilterResult.ExcludedCandidate::reasonCodes)
                .asList()
                .contains(HardFilterReasonCode.EXCLUDED_BY_USER);
    }

    @Test
    void filterExcludesMissingProvenance() {
        RecommendableArrangement candidate = new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Song",
                "en",
                "G",
                KeyMode.MAJOR,
                120,
                "4/4",
                70,
                List.of(),
                approvedSummary());

        HardFilterResult result = hardConstraintFilter.filter(List.of(candidate), request());

        assertThat(result.excludedCandidates())
                .singleElement()
                .extracting(HardFilterResult.ExcludedCandidate::reasonCodes)
                .asList()
                .contains(HardFilterReasonCode.MISSING_PROVENANCE);
    }

    @Test
    void filterExcludesFailedApprovalGate() {
        ApprovalGateSummary failedSummary = new ApprovalGateSummary(
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.REJECTED,
                ApprovalStatus.APPROVED);
        RecommendableArrangement candidate = new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song",
                "en",
                "G",
                KeyMode.MAJOR,
                120,
                "4/4",
                70,
                List.of(),
                failedSummary);

        HardFilterResult result = hardConstraintFilter.filter(List.of(candidate), request());

        assertThat(result.excludedCandidates())
                .singleElement()
                .extracting(HardFilterResult.ExcludedCandidate::reasonCodes)
                .asList()
                .contains(HardFilterReasonCode.FAILED_APPROVAL_GATE);
    }

    @Test
    void filterExcludesLanguageMismatchAndMissingMusicalMetadata() {
        RecommendableArrangement candidate = new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song",
                "es",
                "",
                KeyMode.MAJOR,
                0,
                "4/4",
                70,
                List.of(),
                approvedSummary());

        HardFilterResult result = hardConstraintFilter.filter(List.of(candidate), request());

        assertThat(result.excludedCandidates())
                .singleElement()
                .extracting(HardFilterResult.ExcludedCandidate::reasonCodes)
                .asList()
                .contains(
                        HardFilterReasonCode.UNSUPPORTED_LANGUAGE,
                        HardFilterReasonCode.MISSING_KEY,
                        HardFilterReasonCode.MISSING_TEMPO);
    }

    @Test
    void filterKeepsEligibleCandidatesAndPropagatesCountRequirement() {
        RecommendableArrangement candidate = candidate();

        HardFilterResult result = hardConstraintFilter.filter(List.of(candidate), request());

        assertThat(result.eligibleCandidates()).containsExactly(candidate);
        assertThat(result.excludedCandidates()).isEmpty();
        assertThat(result.countRequirement().requiredPraise()).isEqualTo(10);
        assertThat(result.countRequirement().requiredWorship()).isEqualTo(5);
    }

    private static ScoringRequest request() {
        return new ScoringRequest(
                "Psalm 100",
                List.of("praise"),
                10,
                5,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                "en",
                List.of(),
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static RecommendableArrangement candidate() {
        return new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song",
                "en",
                "G",
                KeyMode.MAJOR,
                120,
                "4/4",
                70,
                List.of(),
                approvedSummary());
    }

    private static ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED);
    }
}
