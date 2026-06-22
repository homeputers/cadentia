package com.cadentia.bot.telegram;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramOperationalControlService {
    private final boolean deploymentEnabled;
    private final TelegramObservabilityRecorder observabilityRecorder;
    private final Map<String, ChannelControl> instanceControls = new ConcurrentHashMap<>();

    public TelegramOperationalControlService(
            @Value("${cadentia.telegram.enabled:true}") boolean deploymentEnabled,
            TelegramObservabilityRecorder observabilityRecorder) {
        this.deploymentEnabled = deploymentEnabled;
        this.observabilityRecorder = observabilityRecorder;
    }

    public boolean acceptsInbound(String churchInstanceId) {
        ChannelControl control = control(churchInstanceId);
        return deploymentEnabled && control.enabled();
    }

    public boolean outboundPaused(String churchInstanceId) {
        return control(churchInstanceId).outboundPaused();
    }

    public ChannelControl update(String churchInstanceId, boolean enabled, boolean outboundPaused, String actorRef, String reason) {
        ChannelControl updated = new ChannelControl(enabled, outboundPaused, safeReason(reason), Instant.now());
        instanceControls.put(key(churchInstanceId), updated);
        observabilityRecorder.audit(enabled ? "telegram_channel_enabled" : "telegram_channel_disabled",
                "telegram_channel", churchInstanceId, actorRef, churchInstanceId, Map.of("reason", safeReason(reason)));
        if (outboundPaused) {
            observabilityRecorder.audit("telegram_outbound_paused", "telegram_channel", churchInstanceId,
                    actorRef, churchInstanceId, Map.of("reason", safeReason(reason)));
        }
        return updated;
    }

    public TelegramHealthStatus health(String churchInstanceId, boolean webhookRegistered, long pendingUpdates, long deadLetters) {
        ChannelControl control = control(churchInstanceId);
        String health = !deploymentEnabled || !control.enabled() ? "DISABLED"
                : !webhookRegistered || deadLetters > 0 ? "DEGRADED" : "HEALTHY";
        return new TelegramHealthStatus(deploymentEnabled, control.enabled(), control.outboundPaused(), webhookRegistered,
                health, pendingUpdates, deadLetters, control.updatedAt());
    }

    private ChannelControl control(String churchInstanceId) {
        return instanceControls.getOrDefault(key(churchInstanceId), new ChannelControl(true, false, "", Instant.EPOCH));
    }

    private String key(String churchInstanceId) {
        return churchInstanceId == null || churchInstanceId.isBlank() ? "default" : churchInstanceId;
    }

    private String safeReason(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.replaceAll("(?i)(bot)?token[=: ][^\\s]+", "$1token=[redacted]")
                .replaceAll("(?i)(secret|webhook)[=: ][^\\s]+", "$1=[redacted]");
    }

    public record ChannelControl(boolean enabled, boolean outboundPaused, String reason, Instant updatedAt) {}
    public record TelegramHealthStatus(boolean deploymentEnabled, boolean instanceEnabled, boolean outboundPaused,
            boolean webhookRegistered, String health, long pendingUpdates, long deadLetters, Instant updatedAt) {}
}
