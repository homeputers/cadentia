package com.cadentia.reng.scoring;

public record RecommendationExplanationSubject(String type, String id, String sourceId, String targetId) {

    public RecommendationExplanationSubject(String type, String id) {
        this(type, id, null, null);
    }
}
