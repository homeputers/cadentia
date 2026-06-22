package com.cadentia.bot.telegram;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TelegramObservabilityRecorder {
    public static final String EVENTS_TOTAL = "cadentia_telegram_events_total";
    public static final String LATENCY_SECONDS = "cadentia_telegram_operation_latency_seconds";
    public static final String AUDIT_EVENTS_TOTAL = "cadentia_telegram_audit_events_total";
    private static final Logger log = LoggerFactory.getLogger(TelegramObservabilityRecorder.class);

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final List<TelegramAuditEvent> auditEvents = new ArrayList<>();

    public TelegramObservabilityRecorder(MeterRegistry meterRegistry) {
        this(meterRegistry, ObservationRegistry.NOOP);
    }

    @Autowired
    public TelegramObservabilityRecorder(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
    }

    public void record(String operation, String outcome, Duration latency, int retryCount, String sessionState,
            String errorCategory, String instanceRef, String actorRef) {
        String safeOperation = normalize(operation, "unknown");
        String safeOutcome = normalize(outcome, "unknown");
        Observation observation = Observation.start("cadentia.telegram.operation", observationRegistry)
                .lowCardinalityKeyValue("channel", "telegram")
                .lowCardinalityKeyValue("operation", safeOperation)
                .lowCardinalityKeyValue("outcome", safeOutcome);
        Counter.builder(EVENTS_TOTAL)
                .tag("channel", "telegram")
                .tag("operation", safeOperation)
                .tag("outcome", safeOutcome)
                .tag("retry_count", bucketRetryCount(retryCount))
                .tag("session_state", normalize(sessionState, "none"))
                .tag("error_category", normalize(errorCategory, "none"))
                .tag("instance_ref", redactRef(instanceRef))
                .tag("actor_ref", redactRef(actorRef))
                .register(meterRegistry)
                .increment();
        Timer.builder(LATENCY_SECONDS)
                .tag("channel", "telegram")
                .tag("operation", safeOperation)
                .tag("outcome", safeOutcome)
                .register(meterRegistry)
                .record(latency == null ? Duration.ZERO : latency);
        log.info("telegram_observability_event channel=telegram operation={} outcome={} retry_count={} session_state={} error_category={} instance_ref={} actor_ref={}",
                safeOperation, safeOutcome, bucketRetryCount(retryCount), normalize(sessionState, "none"),
                normalize(errorCategory, "none"), redactRef(instanceRef), redactRef(actorRef));
        observation.stop();
    }

    public synchronized void audit(String action, String targetType, String targetRef, String actorRef,
            String churchInstanceRef, Map<String, String> metadata) {
        TelegramAuditEvent event = new TelegramAuditEvent(Instant.now(), normalize(action, "unknown"),
                normalize(targetType, "telegram_channel"), redactRef(targetRef), redactRef(actorRef),
                redactRef(churchInstanceRef), sanitizeMetadata(metadata));
        auditEvents.add(event);
        Counter.builder(AUDIT_EVENTS_TOTAL)
                .tag("channel", "telegram")
                .tag("action", event.action())
                .tag("target_type", event.targetType())
                .register(meterRegistry)
                .increment();
        log.info("telegram_audit_event action={} target_type={} target_ref={} actor_ref={} church_instance_ref={}",
                event.action(), event.targetType(), event.targetRef(), event.actorRef(), event.churchInstanceRef());
    }

    public synchronized List<TelegramAuditEvent> auditEvents() {
        return List.copyOf(auditEvents);
    }

    public static String redactRef(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        int hash = Math.abs(Objects.requireNonNull(value).hashCode());
        return "ref_" + Integer.toHexString(hash);
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return metadata.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> normalize(entry.getKey(), "unknown"),
                entry -> sanitize(entry.getValue())));
    }

    private String sanitize(String value) {
        String sanitized = value == null ? "none" : value
                .replaceAll("(?i)(bot)?token[=: ][^\\s]+", "$1token=[redacted]")
                .replaceAll("(?i)(secret|webhook)[=: ][^\\s]+", "$1=[redacted]")
                .replaceAll("(?i)(chat|user|message)_?id[=: ][^\\s]+", "$1_id=[redacted]");
        return normalize(sanitized, "none");
    }

    private String bucketRetryCount(int retryCount) {
        if (retryCount <= 0) {
            return "0";
        }
        if (retryCount == 1) {
            return "1";
        }
        if (retryCount <= 3) {
            return "2_3";
        }
        return "4_plus";
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
    }

    public record TelegramAuditEvent(Instant occurredAt, String action, String targetType, String targetRef,
            String actorRef, String churchInstanceRef, Map<String, String> metadata) {}
}
