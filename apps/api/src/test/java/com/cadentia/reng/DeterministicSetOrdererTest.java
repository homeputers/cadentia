package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.OrderedSetResponse;
import com.cadentia.reng.scoring.ScoringComponentScore;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringRequest;
import com.cadentia.reng.scoring.TransitionScorer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicSetOrdererTest {

    private final DeterministicSetOrderer orderer = new DeterministicSetOrderer(new TransitionScorer());

    @Test
    void orderIsDeterministicForIdenticalInputs() {
        CandidateFeatureScorer.CandidateFeatureScore a = score(candidate("A", "C", 120), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore b = score(candidate("B", "C", 119), 0.9d);

        OrderedSetResponse first = orderer.order(List.of(b, a), request(3), profile(), "snap");
        OrderedSetResponse second = orderer.order(List.of(b, a), request(3), profile(), "snap");

        assertThat(first.items()).extracting(item -> item.arrangementId())
                .containsExactlyElementsOf(second.items().stream().map(item -> item.arrangementId()).toList());
    }

    @Test
    void orderRespectsKeyCenterLimitWhenEnoughCandidatesExist() {
        CandidateFeatureScorer.CandidateFeatureScore cKey = score(candidate("C", "C", 120), 1.0d);
        CandidateFeatureScorer.CandidateFeatureScore gKey = score(candidate("G", "G", 122), 0.95d);
        CandidateFeatureScorer.CandidateFeatureScore dKey = score(candidate("D", "D", 121), 0.99d);

        OrderedSetResponse ordered = orderer.order(List.of(cKey, dKey, gKey), request(2), profile(), "snap");

        assertThat(ordered.items()).hasSize(2);
        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(cKey.candidate().arrangementId(), dKey.candidate().arrangementId());
    }

    @Test
    void tieBreakingUsesNormalizedTitleThenStableIdentifiers() {
        CandidateFeatureScorer.CandidateFeatureScore zeta = score(candidate("zeta", "C", 120), 0.9d);
        CandidateFeatureScorer.CandidateFeatureScore alpha = score(candidate("Alpha", "C", 120), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(zeta, alpha), request(3), profile(), "snap");

        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(alpha.candidate().arrangementId(), zeta.candidate().arrangementId());
    }

    @Test
    void nearTieWithinEpsilonUsesTieBreakersInsteadOfRawTotalScore() {
        CandidateFeatureScorer.CandidateFeatureScore alphaLower = score(candidate("Alpha", "C", 120), 0.90000d);
        CandidateFeatureScorer.CandidateFeatureScore zetaHigher = score(candidate("zeta", "C", 120), 0.90005d);

        OrderedSetResponse ordered = orderer.order(List.of(zetaHigher, alphaLower), request(3), profile(), "snap");

        assertThat(ordered.items()).extracting(item -> item.arrangementId())
                .containsExactly(alphaLower.candidate().arrangementId(), zetaHigher.candidate().arrangementId());
    }

    @Test
    void emitsRoleFitAndEligibilityExplanationsForSelectedItems() {
        CandidateFeatureScorer.CandidateFeatureScore candidate = score(candidate("A", "C", 120), 0.9d);

        OrderedSetResponse ordered = orderer.order(List.of(candidate), request(2), profile(), "snap");

        assertThat(ordered.items()).hasSize(1);
        assertThat(ordered.items().get(0).explanationFacts())
                .extracting(fact -> fact.code())
                .contains("ROLE_FIT", "APPROVAL_ELIGIBLE");
    }

    private static CandidateFeatureScorer.CandidateFeatureScore score(RecommendableArrangement candidate, double total) {
        return new CandidateFeatureScorer.CandidateFeatureScore(
                candidate,
                List.of(
                        new ScoringComponentScore("theme_match", total, total),
                        new ScoringComponentScore("role_fit", 1.0d, 0.2d),
                        new ScoringComponentScore("metadata_confidence", 1.0d, 0.1d)),
                total);
    }

    private static ScoringRequest request(int maxKeyCenters) {
        return new ScoringRequest(
                null,
                List.of(),
                2,
                0,
                new ScoringRequest.KeyPolicy(true, true, maxKeyCenters),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile profile() {
        return new ScoringProfile(
                "v1",
                Map.of(TransitionScorer.BPM_JUMP, 0.2d, TransitionScorer.KEY_SAME, 0.3d),
                List.of("total_score(desc,epsilon=0.0001)", "approval_confidence(desc)", "normalized_title(asc)", "song_id(asc)", "arrangement_id(asc)"));
    }

    private static RecommendableArrangement candidate(String titleSuffix, String key, int bpm) {
        return new RecommendableArrangement(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + (titleSuffix.equals("A") ? "1" : titleSuffix.equals("B") ? "2" : "3")),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song " + titleSuffix,
                "en",
                key,
                KeyMode.MAJOR,
                bpm,
                "4/4",
                70,
                List.of("praise"),
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
                        ApprovalStatus.APPROVED));
    }
}
