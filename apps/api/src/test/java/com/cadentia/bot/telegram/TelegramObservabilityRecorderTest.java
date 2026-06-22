package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelegramObservabilityRecorderTest {

    @Test
    void recordsStableMetricsAndRedactsAuditReferencesAndSecrets() {
        // Arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TelegramObservabilityRecorder recorder = new TelegramObservabilityRecorder(registry);

        // Act
        recorder.record("webhook_verification", "failure", Duration.ofMillis(12), 4, "ACTIVE",
                "secret_mismatch", "church-raw", "telegram-user-99");
        recorder.audit("telegram_secret_rotated", "telegram_bot", "bot-42", "admin-1", "church-raw",
                Map.of("detail", "token=super-secret chat_id=123"));

        // Assert
        assertThat(registry.get(TelegramObservabilityRecorder.EVENTS_TOTAL).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(TelegramObservabilityRecorder.AUDIT_EVENTS_TOTAL).counter().count()).isEqualTo(1.0);
        assertThat(recorder.auditEvents()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("telegram_secret_rotated");
            assertThat(event.actorRef()).startsWith("ref_").doesNotContain("admin-1");
            assertThat(event.churchInstanceRef()).startsWith("ref_").doesNotContain("church-raw");
            assertThat(event.metadata().get("detail")).doesNotContain("super-secret", "123");
        });
    }
}
