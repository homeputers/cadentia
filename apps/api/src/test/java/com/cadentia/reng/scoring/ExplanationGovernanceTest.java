package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExplanationGovernanceTest {

    @Test
    void coverageMatrixIncludesAllDeterministicScoringAndTransitionComponents() {
        Set<String> expectedComponents = Set.of(
                CandidateFeatureScorer.THEME_MATCH,
                CandidateFeatureScorer.SCRIPTURE_MATCH,
                CandidateFeatureScorer.ROLE_FIT,
                CandidateFeatureScorer.MUSICAL_FIT,
                CandidateFeatureScorer.ENERGY_FIT,
                CandidateFeatureScorer.METADATA_CONFIDENCE,
                TransitionScorer.KEY_SAME,
                TransitionScorer.KEY_RELATIVE,
                TransitionScorer.KEY_CLOSE,
                TransitionScorer.KEY_MODULATION,
                TransitionScorer.BPM_JUMP,
                TransitionScorer.METER_MATCH,
                TransitionScorer.ENERGY_CONTINUITY);

        assertThat(ExplanationCoverageMatrix.componentIds()).containsExactlyInAnyOrderElementsOf(expectedComponents);
    }

    @Test
    void requiredCoverageRowsReferenceActiveExplanationCodes() {
        Set<String> activeCodes = ExplanationCodeRegistry.activeCodes();
        assertThat(ExplanationCoverageMatrix.rows())
                .filteredOn(row -> row.coverageMode() == ExplanationCoverageMatrix.CoverageMode.REQUIRED)
                .allSatisfy(row -> assertThat(activeCodes).contains(row.explanationCode()));
    }

    @Test
    void intentionalOmissionsIncludeReasonCodes() {
        assertThat(ExplanationCoverageMatrix.rows())
                .filteredOn(row -> row.coverageMode() == ExplanationCoverageMatrix.CoverageMode.INTENTIONAL_OMISSION)
                .allSatisfy(row -> assertThat(row.omissionReasonCode()).isNotBlank());
    }

    @Test
    void activeRegistryEntriesExposeUiLocalizationAndAudienceMetadata() {
        assertThat(ExplanationCodeRegistry.activeEntries())
                .allSatisfy(entry -> {
                    assertThat(entry.displayGroup()).isNotBlank();
                    assertThat(entry.localizationKey()).isNotBlank();
                    assertThat(entry.allowedSeverities()).isNotEmpty();
                    assertThat(entry.audiences()).isNotEmpty();
                    assertThat(entry.introducedInVersion()).isEqualTo("v1");
                    assertThat(entry.stableForClients()).isTrue();
                });
    }

    @Test
    void backendFactsCannotUseUnregisteredValueKeysOrLocalizationKeys() {
        assertThatThrownBy(() -> new RecommendationExplanationFact(
                        "ROLE_FIT",
                        "info",
                        "item",
                        new RecommendationExplanationSubject("arrangement", "arr-1"),
                        "item.theme_match",
                        Map.of("theme", "holiness"),
                        List.of(new RecommendationExplanationEvidence("score", "candidate.role_fit", "raw", null)),
                        0.5d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Localization key item.theme_match is not registered for code ROLE_FIT");
    }
}
