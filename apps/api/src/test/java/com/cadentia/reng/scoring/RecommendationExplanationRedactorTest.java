package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationExplanationRedactorTest {

    @Test
    void audienceModeChangesOnlyExplanationVisibilityNotSelectionOrderingOrScores() {
        // Arrange
        UUID arrangementId = UUID.randomUUID();
        UUID songId = UUID.randomUUID();
        OrderedSetItem item = new OrderedSetItem(
                arrangementId,
                songId,
                1,
                List.of(publicFact(), worshipLeaderFact()),
                List.of(new ScoringComponentScore("theme_match", 1.0d, 0.4d)),
                0.4d,
                null);
        RecommendationExplanationFact adminFact = adminExclusionFact();
        OrderedSetResponse response = new OrderedSetResponse(
                "profile-v1",
                "snapshot-v1",
                List.of(item),
                List.of(publicSetFact()),
                List.of(adminFact),
                List.of(),
                ScoringProfileLifecycle.active(),
                List.of("total_score", "arrangement_id"),
                0.4d);

        // Act
        OrderedSetResponse publicResponse = response.forAudience(DiagnosticsAudience.PUBLIC);
        OrderedSetResponse worshipLeaderResponse = response.forAudience(DiagnosticsAudience.WORSHIP_LEADER);
        OrderedSetResponse adminResponse = response.forAudience(DiagnosticsAudience.ADMIN);

        // Assert
        assertSelectionIsUnchanged(response, publicResponse);
        assertSelectionIsUnchanged(response, worshipLeaderResponse);
        assertSelectionIsUnchanged(response, adminResponse);
        assertThat(publicResponse.items().get(0).explanationFacts()).extracting(RecommendationExplanationFact::code)
                .containsExactly("ROLE_FIT");
        assertThat(worshipLeaderResponse.items().get(0).explanationFacts()).extracting(RecommendationExplanationFact::code)
                .containsExactly("ROLE_FIT", "SCORE_COMPONENT_MUSICAL_FIT");
        assertThat(publicResponse.adminCandidateExplanationFacts()).isEmpty();
        assertThat(worshipLeaderResponse.adminCandidateExplanationFacts()).isEmpty();
        assertThat(adminResponse.adminCandidateExplanationFacts()).containsExactly(adminFact);
        assertThat(publicResponse.items().get(0).candidateComponentScores()).isEmpty();
        assertThat(adminResponse.items().get(0).candidateComponentScores()).hasSize(1);
    }

    @Test
    void publicAndWorshipLeaderFactsRedactSensitiveProvenanceEvidence() {
        // Arrange
        RecommendationExplanationFact approvalFact = new RecommendationExplanationFact(
                "APPROVAL_ELIGIBLE",
                "info",
                "item",
                new RecommendationExplanationSubject("arrangement", "arr-1"),
                "item.approval_eligible",
                Map.of("hasProvenance", true),
                List.of(
                        new RecommendationExplanationEvidence("approval", "approval:private-review-42", "song_licensing_status", 1.0d),
                        new RecommendationExplanationEvidence("provenance", "lyrics_document:secret", "current_lyrics_document_id", 1.0d)),
                null);

        // Act
        RecommendationExplanationFact publicFact = RecommendationExplanationRedactor.redactFact(
                approvalFact,
                DiagnosticsAudience.PUBLIC);
        RecommendationExplanationFact adminFact = RecommendationExplanationRedactor.redactFact(
                approvalFact,
                DiagnosticsAudience.ADMIN);

        // Assert
        assertThat(publicFact.evidence()).extracting(RecommendationExplanationEvidence::type)
                .containsExactly("approval");
        assertThat(publicFact.evidence().get(0).ref()).isEqualTo("approval_gate_summary");
        assertThat(adminFact.evidence()).hasSize(2);
    }

    private static void assertSelectionIsUnchanged(OrderedSetResponse original, OrderedSetResponse filtered) {
        assertThat(filtered.items()).hasSize(original.items().size());
        assertThat(filtered.items().get(0).arrangementId()).isEqualTo(original.items().get(0).arrangementId());
        assertThat(filtered.items().get(0).songId()).isEqualTo(original.items().get(0).songId());
        assertThat(filtered.items().get(0).position()).isEqualTo(original.items().get(0).position());
        assertThat(filtered.items().get(0).candidateTotalScore()).isEqualTo(original.items().get(0).candidateTotalScore());
        assertThat(filtered.totalScore()).isEqualTo(original.totalScore());
    }

    private static RecommendationExplanationFact publicFact() {
        return new RecommendationExplanationFact(
                "ROLE_FIT",
                "info",
                "item",
                new RecommendationExplanationSubject("arrangement", "arr-1"),
                "item.role_fit",
                Map.of("score", 1.0d),
                List.of(new RecommendationExplanationEvidence("score", "component", "role_fit", 1.0d)),
                0.5d);
    }

    private static RecommendationExplanationFact worshipLeaderFact() {
        return new RecommendationExplanationFact(
                "SCORE_COMPONENT_MUSICAL_FIT",
                "info",
                "item",
                new RecommendationExplanationSubject("arrangement", "arr-1"),
                "item.score_component_musical_fit",
                Map.of("score", 0.9d),
                List.of(new RecommendationExplanationEvidence("score", "component", "musical_fit", 0.9d)),
                0.2d);
    }

    private static RecommendationExplanationFact publicSetFact() {
        return new RecommendationExplanationFact(
                "COUNT_TARGET_MET",
                "info",
                "set",
                new RecommendationExplanationSubject("set", "current"),
                "set.count_target",
                Map.of("selected", 1, "target", 1),
                List.of(new RecommendationExplanationEvidence("policy", "request", "counts", 1.0d)),
                null);
    }

    private static RecommendationExplanationFact adminExclusionFact() {
        return new RecommendationExplanationFact(
                "EXCLUDED_WEAKER_SCORE",
                "info",
                "candidate_exclusion",
                new RecommendationExplanationSubject("candidate", "candidate-1"),
                "candidate_exclusion.weaker_score",
                Map.of("candidateTitle", "Unapproved Internal Candidate", "candidateScore", 0.1d),
                List.of(new RecommendationExplanationEvidence("diagnostic", "candidate:candidate-1", "raw_connector_payload", 1.0d)),
                null);
    }
}
