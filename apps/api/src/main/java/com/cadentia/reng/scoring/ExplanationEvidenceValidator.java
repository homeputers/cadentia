package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ExplanationEvidenceValidator {

    private static final Set<String> ALLOWED_EVIDENCE_TYPES = Set.of(
            "score",
            "catalog",
            "approval",
            "provenance",
            "transition",
            "request",
            "arrangement_suitability",
            "service_assignment");

    private ExplanationEvidenceValidator() {}

    public static void validate(RecommendationExplanationFact fact, Map<String, Set<String>> availableRefsByType) {
        Objects.requireNonNull(fact, "fact");
        Objects.requireNonNull(availableRefsByType, "availableRefsByType");

        List<RecommendationExplanationEvidence> evidence = fact.evidence();
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("Explanation fact must include at least one evidence record: " + fact.code());
        }

        for (RecommendationExplanationEvidence entry : evidence) {
            if (entry == null || entry.type() == null || entry.type().isBlank()) {
                throw new IllegalArgumentException("Explanation evidence type is required for code: " + fact.code());
            }
            if (!ALLOWED_EVIDENCE_TYPES.contains(entry.type())) {
                throw new IllegalArgumentException("Unsupported explanation evidence type: " + entry.type());
            }
            if (entry.ref() == null || entry.ref().isBlank()) {
                throw new IllegalArgumentException("Explanation evidence ref is required for code: " + fact.code());
            }

            Set<String> availableRefs = availableRefsByType.getOrDefault(entry.type(), Set.of());
            if (!availableRefs.contains(entry.ref())) {
                throw new IllegalArgumentException("Unresolvable explanation evidence reference: " + entry.type() + ":" + entry.ref());
            }
        }
    }
}
