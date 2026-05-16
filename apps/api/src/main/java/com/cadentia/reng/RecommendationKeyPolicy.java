package com.cadentia.reng;

public record RecommendationKeyPolicy(
        boolean preferSameKey,
        boolean allowRelativeMajorMinor,
        int maxKeyCenters,
        boolean allowDynamicTransposition) {

    public RecommendationKeyPolicy {
        if (maxKeyCenters < 1) {
            throw new IllegalArgumentException("maxKeyCenters must be at least 1");
        }
    }
}
