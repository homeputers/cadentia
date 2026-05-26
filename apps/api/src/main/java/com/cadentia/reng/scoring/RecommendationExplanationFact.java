package com.cadentia.reng.scoring;

import java.util.List;
import java.util.Map;

public record RecommendationExplanationFact(
        String code,
        String severity,
        String scope,
        RecommendationExplanationSubject subject,
        String templateKey,
        Map<String, Object> values,
        List<RecommendationExplanationEvidence> evidence,
        Double scoreImpact) {

    public RecommendationExplanationFact {
        values = values == null ? Map.of() : Map.copyOf(values);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        ExplanationCodeRegistry.validateFact(code, severity, scope);
    }
}
