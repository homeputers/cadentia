package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.transposition.MusicalKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeyPolicyEvaluatorTest {

    private final KeyPolicyEvaluator evaluator = new KeyPolicyEvaluator();

    @Test
    void preferSameKeyScoresStoredArrangementKeyAboveDynamicTransposition() {
        // Arrange
        RecommendableArrangement candidate = candidate("G", KeyMode.MAJOR);
        RecommendationKeyPolicy policy = new RecommendationKeyPolicy(true, true, 2, true);

        // Act
        List<KeyPolicyEvaluation> evaluations = evaluator.evaluate(
                candidate,
                policy,
                List.of(new MusicalKey("G", KeyMode.MAJOR), new MusicalKey("D", KeyMode.MAJOR)));

        // Assert
        assertThat(evaluations).isNotEmpty();
        assertThat(evaluations.getFirst()).satisfies(evaluation -> {
            assertThat(evaluation.targetKey()).isEqualTo(new MusicalKey("G", KeyMode.MAJOR));
            assertThat(evaluation.transpositionType()).isEqualTo(TranspositionType.STORED_ARRANGEMENT_KEY);
            assertThat(evaluation.explanation()).contains("matches an active key center");
        });
        assertThat(evaluations)
                .anySatisfy(evaluation -> {
                    assertThat(evaluation.targetKey()).isEqualTo(new MusicalKey("D", KeyMode.MAJOR));
                    assertThat(evaluation.transpositionType()).isEqualTo(TranspositionType.DYNAMIC_TRANSPOSITION);
                    assertThat(evaluation.explanation())
                            .contains("dynamic transposition")
                            .contains("stored arrangement key G major");
                });
    }

    @Test
    void relativeMajorMinorAllowanceKeepsCompatibleStoredKeyWithinExistingKeyCenter() {
        // Arrange
        RecommendableArrangement candidate = candidate("A", KeyMode.MINOR);
        RecommendationKeyPolicy policy = new RecommendationKeyPolicy(true, true, 1, false);

        // Act
        List<KeyPolicyEvaluation> evaluations = evaluator.evaluate(
                candidate,
                policy,
                List.of(new MusicalKey("C", KeyMode.MAJOR)));

        // Assert
        assertThat(evaluations).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.targetKey()).isEqualTo(new MusicalKey("A", KeyMode.MINOR));
            assertThat(evaluation.transpositionType()).isEqualTo(TranspositionType.STORED_ARRANGEMENT_KEY);
            assertThat(evaluation.score()).isEqualTo(85);
            assertThat(evaluation.explanation()).contains("relative major/minor compatible");
        });
    }

    @Test
    void maxKeyCentersFiltersStoredKeyThatWouldAddDisallowedCenterButKeepsDynamicTarget() {
        // Arrange
        RecommendableArrangement candidate = candidate("E", KeyMode.MAJOR);
        RecommendationKeyPolicy policy = new RecommendationKeyPolicy(true, true, 1, true);

        // Act
        List<KeyPolicyEvaluation> evaluations = evaluator.evaluate(
                candidate,
                policy,
                List.of(new MusicalKey("C", KeyMode.MAJOR)));

        // Assert
        assertThat(evaluations).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.baseKey()).isEqualTo(new MusicalKey("E", KeyMode.MAJOR));
            assertThat(evaluation.targetKey()).isEqualTo(new MusicalKey("C", KeyMode.MAJOR));
            assertThat(evaluation.transpositionType()).isEqualTo(TranspositionType.DYNAMIC_TRANSPOSITION);
            assertThat(evaluation.explanation())
                    .contains("Target key C major")
                    .contains("dynamic transposition from stored arrangement key E major");
        });
    }

    @Test
    void disallowedTranspositionReturnsNoEvaluationWhenMaxKeyCentersIsAlreadyReached() {
        // Arrange
        RecommendableArrangement candidate = candidate("E", KeyMode.MAJOR);
        RecommendationKeyPolicy policy = new RecommendationKeyPolicy(true, true, 1, false);

        // Act
        List<KeyPolicyEvaluation> evaluations = evaluator.evaluate(
                candidate,
                policy,
                List.of(new MusicalKey("C", KeyMode.MAJOR)));

        // Assert
        assertThat(evaluations).isEmpty();
    }

    private static RecommendableArrangement candidate(String key, KeyMode mode) {
        return new RecommendableArrangement(
                UUID.nameUUIDFromBytes((key + mode).getBytes()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Fixture Song",
                "en",
                key,
                mode,
                96,
                "4/4",
                3,
                List.of("thanksgiving"),
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
