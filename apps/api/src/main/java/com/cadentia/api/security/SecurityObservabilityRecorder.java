package com.cadentia.api.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SecurityObservabilityRecorder {

    private final MeterRegistry meterRegistry;

    public SecurityObservabilityRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuthorizationDecision(String operationClass, String role, String decision, String surface) {
        String normalizedRole = normalize(role, "UNKNOWN");
        Counter.builder("cadentia_authz_decisions_total")
                .tag("operation_class", normalize(operationClass, "unknown"))
                .tag("role", normalizedRole)
                .tag("decision", normalize(decision, "unknown"))
                .tag("surface", normalize(surface, "service_policy"))
                .register(meterRegistry)
                .increment();
    }

    public void recordApprovalDecision(String reviewDomain, String decision, String actorRole) {
        Counter.builder("cadentia_approval_decisions_total")
                .tag("review_domain", normalize(reviewDomain, "unknown"))
                .tag("decision", normalize(decision, "unknown"))
                .tag("actor_role", normalize(actorRole, "UNKNOWN"))
                .register(meterRegistry)
                .increment();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Objects.requireNonNullElse(value.trim(), fallback);
    }
}
