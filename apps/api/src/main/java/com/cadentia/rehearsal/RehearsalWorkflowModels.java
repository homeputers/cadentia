package com.cadentia.rehearsal;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RehearsalWorkflowModels {

    private RehearsalWorkflowModels() {
    }

    public enum ReadinessStateCode {
        DRAFT("draft"),
        PLANNED("planned"),
        REHEARSING("rehearsing"),
        ISSUES_OPEN("issues_open"),
        READY("ready"),
        COMPLETED("completed");

        private final String code;

        ReadinessStateCode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static ReadinessStateCode fromCode(String code) {
            for (ReadinessStateCode value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown readiness state code: " + code);
        }
    }

    public enum TargetTypeCode {
        SERVICE("service"),
        REHEARSAL_SESSION("rehearsal_session"),
        SETLIST_ITEM("setlist_item"),
        TRANSITION("transition"),
        ARRANGEMENT("arrangement"),
        TEAM_ROLE("team_role"),
        MUSICIAN_ASSIGNMENT("musician_assignment");

        private final String code;

        TargetTypeCode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static TargetTypeCode fromCode(String code) {
            for (TargetTypeCode value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown target type code: " + code);
        }
    }

    public enum IssueCategoryCode {
        UNRESOLVED_TRANSITION("unresolved_transition"),
        DIFFICULT_SONG("difficult_song"),
        BLOCKER("blocker"),
        ARRANGEMENT_CONCERN("arrangement_concern"),
        TEAM_ROLE_CONCERN("team_role_concern"),
        GENERAL_FOLLOW_UP("general_follow_up");

        private final String code;

        IssueCategoryCode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static IssueCategoryCode fromCode(String code) {
            for (IssueCategoryCode value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown issue category code: " + code);
        }
    }

    public enum IssueSeverityCode {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        BLOCKING("blocking");

        private final String code;

        IssueSeverityCode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static IssueSeverityCode fromCode(String code) {
            for (IssueSeverityCode value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown issue severity code: " + code);
        }
    }

    public enum IssueStatusCode {
        OPEN("open"),
        IN_PROGRESS("in_progress"),
        RESOLVED("resolved"),
        DEFERRED("deferred"),
        CANCELLED("cancelled");

        private final String code;

        IssueStatusCode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static IssueStatusCode fromCode(String code) {
            for (IssueStatusCode value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown issue status code: " + code);
        }
    }

    public enum IssueActionStatusCode {
        TODO("todo"),
        IN_PROGRESS("in_progress"),
        DONE("done"),
        CANCELLED("cancelled");

        private final String code;

        IssueActionStatusCode(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static IssueActionStatusCode fromCode(String code) {
            for (IssueActionStatusCode value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown issue action status code: " + code);
        }
    }

    public enum IssueOwnerType {
        ACTOR("actor"),
        TEAM_ROLE("team_role"),
        SERVICE_ASSIGNMENT("service_assignment"),
        UNASSIGNED("unassigned");

        private final String code;

        IssueOwnerType(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static IssueOwnerType fromCode(String code) {
            for (IssueOwnerType value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown issue owner type: " + code);
        }
    }

    public record ControlledVocabularyEntry(
            String code,
            String displayName,
            int sortOrder,
            boolean active,
            boolean systemDefault) {
    }

    public record RehearsalSessionRecord(
            UUID rehearsalSessionId,
            UUID servicePlanId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            ReadinessStateCode readinessStateCode,
            Instant archivedAt) {
    }

    public record WorkflowStatus(
            UUID servicePlanId,
            ReadinessStateCode explicitStateCode,
            ReadinessStateCode derivedStateCode,
            int openBlockingIssueCount,
            int openRequiredActionCount) {

        public boolean readyForService() {
            return derivedStateCode == ReadinessStateCode.READY || derivedStateCode == ReadinessStateCode.COMPLETED;
        }
    }

    public record RehearsalTarget(
            TargetTypeCode targetTypeCode,
            UUID rehearsalSessionId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId,
            UUID transitionFromBlockId,
            UUID transitionToBlockId,
            UUID arrangementId,
            String teamRoleCode,
            UUID serviceTeamAssignmentId,
            UUID rehearsalTeamAssignmentId,
            UUID songAssignmentOverrideId) {

        public static RehearsalTarget service() {
            return new RehearsalTarget(
                    TargetTypeCode.SERVICE, null, null, null, null, null, null, null, null, null, null);
        }

        public static RehearsalTarget session(UUID rehearsalSessionId) {
            return new RehearsalTarget(TargetTypeCode.REHEARSAL_SESSION, rehearsalSessionId, null, null, null, null,
                    null, null, null, null, null);
        }

        public static RehearsalTarget setlistItem(UUID servicePlanBlockId, UUID setlistVersionItemId) {
            return new RehearsalTarget(TargetTypeCode.SETLIST_ITEM, null, servicePlanBlockId, setlistVersionItemId,
                    null, null, null, null, null, null, null);
        }

        public static RehearsalTarget transition(UUID transitionFromBlockId, UUID transitionToBlockId) {
            return new RehearsalTarget(TargetTypeCode.TRANSITION, null, null, null, transitionFromBlockId,
                    transitionToBlockId, null, null, null, null, null);
        }

        public static RehearsalTarget arrangement(UUID arrangementId) {
            return new RehearsalTarget(TargetTypeCode.ARRANGEMENT, null, null, null, null, null, arrangementId,
                    null, null, null, null);
        }

        public static RehearsalTarget teamRole(String teamRoleCode) {
            return new RehearsalTarget(TargetTypeCode.TEAM_ROLE, null, null, null, null, null, null, teamRoleCode,
                    null, null, null);
        }

        public static RehearsalTarget serviceAssignment(UUID serviceTeamAssignmentId) {
            return new RehearsalTarget(TargetTypeCode.MUSICIAN_ASSIGNMENT, null, null, null, null, null, null, null,
                    serviceTeamAssignmentId, null, null);
        }
    }

    public record RehearsalNoteRecord(
            UUID noteId,
            UUID servicePlanId,
            RehearsalTarget target,
            String noteBody,
            String visibilityCode,
            String createdBy,
            Instant createdAt) {
    }

    public record RehearsalIssueRecord(
            UUID issueId,
            UUID servicePlanId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            IssueStatusCode statusCode,
            String title,
            String detail,
            String detectedBy,
            Instant resolvedAt,
            Instant archivedAt) {

        public boolean open() {
            return statusCode == IssueStatusCode.OPEN || statusCode == IssueStatusCode.IN_PROGRESS;
        }

        public boolean blocking() {
            return open() && (severityCode == IssueSeverityCode.BLOCKING || categoryCode == IssueCategoryCode.BLOCKER);
        }
    }

    public record RehearsalIssueActionRecord(
            UUID actionId,
            UUID issueId,
            UUID servicePlanId,
            IssueActionStatusCode actionStatusCode,
            String actionSummary,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            Instant dueAt,
            Instant completedAt) {

        public RehearsalIssueActionRecord(
                UUID actionId,
                UUID issueId,
                UUID servicePlanId,
                IssueActionStatusCode actionStatusCode,
                String actionSummary,
                IssueOwnerType ownerType,
                String ownerActor,
                String ownerTeamRoleCode,
                UUID ownerServiceAssignmentId,
                Instant completedAt) {
            this(actionId, issueId, servicePlanId, actionStatusCode, actionSummary, ownerType, ownerActor,
                    ownerTeamRoleCode, ownerServiceAssignmentId, null, completedAt);
        }

        public boolean open() {
            return actionStatusCode == IssueActionStatusCode.TODO
                    || actionStatusCode == IssueActionStatusCode.IN_PROGRESS;
        }
    }

    public enum RehearsalWorkflowSummaryAudience {
        PUBLIC,
        WORSHIP_LEADER,
        ADMIN
    }

    public record RehearsalWorkflowSummarySession(
            UUID rehearsalSessionId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            ReadinessStateCode readinessStateCode) {
    }

    public record RehearsalWorkflowIssueCount(
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            int count) {
    }

    public record RehearsalWorkflowIssueActionIndicator(
            UUID actionId,
            IssueActionStatusCode actionStatusCode,
            String actionSummary,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            Instant dueAt,
            Instant completedAt,
            boolean open) {

        public RehearsalWorkflowIssueActionIndicator(
                UUID actionId,
                IssueActionStatusCode actionStatusCode,
                String actionSummary,
                IssueOwnerType ownerType,
                String ownerActor,
                String ownerTeamRoleCode,
                UUID ownerServiceAssignmentId,
                Instant completedAt,
                boolean open) {
            this(actionId, actionStatusCode, actionSummary, ownerType, ownerActor, ownerTeamRoleCode,
                    ownerServiceAssignmentId, null, completedAt, open);
        }
    }

    public record RehearsalWorkflowIssueIndicator(
            UUID issueId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            IssueStatusCode statusCode,
            boolean blocking,
            String title,
            String detail,
            String detectedBy,
            List<RehearsalWorkflowIssueActionIndicator> actions) {

        public RehearsalWorkflowIssueIndicator {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record RehearsalWorkflowSummary(
            UUID servicePlanId,
            ReadinessStateCode explicitStateCode,
            ReadinessStateCode derivedStateCode,
            boolean readyForService,
            String currentPhase,
            RehearsalWorkflowSummarySession nextRehearsalSession,
            RehearsalWorkflowSummarySession mostRecentPastRehearsalSession,
            List<RehearsalWorkflowSummarySession> rehearsalSessions,
            int blockerCount,
            int overdueActionCount,
            List<RehearsalWorkflowIssueCount> openIssueCounts,
            int unresolvedTransitionIssueCount,
            int difficultSongIssueCount,
            int serviceSpecificOverrideCount,
            boolean hasServiceSpecificOverrides,
            List<RehearsalWorkflowIssueIndicator> openIssues,
            boolean redacted) {

        public RehearsalWorkflowSummary {
            rehearsalSessions = rehearsalSessions == null
                    ? List.of()
                    : List.copyOf(rehearsalSessions);
            openIssueCounts = openIssueCounts == null
                    ? List.of()
                    : List.copyOf(openIssueCounts);
            openIssues = openIssues == null
                    ? List.of()
                    : List.copyOf(openIssues);
        }
    }

    public record RehearsalReportServiceRow(
            UUID servicePlanId,
            ReadinessStateCode explicitStateCode,
            ReadinessStateCode derivedStateCode,
            Instant serviceDateTime,
            int openBlockingIssueCount,
            int openRequiredActionCount,
            int openBlockerCount,
            int unresolvedTransitionIssueCount,
            int difficultSongIssueCount,
            int overdueOwnerActionCount,
            int activeOverrideCount) {
    }

    public record RehearsalReportIssueRow(
            UUID issueId,
            UUID servicePlanId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            IssueStatusCode statusCode,
            Instant createdAt,
            Instant resolvedAt) {
    }

    public record RehearsalReportActionRow(
            UUID actionId,
            UUID issueId,
            UUID servicePlanId,
            IssueActionStatusCode actionStatusCode,
            IssueOwnerType ownerType,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            Instant dueAt,
            Instant completedAt) {
    }

    public record RehearsalCompletedServiceHistoryRow(
            UUID servicePlanId,
            Instant serviceDateTime,
            Instant completedAt,
            int sessionCount,
            int archivedSessionCount,
            int issueCount,
            int resolvedIssueCount,
            int overrideCount,
            int auditEventCount) {
    }

    public record RehearsalRetentionConfiguration(
            int completedSessionsRetainDays,
            int notesRetainDays,
            int issuesRetainDays,
            int overridesRetainDays,
            int auditRetainDays,
            int minCompletedSessionsRetainDays,
            int minNotesRetainDays,
            int minIssuesRetainDays,
            int minOverridesRetainDays,
            int minAuditRetainDays) {

        public static RehearsalRetentionConfiguration defaults() {
            return new RehearsalRetentionConfiguration(400, 180, 400, 400, 2555, 90, 30, 180, 180, 2555);
        }
    }

    public record RehearsalRetentionArchiveResult(
            Instant archivedBefore,
            int archivedSessions,
            int archivedNotes,
            int archivedIssues,
            int archivedOverrides,
            int retainedAuditRecords) {
    }

    public record RehearsalAuditRecord(
            UUID auditId,
            String actor,
            Set<String> actorRoles,
            String actionCode,
            String targetType,
            UUID targetId,
            UUID servicePlanId,
            UUID rehearsalSessionId,
            Instant occurredAt,
            String reason,
            String reference,
            String beforeStateSnapshot,
            String afterStateSnapshot) {
    }

    public record ArrangementOverrideRecord(
            UUID arrangementOverrideId,
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId,
            UUID sourceArrangementId,
            String sourceArrangementVersionRef,
            String effectiveKey,
            String effectiveMode,
            Integer effectiveTempoBpm,
            String effectiveTimeSignature,
            Integer effectiveDurationSeconds,
            Integer effectiveEnergyLevel,
            Integer effectiveDifficultyLevel,
            String effectiveNotes,
            Integer capoFret,
            Integer transpositionSemitones,
            String chartAnnotations,
            String sectionOrderNotes,
            String transitionCues,
            String instrumentationNotes,
            String assetSelectionNotes,
            String rationale,
            String provenanceNote,
            String createdBy,
            String updatedBy) {

        public ArrangementOverrideRecord(
                UUID arrangementOverrideId,
                UUID servicePlanId,
                UUID servicePlanBlockId,
                UUID setlistVersionItemId,
                UUID sourceArrangementId,
                String sourceArrangementVersionRef,
                String effectiveKey,
                String effectiveMode,
                Integer effectiveTempoBpm,
                String effectiveTimeSignature,
                Integer effectiveDurationSeconds,
                Integer effectiveEnergyLevel,
                Integer effectiveDifficultyLevel,
                String effectiveNotes,
                String rationale,
                String provenanceNote,
                String createdBy,
                String updatedBy) {
            this(arrangementOverrideId, servicePlanId, servicePlanBlockId, setlistVersionItemId, sourceArrangementId,
                    sourceArrangementVersionRef, effectiveKey, effectiveMode, effectiveTempoBpm, effectiveTimeSignature,
                    effectiveDurationSeconds, effectiveEnergyLevel, effectiveDifficultyLevel, effectiveNotes,
                    null, null, null, null, null, null, null, rationale, provenanceNote, createdBy, updatedBy);
        }
    }

    public enum EffectiveArrangementValueSource {
        CATALOG,
        SERVICE_OVERRIDE
    }

    public record EffectiveArrangementValue<T>(
            T sourceValue,
            T overrideValue,
            T effectiveValue,
            EffectiveArrangementValueSource valueSource) {

        public static <T> EffectiveArrangementValue<T> from(T sourceValue, T overrideValue) {
            return new EffectiveArrangementValue<>(sourceValue, overrideValue,
                    overrideValue == null ? sourceValue : overrideValue,
                    overrideValue == null ? EffectiveArrangementValueSource.CATALOG
                            : EffectiveArrangementValueSource.SERVICE_OVERRIDE);
        }
    }

    public record EffectiveArrangementProvenance(
            UUID sourceArrangementId,
            String sourceArrangementVersionRef,
            UUID arrangementOverrideId,
            String provenanceNote,
            String rationale,
            String createdBy,
            String updatedBy,
            String auditReference) {
    }

    public record EffectiveArrangementRendering(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId,
            UUID sourceArrangementId,
            String arrangementName,
            EffectiveArrangementValue<String> musicalKey,
            EffectiveArrangementValue<String> keyMode,
            EffectiveArrangementValue<Integer> tempoBpm,
            EffectiveArrangementValue<String> timeSignature,
            EffectiveArrangementValue<Integer> durationSeconds,
            EffectiveArrangementValue<Integer> energyLevel,
            EffectiveArrangementValue<Integer> difficultyLevel,
            EffectiveArrangementValue<String> rehearsalNotes,
            EffectiveArrangementValue<Integer> capoFret,
            EffectiveArrangementValue<Integer> transpositionSemitones,
            EffectiveArrangementValue<String> chartAnnotations,
            EffectiveArrangementValue<String> sectionOrderNotes,
            EffectiveArrangementValue<String> transitionCues,
            EffectiveArrangementValue<String> instrumentationNotes,
            EffectiveArrangementValue<String> assetSelectionNotes,
            int renderedTranspositionInterval,
            String transpositionSource,
            String renderedLyricsContent,
            String renderedChordMapJson,
            EffectiveArrangementProvenance provenance) {

        public boolean hasServiceOverride() {
            return provenance.arrangementOverrideId() != null;
        }
    }
}
