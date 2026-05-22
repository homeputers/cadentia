package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScoringProfileLifecycleTest {

    @Test
    void deprecatedStateRequiresSuccessorVersion() {
        assertThatThrownBy(() -> new ScoringProfileLifecycle(
                ScoringProfileLifecycle.ProfileState.DEPRECATED,
                "v1",
                ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deprecatedByVersion");
    }

    @Test
    void activeStateCannotCarryDeprecationVersion() {
        assertThatThrownBy(() -> new ScoringProfileLifecycle(
                ScoringProfileLifecycle.ProfileState.ACTIVE,
                null,
                "v2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Active profiles");
    }
}
