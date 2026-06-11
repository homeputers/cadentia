package com.cadentia.rehearsal;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class RehearsalWorkflowTelemetryRecorderTest {

    @Test
    void normalizesUnsafeFreeTextLabelsToBoundedFallbacks() {
        // Arrange
        RehearsalWorkflowTelemetryRecorder recorder = new RehearsalWorkflowTelemetryRecorder(new SimpleMeterRegistry());

        // Act / Assert
        assertThat(recorder.safeAction("Amazing Grace rehearsal note for Bob")).isEqualTo("other");
        assertThat(recorder.safeStatus("call alice@example.com")).isEqualTo("other");
        assertThat(recorder.safeReason("lyrics pasted into failure text")).isEqualTo("other");
        assertThat(recorder.safeReason("readiness-gate")).isEqualTo("readiness_gate");
    }
}
