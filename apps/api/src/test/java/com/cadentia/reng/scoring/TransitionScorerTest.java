package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransitionScorerTest {

    private final TransitionScorer scorer = new TransitionScorer();

    @Test
    void scoreMarksRelativeMajorMinorTransitionWhenAllowed() {
        TransitionScore score = scorer.score(candidate("C", KeyMode.MAJOR, 120), candidate("A", KeyMode.MINOR, 125), request(true), profile());

        assertThat(score.components())
                .filteredOn(component -> component.componentCode().equals(TransitionScorer.KEY_RELATIVE))
                .singleElement()
                .extracting(ScoringComponentScore::rawScore)
                .isEqualTo(1.0d);
    }

    @Test
    void scorePenalizesExcessiveBpmJump() {
        TransitionScore score = scorer.score(candidate("G", KeyMode.MAJOR, 100), candidate("G", KeyMode.MAJOR, 135), request(true), profile());

        assertThat(score.components())
                .filteredOn(component -> component.componentCode().equals(TransitionScorer.BPM_JUMP))
                .singleElement()
                .extracting(ScoringComponentScore::rawScore)
                .satisfies(raw -> assertThat((Double) raw).isLessThan(0.1d));
    }

    private static ScoringRequest request(boolean allowRelative) {
        return new ScoringRequest(
                null,
                List.of(),
                2,
                1,
                new ScoringRequest.KeyPolicy(true, allowRelative, 2),
                new ScoringRequest.TempoPolicy(12),
                null,
                "en",
                List.of(),
                false,
                new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    private static ScoringProfile profile() {
        return new ScoringProfile(
                "v1",
                Map.of(
                        TransitionScorer.KEY_RELATIVE, 1.0d,
                        TransitionScorer.BPM_JUMP, 1.0d),
                List.of("total_score", "song_id", "arrangement_id"));
    }

    private static RecommendableArrangement candidate(String key, KeyMode keyMode, int bpm) {
        return new RecommendableArrangement(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Song",
                "en",
                key,
                keyMode,
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
