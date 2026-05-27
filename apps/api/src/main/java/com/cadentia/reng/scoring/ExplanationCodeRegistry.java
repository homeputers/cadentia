package com.cadentia.reng.scoring;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExplanationCodeRegistry {

    private static final Map<String, Entry> ENTRIES = List.of(
                    new Entry("ROLE_FIT", Status.ACTIVE, Set.of(Scope.ITEM), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("APPROVAL_ELIGIBLE", Status.ACTIVE, Set.of(Scope.ITEM), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("THEME_MATCH", Status.ACTIVE, Set.of(Scope.ITEM), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("METADATA_LOW_CONFIDENCE", Status.ACTIVE, Set.of(Scope.ITEM), Set.of(Severity.WARNING), "v1", null, null),
                    new Entry("FEEDBACK_TUNING", Status.ACTIVE, Set.of(Scope.ITEM), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("SAME_KEY_TRANSITION", Status.ACTIVE, Set.of(Scope.TRANSITION), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("RELATIVE_KEY_TRANSITION", Status.ACTIVE, Set.of(Scope.TRANSITION), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("CLOSE_KEY_TRANSITION", Status.ACTIVE, Set.of(Scope.TRANSITION), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("MODULATION_PENALTY", Status.ACTIVE, Set.of(Scope.TRANSITION), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("TEMPO_POLICY_OK", Status.ACTIVE, Set.of(Scope.TRANSITION), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("METER_COMPATIBLE", Status.ACTIVE, Set.of(Scope.TRANSITION), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("ENERGY_ARC_MATCH", Status.ACTIVE, Set.of(Scope.TRANSITION, Scope.SET), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("COUNT_TARGET_MET", Status.ACTIVE, Set.of(Scope.SET), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("INSUFFICIENT_CANDIDATES", Status.ACTIVE, Set.of(Scope.SET), Set.of(Severity.WARNING), "v1", null, null),
                    new Entry("KEY_CENTER_POLICY_MET", Status.ACTIVE, Set.of(Scope.SET), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("THEME_COVERAGE", Status.ACTIVE, Set.of(Scope.SET), Set.of(Severity.INFO, Severity.WARNING), "v1", null, null),
                    new Entry("REQUEST_DEFAULTS_APPLIED", Status.ACTIVE, Set.of(Scope.SET), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("LOW_CONFIDENCE_METADATA_PRESENT", Status.ACTIVE, Set.of(Scope.SET), Set.of(Severity.WARNING), "v1", null, null),
                    new Entry("FILLED_QUOTA", Status.ACTIVE, Set.of(Scope.CANDIDATE_EXCLUSION), Set.of(Severity.INFO), "v1", null, null),
                    new Entry("WEAKER_SCORE", Status.ACTIVE, Set.of(Scope.CANDIDATE_EXCLUSION), Set.of(Severity.INFO), "v1", null, null))
            .stream()
            .collect(Collectors.toUnmodifiableMap(Entry::code, Function.identity()));

    private ExplanationCodeRegistry() {}

    public static void validateFact(String code, String severity, String scope) {
        Entry entry = ENTRIES.get(code);
        if (entry == null) {
            throw new IllegalArgumentException("Unregistered explanation code: " + code);
        }
        if (entry.status() != Status.ACTIVE) {
            throw new IllegalArgumentException("Non-active explanation code emission is not allowed: " + code);
        }
        Scope parsedScope = Scope.fromWireValue(scope);
        Severity parsedSeverity = Severity.fromWireValue(severity);
        if (!entry.allowedScopes().contains(parsedScope)) {
            throw new IllegalArgumentException("Scope " + parsedScope + " is not allowed for code " + code);
        }
        if (!entry.allowedSeverities().contains(parsedSeverity)) {
            throw new IllegalArgumentException("Severity " + parsedSeverity + " is not allowed for code " + code);
        }
    }

    public static Set<String> activeCodes() {
        return ENTRIES.values().stream()
                .filter(entry -> entry.status() == Status.ACTIVE)
                .map(Entry::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    public enum Status {ACTIVE, DEPRECATED, REPLACED}

    public enum Scope {
        ITEM("item"),
        TRANSITION("transition"),
        SET("set"),
        CANDIDATE_EXCLUSION("candidate_exclusion");

        private final String wireValue;

        Scope(String wireValue) {this.wireValue = wireValue;}

        static Scope fromWireValue(String value) {
            for (Scope scope : values()) {
                if (scope.wireValue.equals(value)) {
                    return scope;
                }
            }
            throw new IllegalArgumentException("Unknown explanation scope: " + value);
        }
    }

    public enum Severity {
        INFO("info"),
        WARNING("warning"),
        BLOCKED("blocked");
        private final String wireValue;
        Severity(String wireValue) {this.wireValue = wireValue;}
        static Severity fromWireValue(String value) {
            for (Severity severity : values()) {
                if (severity.wireValue.equals(value)) {
                    return severity;
                }
            }
            throw new IllegalArgumentException("Unknown explanation severity: " + value);
        }
    }

    public record Entry(
            String code,
            Status status,
            Set<Scope> allowedScopes,
            Set<Severity> allowedSeverities,
            String introducedInVersion,
            String deprecatedInVersion,
            String replacedBy) {
        public Entry {
            allowedScopes = allowedScopes == null ? EnumSet.noneOf(Scope.class) : Set.copyOf(allowedScopes);
            allowedSeverities = allowedSeverities == null ? EnumSet.noneOf(Severity.class) : Set.copyOf(allowedSeverities);
        }
    }
}
