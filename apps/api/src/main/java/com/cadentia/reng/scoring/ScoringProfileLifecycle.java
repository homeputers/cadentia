package com.cadentia.reng.scoring;

public record ScoringProfileLifecycle(ProfileState state, String activatedFromVersion, String deprecatedByVersion) {

    public enum ProfileState {
        DRAFT,
        ACTIVE,
        DEPRECATED
    }

    public ScoringProfileLifecycle {
        state = state == null ? ProfileState.DRAFT : state;
        if (state == ProfileState.ACTIVE && deprecatedByVersion != null && !deprecatedByVersion.isBlank()) {
            throw new IllegalArgumentException("Active profiles cannot have a deprecatedByVersion");
        }
        if (state == ProfileState.DEPRECATED && (deprecatedByVersion == null || deprecatedByVersion.isBlank())) {
            throw new IllegalArgumentException("Deprecated profiles must declare deprecatedByVersion");
        }
    }

    public static ScoringProfileLifecycle active() {
        return new ScoringProfileLifecycle(ProfileState.ACTIVE, null, null);
    }
}
