package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class RehearsalWorkflowTelemetryRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RehearsalWorkflowTelemetryRecorder.class);

    private final MeterRegistry meterRegistry;

    public RehearsalWorkflowTelemetryRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordReadinessTransition(
            ReadinessStateCode fromState,
            ReadinessStateCode toState,
            String status,
            boolean emergencyCorrection,
            UUID servicePlanId,
            UUID rehearsalSessionId) {
        Counter.builder("cadentia_rehearsal_readiness_transitions_total")
                .tag("from_state", readiness(fromState))
                .tag("to_state", readiness(toState))
                .tag("status", safeStatus(status))
                .tag("emergency", Boolean.toString(emergencyCorrection))
                .register(meterRegistry)
                .increment();
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("from_state", readiness(fromState));
        fields.put("to_state", readiness(toState));
        fields.put("emergency", emergencyCorrection);
        fields.put("service_plan_id", servicePlanId);
        if (rehearsalSessionId != null) {
            fields.put("rehearsal_session_id", rehearsalSessionId);
        }
        log("readiness_transition", status, fields);
    }

    public void recordReadinessStateDuration(ReadinessStateCode state, Duration duration) {
        Timer.builder("cadentia_rehearsal_readiness_state_duration")
                .tag("state", readiness(state))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordIssueCreated(IssueCategoryCode category, IssueSeverityCode severity) {
        Counter.builder("cadentia_rehearsal_issues_total")
                .tag("action", "created")
                .tag("category", category(category))
                .tag("severity", severity(severity))
                .tag("status", "open")
                .register(meterRegistry)
                .increment();
        log("issue_changed", "created", Map.of("category", category(category), "severity", severity(severity)));
    }

    public void recordIssueStatusChanged(IssueCategoryCode category, IssueStatusCode status) {
        Counter.builder("cadentia_rehearsal_issues_total")
                .tag("action", "status_changed")
                .tag("category", category(category))
                .tag("severity", "unknown")
                .tag("status", issueStatus(status))
                .register(meterRegistry)
                .increment();
        log("issue_changed", issueStatus(status), Map.of("category", category(category)));
    }

    public void recordBlockerCount(ReadinessStateCode state, int openBlockingIssues, int openRequiredActions) {
        Counter.builder("cadentia_rehearsal_blocker_observations_total")
                .tag("state", readiness(state))
                .tag("issue_bucket", bucket(openBlockingIssues))
                .tag("action_bucket", bucket(openRequiredActions))
                .register(meterRegistry)
                .increment();
    }

    public void recordOverrideChanged(String action, String status) {
        Counter.builder("cadentia_rehearsal_overrides_total")
                .tag("action", safeAction(action))
                .tag("status", safeStatus(status))
                .register(meterRegistry)
                .increment();
        log("override_changed", status, Map.of("action", safeAction(action)));
    }

    public void recordFailedReadinessTransition(ReadinessStateCode fromState, ReadinessStateCode toState, String reasonCode) {
        Counter.builder("cadentia_rehearsal_readiness_transition_failures_total")
                .tag("from_state", readiness(fromState))
                .tag("to_state", readiness(toState))
                .tag("reason", safeReason(reasonCode))
                .register(meterRegistry)
                .increment();
        log("readiness_transition", "failed", Map.of(
                "from_state", readiness(fromState),
                "to_state", readiness(toState),
                "failure_reason", safeReason(reasonCode)));
    }

    String safeAction(String value) {
        return switch (normalize(value)) {
            case "created", "updated", "archived", "rollback", "archive_retention", "readiness_transition",
                    "issue_changed", "override_changed" -> normalize(value);
            default -> "other";
        };
    }

    String safeStatus(String value) {
        return switch (normalize(value)) {
            case "created", "updated", "archived", "success", "failed", "resolved", "open", "cancelled" -> normalize(value);
            default -> "other";
        };
    }

    String safeReason(String value) {
        return switch (normalize(value)) {
            case "invalid_state", "readiness_gate", "authorization", "validation", "not_found" -> normalize(value);
            default -> "other";
        };
    }

    private String readiness(ReadinessStateCode state) {
        return state == null ? "unknown" : state.code();
    }

    private String category(IssueCategoryCode category) {
        return category == null ? "unknown" : category.code();
    }

    private String severity(IssueSeverityCode severity) {
        return severity == null ? "unknown" : severity.code();
    }

    private String issueStatus(IssueStatusCode status) {
        return status == null ? "unknown" : status.code();
    }

    private String bucket(int count) {
        if (count <= 0) {
            return "0";
        }
        if (count == 1) {
            return "1";
        }
        if (count <= 5) {
            return "2_5";
        }
        return "gt_5";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return Objects.requireNonNull(value).trim().toLowerCase().replace('-', '_');
    }

    private void log(String action, String status, Map<String, ?> fields) {
        LinkedHashMap<String, Object> event = new LinkedHashMap<>();
        event.put("action", safeAction(action));
        event.put("status", safeStatus(status));
        event.put("correlation_id", correlationId());
        event.putAll(fields);
        LOGGER.info("rehearsal_workflow_event={}", event);
    }

    private String correlationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("requestId");
        }
        return correlationId == null || correlationId.isBlank() ? "unavailable" : correlationId;
    }
}
