package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateFeatureScorerTest {

    private final CandidateFeatureScorer scorer = new CandidateFeatureScorer();

    @Test
    void scoreCandidateDecomposesWeightedComponents() {
        ScoringProfile profile = scoringProfile();
        ScoringRequest request = request();
        RecommendableArrangement candidate = candidate("G", 120, "4/4", 80, List.of("praise"), List.of(
                new RecommendationTag(UUID.randomUUID(), TagType.THEME, "Holiness", "holiness"),
                new RecommendationTag(UUID.randomUUID(), TagType.SCRIPTURE, "Psalm 24", "psalm-24")));

        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(candidate, request, profile);

        assertThat(score.componentScores()).hasSize(7);
        assertThat(score.componentScores())
                .extracting(ScoringComponentScore::componentCode)
                .containsExactly(
                        CandidateFeatureScorer.THEME_MATCH,
                        CandidateFeatureScorer.SCRIPTURE_MATCH,
                        CandidateFeatureScorer.ROLE_FIT,
                        CandidateFeatureScorer.MUSICAL_FIT,
                        CandidateFeatureScorer.ENERGY_FIT,
                        CandidateFeatureScorer.METADATA_CONFIDENCE,
                        CandidateFeatureScorer.FEEDBACK_TUNING);
        assertThat(score.totalScore()).isGreaterThan(0.8d);
    }

    @Test
    void scoreCandidateAppliesPenaltyForMissingMusicalMetadata() {
        RecommendableArrangement candidate = candidate("", 0, "", 0, List.of(), List.of());

        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(candidate, request(), scoringProfile());

        assertThat(score.componentScores())
                .filteredOn(component -> component.componentCode().equals(CandidateFeatureScorer.MUSICAL_FIT))
                .singleElement()
                .extracting(ScoringComponentScore::rawScore)
                .isEqualTo(0.4d);
        assertThat(score.componentScores())
                .filteredOn(component -> component.componentCode().equals(CandidateFeatureScorer.METADATA_CONFIDENCE))
                .singleElement()
                .extracting(ScoringComponentScore::rawScore)
                .isEqualTo(0.0d);
    }

    @Test
    void scoreCandidatesReturnsDeterministicOrderForEquivalentScores() {
        RecommendableArrangement first = candidateWithIds(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        RecommendableArrangement second = candidateWithIds(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));

        List<CandidateFeatureScorer.CandidateFeatureScore> scores = scorer.scoreCandidates(
                List.of(second, first),
                request(),
                scoringProfile());

        assertThat(scores)
                .extracting(item -> item.candidate().arrangementId())
                .containsExactly(first.arrangementId(), second.arrangementId());
    }

    @Test
    void scoreCandidateAppliesFeedbackContributionWhenPresent() {
        RecommendableArrangement candidate = candidateWithIds(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000010"));

        CandidateFeatureScorer.CandidateFeatureScore score = scorer.scoreCandidate(
                candidate,
                request(),
                scoringProfile(),
                Map.of(candidate.arrangementId(), 0.75d));

        assertThat(score.componentScores())
                .filteredOn(component -> component.componentCode().equals(CandidateFeatureScorer.FEEDBACK_TUNING))
                .singleElement()
                .satisfies(component -> {
                    assertThat(component.rawScore()).isEqualTo(0.75d);
                    assertThat(component.weightedContribution()).isCloseTo(0.075d, org.assertj.core.data.Offset.offset(1.0e-9d));
                });
    }

    private static RecommendableArrangement candidateWithIds(UUID arrangementId, UUID songId) {
        return new RecommendableArrangement(
                arrangementId,
                songId,
                UUID.randomUUID(),
                "Song",
                "en",
                "G",
                KeyMode.MAJOR,
                120,
                "4/4",
                70,
                List.of("praise"),
                List.of(),
                List.of(),
                approvedSummary());
    }

    private static RecommendableArrangement candidate(
            String key,
            int bpm,
            String meter,
            int energy,
            List<String> tags,
            List<RecommendationTag> matchedTags) {
        return new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song",
                "en",
                key,
                KeyMode.MAJOR,
                bpm,
                meter,
                energy,
                tags,
                matchedTags,
                matchedTags,
                approvedSummary());
    }

    private static ScoringRequest request() {
        return new ScoringRequest(
                "Psalm 24",
                List.of("holiness"),
                10,
                5,
                new ScoringRequest.KeyPolicy(true, true, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile scoringProfile() {
        return new ScoringProfile(
                "v1",
                Map.of(
                        CandidateFeatureScorer.THEME_MATCH, 0.3,
                        CandidateFeatureScorer.SCRIPTURE_MATCH, 0.2,
                        CandidateFeatureScorer.ROLE_FIT, 0.2,
                        CandidateFeatureScorer.MUSICAL_FIT, 0.1,
                        CandidateFeatureScorer.ENERGY_FIT, 0.1,
                        CandidateFeatureScorer.METADATA_CONFIDENCE, 0.1,
                        CandidateFeatureScorer.FEEDBACK_TUNING, 0.1),
                List.of("total_score", "song_id", "arrangement_id"));
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
