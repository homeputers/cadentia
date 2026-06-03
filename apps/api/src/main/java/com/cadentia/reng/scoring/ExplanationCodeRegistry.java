package com.cadentia.reng.scoring;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExplanationCodeRegistry {

    private static final Set<Audience> PUBLIC_AUDIENCES = Set.of(
            Audience.PUBLIC,
            Audience.WORSHIP_LEADER,
            Audience.ADMIN);
    private static final Set<Audience> INTERNAL_AUDIENCES = Set.of(
            Audience.WORSHIP_LEADER,
            Audience.ADMIN);
    private static final Set<Audience> ADMIN_AUDIENCE = Set.of(Audience.ADMIN);

    private static final Map<String, Entry> ENTRIES = registryEntries().stream()
            .collect(Collectors.toUnmodifiableMap(Entry::code, Function.identity()));

    private static List<Entry> registryEntries() {
        return List.of(
            item("ROLE_FIT", "item_fit", "item.role_fit", Set.of("score")),
            item("APPROVAL_ELIGIBLE", "eligibility", "item.approval_eligible", Set.of("hasProvenance")),
            item("THEME_MATCH", "theme_scripture", "item.theme_match", Set.of("themes")),
            item("SCRIPTURE_MATCH", "theme_scripture", "item.scripture_match", Set.of("scripture")),
            itemInternal(
                    "SCORE_COMPONENT_MUSICAL_FIT",
                    "score_components",
                    "item.score_component_musical_fit",
                    Set.of("score")),
            itemInternal(
                    "SCORE_COMPONENT_ENERGY_FIT",
                    "score_components",
                    "item.score_component_energy_fit",
                    Set.of("score")),
            itemInternal(
                    "METADATA_LOW_CONFIDENCE",
                    "warnings",
                    "item.metadata_low_confidence",
                    Set.of("confidence"),
                    Severity.WARNING),
            itemInternal(
                    "FEEDBACK_TUNING",
                    "score_components",
                    "item.feedback_tuning",
                    Set.of("feedbackContribution"),
                    Severity.INFO,
                    Severity.WARNING),
            transition(
                    "SAME_KEY_TRANSITION",
                    "transitions",
                    "transition.same_key",
                    Set.of("fromKey", "toKey")),
            transition(
                    "RELATIVE_KEY_TRANSITION",
                    "transitions",
                    "transition.relative_key",
                    Set.of("fromKey", "toKey", "allowRelativeMajorMinor")),
            transitionInternal(
                    "CLOSE_KEY_TRANSITION",
                    "transitions",
                    "transition.close_key",
                    Set.of("fromKey", "toKey")),
            transitionInternal(
                    "MODULATION_PENALTY",
                    "tradeoffs",
                    "transition.modulation_penalty",
                    Set.of("penalty"),
                    Severity.INFO,
                    Severity.WARNING),
            transition(
                    "TEMPO_POLICY_OK",
                    "transitions",
                    "transition.tempo_policy",
                    Set.of("fromBpm", "toBpm", "maxJumpBpm"),
                    Severity.INFO,
                    Severity.WARNING),
            transitionInternal(
                    "METER_COMPATIBLE",
                    "transitions",
                    "transition.meter_compatibility",
                    Set.of("fromMeter", "toMeter"),
                    Severity.INFO,
                    Severity.WARNING),
            transition(
                    "ENERGY_ARC_MATCH",
                    "energy_arc",
                    "transition.energy_continuity",
                    Set.of("fromEnergy", "toEnergy"),
                    Severity.INFO,
                    Severity.WARNING),
            transition(
                    "TEMPO_TRADEOFF_ACCEPTED",
                    "tradeoffs",
                    "transition.tempo_tradeoff_accepted",
                    Set.of("fromBpm", "toBpm", "maxJumpBpm", "jumpBpm", "penalty"),
                    Severity.WARNING),
            transitionInternal(
                    "ARRANGEMENT_COMPATIBLE",
                    "transitions",
                    "transition.arrangement_compatibility",
                    Set.of("compatible", "parserConfidence"),
                    Severity.INFO,
                    Severity.WARNING),
            transitionInternal(
                    "TRANSITION_METADATA_MISSING",
                    "warnings",
                    "transition.metadata_missing",
                    Set.of("missingFields"),
                    Severity.WARNING),
            set(
                    "COUNT_TARGET_MET",
                    "set_shape",
                    "set.count_target",
                    Set.of(
                            "selected",
                            "target",
                            "selectedPraise",
                            "requestedPraise",
                            "selectedWorship",
                            "requestedWorship"),
                    Severity.INFO,
                    Severity.WARNING),
            set(
                    "INSUFFICIENT_CANDIDATES",
                    "warnings",
                    "warning.insufficient_candidates",
                    Set.of("selected", "target", "availableCandidates", "limitation"),
                    Severity.WARNING),
            set(
                    "KEY_CENTER_POLICY_MET",
                    "set_shape",
                    "set.key_centers",
                    Set.of(
                            "distinctKeyCenters",
                            "maxKeyCenters",
                            "preferSameKey",
                            "allowRelativeMajorMinor",
                            "keyCenters"),
                    Severity.INFO,
                    Severity.WARNING),
            set(
                    "TEMPO_POLICY_MET",
                    "set_shape",
                    "set.tempo_policy",
                    Set.of("maxJumpBpm", "evaluatedTransitions", "maxObservedJumpBpm", "exceededJumps"),
                    Severity.INFO,
                    Severity.WARNING),
            set(
                    "SET_ENERGY_ARC_MATCH",
                    "energy_arc",
                    "set.energy_arc",
                    Set.of(
                            "requestedArc",
                            "arcVersion",
                            "firstPosition",
                            "lastPosition",
                            "firstEnergy",
                            "lastEnergy",
                            "shapeScore",
                            "sectionBalanceScore",
                            "tradeoffs"),
                    Severity.INFO,
                    Severity.WARNING),
            set(
                    "THEME_COVERAGE",
                    "theme_scripture",
                    "set.theme_coverage",
                    Set.of("coveredItems", "selectedItems", "requestedThemes", "coveredThemes"),
                    Severity.INFO,
                    Severity.WARNING),
            set(
                    "SCRIPTURE_COVERAGE",
                    "theme_scripture",
                    "set.scripture_coverage",
                    Set.of("coveredItems", "selectedItems", "coveredScripture"),
                    Severity.INFO,
                    Severity.WARNING),
            set(
                    "REQUEST_DEFAULTS_APPLIED",
                    "policy",
                    "set.defaults_applied",
                    Set.of(
                            "countsDefaulted",
                            "keyPolicyDefaulted",
                            "tempoPolicyDefaulted",
                            "languageDefaulted",
                            "defaultPraise",
                            "defaultWorship",
                            "preferSameKey",
                            "allowRelativeMajorMinor",
                            "maxKeyCenters",
                            "maxJumpBpm")),
            set(
                    "LOW_CONFIDENCE_METADATA_PRESENT",
                    "warnings",
                    "warning.low_confidence_metadata",
                    Set.of("reason"),
                    Severity.WARNING),
            diagnostic(
                    "EXCLUDED_APPROVAL_GATE",
                    "eligibility",
                    "diagnostic.excluded_approval_gate",
                    Set.of("gate", "candidateCount"),
                    Severity.BLOCKED),
            diagnostic(
                    "EXCLUDED_MISSING_PROVENANCE",
                    "eligibility",
                    "diagnostic.excluded_missing_provenance",
                    Set.of("candidateCount"),
                    Severity.BLOCKED),
            diagnostic(
                    "EXCLUDED_LICENSING_CONCERN",
                    "eligibility",
                    "diagnostic.excluded_licensing_concern",
                    Set.of("candidateCount"),
                    Severity.BLOCKED),
            diagnostic(
                    "EXCLUDED_INACTIVE_ARRANGEMENT",
                    "eligibility",
                    "diagnostic.excluded_inactive_arrangement",
                    Set.of("candidateCount"),
                    Severity.BLOCKED),
            diagnostic(
                    "EXCLUDED_DUPLICATE_ARRANGEMENT",
                    "near_miss",
                    "diagnostic.excluded_duplicate_arrangement",
                    Set.of("duplicateOfArrangementId"),
                    Severity.INFO),
            diagnostic(
                    "EXCLUDED_KEY_CENTER_LIMIT",
                    "near_miss",
                    "diagnostic.excluded_key_center_limit",
                    Set.of("candidateKey", "maxKeyCenters"),
                    Severity.INFO),
            diagnostic(
                    "EXCLUDED_TEMPO_POLICY",
                    "near_miss",
                    "diagnostic.excluded_tempo_policy",
                    Set.of("fromBpm", "toBpm", "maxJumpBpm"),
                    Severity.WARNING),
            candidateExclusion(
                    "EXCLUDED_WEAKER_SCORE",
                    "near_miss",
                    "candidate_exclusion.weaker_score",
                    Set.of("candidateTitle", "candidateScore")),
            candidateExclusion(
                    "EXCLUDED_QUOTA_FILLED",
                    "near_miss",
                    "candidate_exclusion.quota_filled",
                    Set.of("candidateTitle", "candidateScore")),
            tieBreak(
                    "DETERMINISTIC_TIE_BREAK_APPLIED",
                    "tie_breaks",
                    "tie_break.deterministic_applied",
                    Set.of("rule", "direction", "affectedResultIds")));
    }

    private ExplanationCodeRegistry() {}

    public static void validateFact(
            String code,
            String severity,
            String scope,
            String localizationKey,
            Set<String> valueKeys) {
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
        if (!entry.localizationKey().equals(localizationKey)) {
            throw new IllegalArgumentException(
                    "Localization key " + localizationKey + " is not registered for code " + code);
        }

        Set<String> unexpectedValueKeys = Set.copyOf(valueKeys == null ? Set.of() : valueKeys).stream()
                .filter(valueKey -> !entry.allowedValueKeys().contains(valueKey))
                .collect(Collectors.toUnmodifiableSet());
        if (!unexpectedValueKeys.isEmpty()) {
            throw new IllegalArgumentException("Unexpected value keys for code " + code + ": " + unexpectedValueKeys);
        }
    }

    public static Set<String> activeCodes() {
        return ENTRIES.values().stream()
                .filter(entry -> entry.status() == Status.ACTIVE)
                .map(Entry::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static List<Entry> activeEntries() {
        return ENTRIES.values().stream()
                .filter(entry -> entry.status() == Status.ACTIVE)
                .sorted((left, right) -> left.code().compareTo(right.code()))
                .toList();
    }

    public static boolean isAllowedForAudience(String code, DiagnosticsAudience audience) {
        Entry entry = ENTRIES.get(code);
        if (entry == null || entry.status() != Status.ACTIVE) {
            return false;
        }
        Audience registryAudience = switch (audience == null ? DiagnosticsAudience.PUBLIC : audience) {
            case PUBLIC -> Audience.PUBLIC;
            case WORSHIP_LEADER -> Audience.WORSHIP_LEADER;
            case ADMIN -> Audience.ADMIN;
        };
        return entry.audiences().contains(registryAudience);
    }

    public static String displayGroup(String code) {
        Entry entry = ENTRIES.get(code);
        return entry == null ? null : entry.displayGroup();
    }

    private static Entry item(String code, String group, String key, Set<String> values, Severity... severities) {
        return entry(code, group, key, Set.of(Scope.ITEM), PUBLIC_AUDIENCES, values, severities);
    }

    private static Entry itemInternal(
            String code,
            String group,
            String key,
            Set<String> values,
            Severity... severities) {
        return entry(code, group, key, Set.of(Scope.ITEM), INTERNAL_AUDIENCES, values, severities);
    }

    private static Entry transition(
            String code,
            String group,
            String key,
            Set<String> values,
            Severity... severities) {
        return entry(code, group, key, Set.of(Scope.TRANSITION), PUBLIC_AUDIENCES, values, severities);
    }

    private static Entry transitionInternal(
            String code,
            String group,
            String key,
            Set<String> values,
            Severity... severities) {
        return entry(code, group, key, Set.of(Scope.TRANSITION), INTERNAL_AUDIENCES, values, severities);
    }

    private static Entry set(String code, String group, String key, Set<String> values, Severity... severities) {
        return entry(code, group, key, Set.of(Scope.SET), PUBLIC_AUDIENCES, values, severities);
    }

    private static Entry diagnostic(
            String code,
            String group,
            String key,
            Set<String> values,
            Severity... severities) {
        return entry(code, group, key, Set.of(Scope.DIAGNOSTIC), ADMIN_AUDIENCE, values, severities);
    }

    private static Entry candidateExclusion(String code, String group, String key, Set<String> values) {
        return entry(
                code,
                group,
                key,
                Set.of(Scope.CANDIDATE_EXCLUSION, Scope.DIAGNOSTIC),
                ADMIN_AUDIENCE,
                values,
                Severity.INFO);
    }

    private static Entry tieBreak(String code, String group, String key, Set<String> values) {
        return entry(
                code,
                group,
                key,
                Set.of(Scope.SET, Scope.DIAGNOSTIC),
                PUBLIC_AUDIENCES,
                values,
                Severity.INFO);
    }

    private static Entry entry(
            String code,
            String displayGroup,
            String localizationKey,
            Set<Scope> scopes,
            Set<Audience> audiences,
            Set<String> values,
            Severity... severities) {
        Set<Severity> allowedSeverities = severities.length == 0 ? Set.of(Severity.INFO) : Set.of(severities);
        return new Entry(
                code,
                Status.ACTIVE,
                true,
                displayGroup,
                localizationKey,
                scopes,
                allowedSeverities,
                audiences,
                values,
                "v1",
                null,
                null);
    }

    public enum Status {
        ACTIVE,
        DEPRECATED,
        REPLACED
    }

    public enum Scope {
        ITEM("item"),
        TRANSITION("transition"),
        SET("set"),
        DIAGNOSTIC("diagnostic"),
        CANDIDATE_EXCLUSION("candidate_exclusion");

        private final String wireValue;

        Scope(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

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

        Severity(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        static Severity fromWireValue(String value) {
            for (Severity severity : values()) {
                if (severity.wireValue.equals(value)) {
                    return severity;
                }
            }
            throw new IllegalArgumentException("Unknown explanation severity: " + value);
        }
    }

    public enum Audience {
        PUBLIC("public"),
        WORSHIP_LEADER("worship_leader"),
        ADMIN("admin");

        private final String wireValue;

        Audience(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record Entry(
            String code,
            Status status,
            boolean stableForClients,
            String displayGroup,
            String localizationKey,
            Set<Scope> allowedScopes,
            Set<Severity> allowedSeverities,
            Set<Audience> audiences,
            Set<String> allowedValueKeys,
            String introducedInVersion,
            String deprecatedInVersion,
            String replacedBy) {
        public Entry {
            allowedScopes = allowedScopes == null ? EnumSet.noneOf(Scope.class) : Set.copyOf(allowedScopes);
            allowedSeverities = allowedSeverities == null ? EnumSet.noneOf(Severity.class) : Set.copyOf(allowedSeverities);
            audiences = audiences == null ? EnumSet.noneOf(Audience.class) : Set.copyOf(audiences);
            allowedValueKeys = allowedValueKeys == null ? Set.of() : Set.copyOf(allowedValueKeys);
        }
    }
}
