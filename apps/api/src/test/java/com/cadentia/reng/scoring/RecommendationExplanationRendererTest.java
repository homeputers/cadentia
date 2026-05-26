package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecommendationExplanationRendererTest {

    private final RecommendationExplanationRenderer renderer = new RecommendationExplanationRenderer();

    @Test
    void shouldRenderItemTransitionSetWarningAndAdminExclusionFacts() {
        RecommendationExplanationFact itemFact = fact("THEME_MATCH", "item.theme_match", Map.of("theme", "holiness", "requestInput", "Psalm 24"));
        RecommendationExplanationFact transitionFact = fact(
                "RELATIVE_KEY_TRANSITION",
                "transition.relative_key",
                Map.of("fromKey", "G", "toKey", "Em"));
        RecommendationExplanationFact setFact = fact("COUNT_TARGET_MET", "set.count_target", Map.of("selected", 15, "target", 15));
        RecommendationExplanationFact warningFact = fact(
                "INSUFFICIENT_CANDIDATES",
                "warning.insufficient_candidates",
                Map.of("selected", 10, "target", 15, "availableCandidates", 12));
        RecommendationExplanationFact exclusionFact = fact(
                "FILLED_QUOTA",
                "candidate_exclusion.filled_quota",
                Map.of("candidateTitle", "Example Song"));

        List<ExplanationRenderResult> results = renderer.renderAll(List.of(itemFact, transitionFact, setFact, warningFact, exclusionFact));

        assertThat(results).hasSize(5);
        assertThat(results).allMatch(result -> !result.hasValidationErrors());
        assertThat(results.get(0).text()).isEqualTo("Theme holiness matched request input Psalm 24.");
        assertThat(results.get(1).text()).contains("Transition from G to Em");
        assertThat(results.get(2).text()).contains("target 15");
        assertThat(results.get(3).text()).contains("available candidates: 12");
        assertThat(results.get(4).text()).contains("Example Song");
    }

    @Test
    void shouldReportValidationErrorWhenTemplateValuesAreMissing() {
        RecommendationExplanationFact fact = fact("TEMPO_POLICY_OK", "transition.tempo_policy", Map.of("fromBpm", 96, "toBpm", 108));

        ExplanationRenderResult result = renderer.render(fact);

        assertThat(result.hasValidationErrors()).isTrue();
        assertThat(result.validationErrors()).contains("Missing template value: maxJumpBpm");
        assertThat(result.text()).contains("{maxJumpBpm}");
    }

    @Test
    void shouldReturnSafeFallbackForUnknownTemplateKey() {
        RecommendationExplanationFact fact = fact("ROLE_FIT", "unknown.template", Map.of());

        ExplanationRenderResult result = renderer.render(fact);

        assertThat(result.text()).isEqualTo("[ROLE_FIT]");
        assertThat(result.validationErrors()).contains("Unknown template key: unknown.template");
    }

    private static RecommendationExplanationFact fact(String code, String templateKey, Map<String, Object> values) {
        String scope = switch (code) {
            case "RELATIVE_KEY_TRANSITION", "TEMPO_POLICY_OK" -> "transition";
            case "COUNT_TARGET_MET", "INSUFFICIENT_CANDIDATES" -> "set";
            case "FILLED_QUOTA", "WEAKER_SCORE" -> "candidate_exclusion";
            default -> "item";
        };
        String severity = switch (code) {
            case "INSUFFICIENT_CANDIDATES" -> "warning";
            default -> "info";
        };
        return new RecommendationExplanationFact(
                code,
                severity,
                scope,
                new RecommendationExplanationSubject("arrangement", "subject-id"),
                templateKey,
                values,
                List.of(new RecommendationExplanationEvidence("score", "component", "field", 1.0d)),
                null);
    }
}
