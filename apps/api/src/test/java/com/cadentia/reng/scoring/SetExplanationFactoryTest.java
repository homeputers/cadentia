package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.SongRole;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SetExplanationFactoryTest {

    @Test
    void countTargetFactUsesExplicitSongRolesWhenPresent() {
        // Arrange
        List<CandidateFeatureScorer.CandidateFeatureScore> selected = List.of(
                score(candidate(SongRole.PRAISE, List.of())),
                score(candidate(SongRole.WORSHIP, List.of())),
                score(candidate(SongRole.BOTH, List.of())));

        // Act
        RecommendationExplanationFact countFact = countTargetFact(new SetExplanationFactory()
                .build(request(), selected, selected, List.of()));

        // Assert
        assertThat(countFact.values())
                .containsEntry("selectedPraise", 2)
                .containsEntry("selectedWorship", 2)
                .containsEntry("requestedPraise", 10)
                .containsEntry("requestedWorship", 5);
    }

    @Test
    void explicitSongRoleOverridesTagClassificationInRoleCounts() {
        // Arrange
        List<CandidateFeatureScorer.CandidateFeatureScore> selected = List.of(
                score(candidate(SongRole.WORSHIP, List.of("praise", "worship"))));

        // Act
        RecommendationExplanationFact countFact = countTargetFact(new SetExplanationFactory()
                .build(request(), selected, selected, List.of()));

        // Assert
        assertThat(countFact.values())
                .containsEntry("selectedPraise", 0)
                .containsEntry("selectedWorship", 1);
    }

    @Test
    void countTargetFactFallsBackToTagSlugsWithoutExplicitRole() {
        // Arrange
        List<CandidateFeatureScorer.CandidateFeatureScore> selected = List.of(
                score(candidate(null, List.of("praise"))),
                score(candidate(null, List.of())));

        // Act
        RecommendationExplanationFact countFact = countTargetFact(new SetExplanationFactory()
                .build(request(), selected, selected, List.of()));

        // Assert
        assertThat(countFact.values())
                .containsEntry("selectedPraise", 1)
                .containsEntry("selectedWorship", 0);
    }

    private static RecommendationExplanationFact countTargetFact(List<RecommendationExplanationFact> facts) {
        return facts.stream()
                .filter(fact -> fact.code().equals("COUNT_TARGET_MET"))
                .findFirst()
                .orElseThrow();
    }

    private static ScoringRequest request() {
        return new ScoringRequest(
                null,
                List.of(),
                List.of(),
                10,
                5,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                null,
                null,
                null);
    }

    private static CandidateFeatureScorer.CandidateFeatureScore score(RecommendableArrangement candidate) {
        return new CandidateFeatureScorer.CandidateFeatureScore(candidate, List.of(), 1.0d);
    }

    private static RecommendableArrangement candidate(SongRole songRole, List<String> tags) {
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
                80,
                tags,
                List.of(),
                List.of(),
                new ApprovalGateSummary(
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED),
                null,
                songRole);
    }
}
