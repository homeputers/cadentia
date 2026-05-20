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

    private static CandidateFeatureScorer.CandidateFeatureScore score(RecommendableArrangement candidate, double total) {
        return new CandidateFeatureScorer.CandidateFeatureScore(
                candidate,
                List.of(new ScoringComponentScore("theme_match", total, total)),
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
                "en",
                List.of(),
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile profile() {
        return new ScoringProfile(
                "v1",
                Map.of(TransitionScorer.BPM_JUMP, 0.2d, TransitionScorer.KEY_SAME, 0.3d),
                List.of("total_score", "song_id", "arrangement_id"));
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
