package com.cadentia.reng.scoring;

import static com.cadentia.catalog.model.ApprovalStatus.APPROVED;
import static com.cadentia.catalog.model.ApprovalStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetAwareRecommendationSafetyTest {

    @Test
    void assetPresenceDoesNotReplaceCatalogApprovalEligibility() {
        // Arrange
        RecommendableArrangement approvedWithAssets = arrangement("Approved with assets", approved());
        RecommendableArrangement nonApprovedWithAssets = arrangement("Pending with assets", pending());
        RecommendableArrangement approvedWithoutAssets = arrangement("Approved without assets", approved());
        ScoringRequest request = request();

        // Act
        HardFilterResult filtered = new HardConstraintFilter().filter(
                List.of(approvedWithAssets, nonApprovedWithAssets, approvedWithoutAssets), request);
        List<CandidateFeatureScorer.CandidateFeatureScore> scored = new CandidateFeatureScorer().scoreCandidates(
                filtered.eligibleCandidates(),
                request,
                new ScoringProfile("asset-aware", Map.of(CandidateFeatureScorer.ASSET_AVAILABILITY, 0.25d), List.of()),
                Map.of(),
                Map.of(approvedWithAssets.arrangementId(), true, nonApprovedWithAssets.arrangementId(), true));

        // Assert
        assertThat(filtered.eligibleCandidates())
                .extracting(RecommendableArrangement::title)
                .containsExactly("Approved with assets", "Approved without assets")
                .doesNotContain("Pending with assets");
        assertThat(filtered.excludedCandidates()).singleElement().satisfies(excluded ->
                assertThat(excluded.candidate().title()).isEqualTo("Pending with assets"));
        assertThat(scored).extracting(score -> score.candidate().title())
                .containsExactly("Approved with assets", "Approved without assets");
    }

    @Test
    void explanationsPreserveApprovalAndDatasetReferencesRatherThanAssetEvidence() {
        // Arrange
        RecommendableArrangement candidate = arrangement("Approved without assets", approved());
        ScoringRequest request = request();
        var score = new CandidateFeatureScorer().scoreCandidate(
                candidate,
                request,
                new ScoringProfile("asset-aware", Map.of(CandidateFeatureScorer.ASSET_AVAILABILITY, 0.25d), List.of()),
                Map.of(),
                Map.of(candidate.arrangementId(), false));

        // Act
        RecommendationSongExplanation explanation = new ItemExplanationFactory()
                .buildSongExplanation(candidate, request, score.componentScores(), 1);

        // Assert
        assertThat(explanation.approvalEvidence())
                .extracting(RecommendationExplanationEvidence::type)
                .containsOnly("approval");
        assertThat(explanation.provenanceEvidence())
                .extracting(RecommendationExplanationEvidence::type)
                .containsOnly("provenance");
        assertThat(explanation.facts()).flatExtracting(RecommendationExplanationFact::evidence)
                .extracting(RecommendationExplanationEvidence::type)
                .doesNotContain("asset");
    }

    private static ScoringRequest request() {
        return new ScoringRequest(
                "Psalm 100",
                List.of("praise"),
                1,
                1,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static RecommendableArrangement arrangement(String title, ApprovalGateSummary approval) {
        return new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                "en",
                "G",
                KeyMode.MAJOR,
                100,
                "4/4",
                70,
                List.of("praise"),
                List.of(),
                List.of(),
                approval);
    }

    private static ApprovalGateSummary approved() {
        return new ApprovalGateSummary(APPROVED, APPROVED, APPROVED, APPROVED, APPROVED, APPROVED, APPROVED, APPROVED);
    }

    private static ApprovalGateSummary pending() {
        return new ApprovalGateSummary(APPROVED, PENDING, APPROVED, APPROVED, APPROVED, APPROVED, APPROVED, APPROVED);
    }
}
