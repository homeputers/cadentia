package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies backend-owned audience partitioning before recommendation explanations leave REng.
 * The redactor never influences candidate retrieval, scoring, selection, or ordering.
 */
public final class RecommendationExplanationRedactor {

    private static final Set<String> ADMIN_ONLY_EVIDENCE_TYPES = Set.of("diagnostic", "provenance");
    private static final Set<String> ADMIN_ONLY_VALUE_KEYS = Set.of(
            "candidateTitle",
            "candidateScore",
            "gate",
            "duplicateOfArrangementId",
            "candidateKey");

    private RecommendationExplanationRedactor() {}

    public static List<RecommendationExplanationFact> filterFacts(
            List<RecommendationExplanationFact> facts,
            DiagnosticsAudience audience) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        DiagnosticsAudience effectiveAudience = audience == null ? DiagnosticsAudience.PUBLIC : audience;
        return facts.stream()
                .filter(fact -> ExplanationCodeRegistry.isAllowedForAudience(fact.code(), effectiveAudience))
                .map(fact -> redactFact(fact, effectiveAudience))
                .toList();
    }

    public static RecommendationExplanationFact redactFact(
            RecommendationExplanationFact fact,
            DiagnosticsAudience audience) {
        DiagnosticsAudience effectiveAudience = audience == null ? DiagnosticsAudience.PUBLIC : audience;
        if (effectiveAudience == DiagnosticsAudience.ADMIN) {
            return fact;
        }
        return new RecommendationExplanationFact(
                fact.code(),
                fact.severity(),
                fact.scope(),
                fact.subject(),
                fact.templateKey(),
                redactValues(fact.values()),
                redactEvidence(fact.evidence(), effectiveAudience),
                fact.scoreImpact());
    }

    public static List<RecommendationExplanationEvidence> redactEvidence(
            List<RecommendationExplanationEvidence> evidence,
            DiagnosticsAudience audience) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        DiagnosticsAudience effectiveAudience = audience == null ? DiagnosticsAudience.PUBLIC : audience;
        if (effectiveAudience == DiagnosticsAudience.ADMIN) {
            return List.copyOf(evidence);
        }
        return evidence.stream()
                .filter(entry -> !ADMIN_ONLY_EVIDENCE_TYPES.contains(normalize(entry.type())))
                .map(RecommendationExplanationRedactor::redactEvidenceReference)
                .toList();
    }

    private static RecommendationExplanationEvidence redactEvidenceReference(RecommendationExplanationEvidence evidence) {
        if ("approval".equals(normalize(evidence.type()))) {
            return new RecommendationExplanationEvidence(evidence.type(), "approval_gate_summary", evidence.field(), evidence.confidence());
        }
        return evidence;
    }

    private static Map<String, Object> redactValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return values.entrySet().stream()
                .filter(entry -> !ADMIN_ONLY_VALUE_KEYS.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
