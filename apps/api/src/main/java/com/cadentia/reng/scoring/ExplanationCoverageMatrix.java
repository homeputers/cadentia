package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExplanationCoverageMatrix {

    private static final Map<String, CoverageRow> ROWS = List.of(
                    required(CandidateFeatureScorer.THEME_MATCH, ComponentType.SCORING, "THEME_MATCH"),
                    required(CandidateFeatureScorer.SCRIPTURE_MATCH, ComponentType.SCORING, "SCRIPTURE_MATCH"),
                    required(CandidateFeatureScorer.ROLE_FIT, ComponentType.SCORING, "ROLE_FIT"),
                    required(CandidateFeatureScorer.MUSICAL_FIT, ComponentType.SCORING, "SCORE_COMPONENT_MUSICAL_FIT"),
                    required(CandidateFeatureScorer.ENERGY_FIT, ComponentType.SCORING, "SCORE_COMPONENT_ENERGY_FIT"),
                    required(CandidateFeatureScorer.METADATA_CONFIDENCE, ComponentType.SCORING, "METADATA_LOW_CONFIDENCE"),
                    required(TransitionScorer.KEY_SAME, ComponentType.TRANSITION, "SAME_KEY_TRANSITION"),
                    required(TransitionScorer.KEY_RELATIVE, ComponentType.TRANSITION, "RELATIVE_KEY_TRANSITION"),
                    required(TransitionScorer.KEY_CLOSE, ComponentType.TRANSITION, "CLOSE_KEY_TRANSITION"),
                    required(TransitionScorer.KEY_MODULATION, ComponentType.TRANSITION, "MODULATION_PENALTY"),
                    required(TransitionScorer.BPM_JUMP, ComponentType.TRANSITION, "TEMPO_POLICY_OK"),
                    required(TransitionScorer.METER_MATCH, ComponentType.TRANSITION, "METER_COMPATIBLE"),
                    required(TransitionScorer.ENERGY_CONTINUITY, ComponentType.TRANSITION, "ENERGY_ARC_MATCH"))
            .stream()
            .collect(Collectors.toUnmodifiableMap(CoverageRow::componentId, Function.identity()));

    private ExplanationCoverageMatrix() {}

    public static Set<String> componentIds() {
        return ROWS.keySet();
    }

    public static List<CoverageRow> rows() {
        return ROWS.values().stream().toList();
    }

    private static CoverageRow required(String componentId, ComponentType type, String code) {
        return new CoverageRow(componentId, type, code, CoverageMode.REQUIRED, null);
    }

    private static CoverageRow intentionalOmission(String componentId, ComponentType type, String omissionReasonCode) {
        return new CoverageRow(componentId, type, null, CoverageMode.INTENTIONAL_OMISSION, omissionReasonCode);
    }

    public enum ComponentType {SCORING, FILTER, TRANSITION, QUOTA, POLICY_GUARD}
    public enum CoverageMode {REQUIRED, OPTIONAL, INTENTIONAL_OMISSION}

    public record CoverageRow(
            String componentId,
            ComponentType componentType,
            String explanationCode,
            CoverageMode coverageMode,
            String omissionReasonCode) {}
}
