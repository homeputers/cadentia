package com.cadentia.bot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TelegramOperationalControlServiceTest {

    @Test
    void disablesInstanceWithoutDisablingCoreApiAndEmitsAuditEvent() {
        // Arrange
        TelegramObservabilityRecorder recorder = new TelegramObservabilityRecorder(new SimpleMeterRegistry());
        TelegramOperationalControlService service = new TelegramOperationalControlService(true, recorder);

        // Act
        TelegramOperationalControlService.ChannelControl control = service.update(
                "church-1", false, true, "operator-1", "incident secret=raw");
        TelegramOperationalControlService.TelegramHealthStatus health = service.health("church-1", true, 0, 0);

        // Assert
        assertThat(control.enabled()).isFalse();
        assertThat(service.acceptsInbound("church-1")).isFalse();
        assertThat(service.acceptsInbound("church-2")).isTrue();
        assertThat(service.outboundPaused("church-1")).isTrue();
        assertThat(health.health()).isEqualTo("DISABLED");
        assertThat(recorder.auditEvents()).extracting(TelegramObservabilityRecorder.TelegramAuditEvent::action)
                .contains("telegram_channel_disabled", "telegram_outbound_paused");
        assertThat(recorder.auditEvents().get(0).metadata().get("reason")).doesNotContain("raw");
    }
}
