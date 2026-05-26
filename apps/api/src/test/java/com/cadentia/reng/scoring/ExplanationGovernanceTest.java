package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

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
}
