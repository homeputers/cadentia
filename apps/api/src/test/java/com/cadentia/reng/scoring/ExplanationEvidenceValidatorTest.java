package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplanationEvidenceValidatorTest {

    @Test
    void shouldFailWhenEvidenceReferenceDoesNotResolve() {
        RecommendationExplanationFact fact = fact(List.of(new RecommendationExplanationEvidence("catalog", "missing_ref", "theme", 1.0d)));

        assertThatThrownBy(() -> ExplanationEvidenceValidator.validate(fact, Map.of("catalog", Set.of("matched_tags"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unresolvable explanation evidence reference");
    }

    @Test
    void shouldPassWhenAllEvidenceReferencesResolve() {
        RecommendationExplanationFact fact = fact(List.of(
                new RecommendationExplanationEvidence("catalog", "matched_tags", "theme", 1.0d),
                new RecommendationExplanationEvidence("request", "verse_text", null, 1.0d)));

        assertThatCode(() -> ExplanationEvidenceValidator.validate(
                        fact,
                        Map.of("catalog", Set.of("matched_tags"), "request", Set.of("verse_text"))))
                .doesNotThrowAnyException();
    }

    private static RecommendationExplanationFact fact(List<RecommendationExplanationEvidence> evidence) {
        return new RecommendationExplanationFact(
                "THEME_MATCH",
                "info",
                "item",
                new RecommendationExplanationSubject("arrangement", "arr-1"),
                "item.theme_match",
                Map.of("theme", "Holiness", "requestInput", "Psalm 24"),
                evidence,
                0.3d);
    }
}
