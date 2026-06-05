package com.cadentia.rehearsal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ControlledVocabularyEntry;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalAuditRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalNoteRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RehearsalWorkflowServiceTest {

    private FakeRehearsalWorkflowRepository repository;
    private RehearsalWorkflowService service;
    private UUID servicePlanId;

    @BeforeEach
    void setUp() {
        repository = new FakeRehearsalWorkflowRepository();
        service = new RehearsalWorkflowService(repository, new RehearsalWorkflowAuthorizationPolicy());
        servicePlanId = UUID.randomUUID();
        authenticate("planner", RbacAuthorities.ROLE_WORSHIP_LEADER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managesIssueLifecycleAndBlocksReadyUntilBlockerAndRequiredActionAreClosed() {
        // Arrange
        UUID sessionId = createSessionAndMoveToRehearsing();
        RehearsalIssueRecord issue = service.openIssue(
                servicePlanId,
                RehearsalTarget.session(sessionId),
                IssueCategoryCode.UNRESOLVED_TRANSITION,
                IssueSeverityCode.HIGH,
                "Bridge transition",
                "Count-in needed for bridge.",
                "rehearsal finding",
                "runbook:adr-024");
        RehearsalIssueActionRecord action = service.assignAction(
                servicePlanId,
                issue.issueId(),
                "Publish cue sheet",
                IssueOwnerType.TEAM_ROLE,
                null,
                "MUSIC_DIRECTOR",
                null,
                "owner assignment",
                "ticket-1");
        service.markBlocker(servicePlanId, issue.issueId(), "blocks ready", "ticket-2");

        // Act / Assert
        assertThatThrownBy(() -> service.requestReadinessTransition(
                        servicePlanId, sessionId, ReadinessStateCode.READY, "ready check", "ticket-3"))
                .isInstanceOf(RehearsalWorkflowException.class)
                .hasMessageContaining("unresolved blocking issues");

        service.changeActionStatus(servicePlanId, action.actionId(), IssueActionStatusCode.DONE, "cue published", "ticket-4");
        service.resolveIssue(servicePlanId, issue.issueId(), "transition rehearsed", "ticket-5");
        WorkflowStatus ready = service.requestReadinessTransition(
                servicePlanId, sessionId, ReadinessStateCode.READY, "all blockers closed", "ticket-6");

        assertThat(ready.explicitStateCode()).isEqualTo(ReadinessStateCode.READY);
        assertThat(ready.derivedStateCode()).isEqualTo(ReadinessStateCode.READY);
        assertThat(ready.openBlockingIssueCount()).isZero();
        assertThat(ready.openRequiredActionCount()).isZero();
        assertThat(repository.audits).extracting(RehearsalAuditRecord::actionCode)
                .contains(
                        "REHEARSAL_ISSUE_OPENED",
                        "REHEARSAL_ACTION_ASSIGNED",
                        "REHEARSAL_ISSUE_BLOCKER_MARKED",
                        "REHEARSAL_ACTION_STATUS_CHANGED",
                        "REHEARSAL_ISSUE_STATUS_CHANGED",
                        "REHEARSAL_READINESS_CHANGED");
        assertThat(repository.audits.getFirst().actor()).isEqualTo("planner");
        assertThat(repository.audits.getFirst().servicePlanId()).isEqualTo(servicePlanId);
        assertThat(repository.audits.getFirst().afterStateSnapshot()).doesNotContain("Count-in needed");
    }

    @Test
    void reopeningABlockingIssueDerivesIssuesOpenEvenWhenExplicitStateWasReady() {
        // Arrange
        UUID sessionId = createSessionAndMoveToRehearsing();
        RehearsalIssueRecord issue = service.openIssue(
                servicePlanId,
                RehearsalTarget.session(sessionId),
                IssueCategoryCode.BLOCKER,
                IssueSeverityCode.BLOCKING,
                "Leader unavailable",
                "Find alternate leader.",
                "blocked",
                "ticket-7");
        service.resolveIssue(servicePlanId, issue.issueId(), "leader confirmed", "ticket-8");
        service.requestReadinessTransition(servicePlanId, sessionId, ReadinessStateCode.READY, "ready", "ticket-9");

        // Act
        service.reopenIssue(servicePlanId, issue.issueId(), "leader sick", "ticket-10");
        WorkflowStatus status = service.workflowStatus(servicePlanId);

        // Assert
        assertThat(status.explicitStateCode()).isEqualTo(ReadinessStateCode.ISSUES_OPEN);
        assertThat(status.derivedStateCode()).isEqualTo(ReadinessStateCode.ISSUES_OPEN);
        assertThat(status.openBlockingIssueCount()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidReadinessTransitionsAndOpenBlockersOnCompletion() {
        // Arrange
        UUID sessionId = createSessionAndMoveToRehearsing();
        service.openIssue(
                servicePlanId,
                RehearsalTarget.session(sessionId),
                IssueCategoryCode.BLOCKER,
                IssueSeverityCode.BLOCKING,
                "Sound console broken",
                "Console must be restored.",
                "blocked",
                "ticket-11");

        // Act / Assert
        assertThatThrownBy(() -> service.requestReadinessTransition(
                        servicePlanId, sessionId, ReadinessStateCode.COMPLETED, "skip ahead", "ticket-12"))
                .isInstanceOf(RehearsalWorkflowException.class)
                .hasMessageContaining("not permitted");
    }

    @Test
    void deniesUnauthorizedWorkflowChangesBeforeWritingAudit() {
        // Arrange
        authenticate("viewer", RbacAuthorities.ROLE_REPORTING_VIEWER);

        // Act / Assert
        assertThatThrownBy(() -> service.openIssue(
                        servicePlanId,
                        RehearsalTarget.service(),
                        IssueCategoryCode.GENERAL_FOLLOW_UP,
                        IssueSeverityCode.LOW,
                        "Need follow up",
                        "Private detail",
                        "not allowed",
                        "ticket-13"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(repository.issues).isEmpty();
        assertThat(repository.audits).isEmpty();
    }

    @Test
    void recordsEmergencyCorrectionAuditForManualOverride() {
        // Arrange
        repository.readiness.put(servicePlanId, ReadinessStateCode.COMPLETED);
        authenticate("admin", RbacAuthorities.ROLE_ADMIN);

        // Act
        WorkflowStatus status = service.emergencyCorrectReadiness(
                servicePlanId, null, ReadinessStateCode.REHEARSING, "incorrect closure", "incident-1");

        // Assert
        assertThat(status.explicitStateCode()).isEqualTo(ReadinessStateCode.REHEARSING);
        assertThat(repository.audits).extracting(RehearsalAuditRecord::actionCode)
                .contains("REHEARSAL_READINESS_EMERGENCY_CORRECTED");
    }

    private UUID createSessionAndMoveToRehearsing() {
        RehearsalSessionRecord session = service.createSession(
                servicePlanId,
                "primary",
                Instant.parse("2026-06-05T00:00:00Z"),
                Instant.parse("2026-06-05T02:00:00Z"),
                "Sanctuary",
                "setup",
                "schedule");
        service.requestReadinessTransition(servicePlanId, session.rehearsalSessionId(), ReadinessStateCode.PLANNED,
                "planned", "schedule");
        service.requestReadinessTransition(servicePlanId, session.rehearsalSessionId(), ReadinessStateCode.REHEARSING,
                "started", "schedule");
        return session.rehearsalSessionId();
    }

    private void authenticate(String name, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(name, "n/a", authorities));
    }

    private static class FakeRehearsalWorkflowRepository implements RehearsalWorkflowRepository {

        private final Map<UUID, ReadinessStateCode> readiness = new LinkedHashMap<>();
        private final Map<UUID, RehearsalIssueRecord> issues = new LinkedHashMap<>();
        private final Map<UUID, RehearsalIssueActionRecord> actions = new LinkedHashMap<>();
        private final Map<UUID, ArrangementOverrideRecord> overrides = new LinkedHashMap<>();
        private final List<RehearsalAuditRecord> audits = new ArrayList<>();

        @Override
        public List<ControlledVocabularyEntry> listReadinessStates() {
            return List.of();
        }

        @Override
        public List<ControlledVocabularyEntry> listIssueCategories() {
            return List.of();
        }

        @Override
        public List<ControlledVocabularyEntry> listIssueStatuses() {
            return List.of();
        }

        @Override
        public Optional<ReadinessStateCode> findServiceReadiness(UUID servicePlanId) {
            return Optional.ofNullable(readiness.get(servicePlanId));
        }

        @Override
        public List<RehearsalSessionRecord> listSessions(UUID servicePlanId) {
            return List.of();
        }

        @Override
        public List<RehearsalNoteRecord> listNotes(UUID servicePlanId) {
            return List.of();
        }

        @Override
        public List<ArrangementOverrideRecord> listArrangementOverrides(UUID servicePlanId) {
            return overrides.values().stream().filter(overrideRecord -> overrideRecord.servicePlanId().equals(servicePlanId)).toList();
        }

        @Override
        public RehearsalSessionRecord createSession(
                UUID servicePlanId, String sessionCode, Instant startsAt, Instant endsAt, String location, String createdBy) {
            return new RehearsalSessionRecord(
                    UUID.randomUUID(), servicePlanId, sessionCode, startsAt, endsAt, location, ReadinessStateCode.DRAFT, null);
        }

        @Override
        public RehearsalSessionRecord updateSession(
                UUID servicePlanId,
                UUID rehearsalSessionId,
                String sessionCode,
                Instant startsAt,
                Instant endsAt,
                String location,
                String updatedBy) {
            return new RehearsalSessionRecord(
                    rehearsalSessionId, servicePlanId, sessionCode, startsAt, endsAt, location, ReadinessStateCode.DRAFT, null);
        }

        @Override
        public RehearsalSessionRecord archiveSession(UUID servicePlanId, UUID rehearsalSessionId, String archivedBy) {
            return new RehearsalSessionRecord(
                    rehearsalSessionId, servicePlanId, "primary", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), null,
                    ReadinessStateCode.DRAFT, Instant.now());
        }

        @Override
        public void recordServiceReadiness(
                UUID servicePlanId,
                UUID rehearsalSessionId,
                ReadinessStateCode newStateCode,
                String rationale,
                String changedBy) {
            readiness.put(servicePlanId, newStateCode);
        }

        @Override
        public RehearsalNoteRecord addNote(
                UUID servicePlanId, RehearsalTarget target, String noteBody, String visibilityCode, String createdBy) {
            return new RehearsalNoteRecord(UUID.randomUUID(), servicePlanId, target, noteBody, visibilityCode, createdBy, Instant.now());
        }

        @Override
        public Optional<RehearsalIssueRecord> findIssue(UUID servicePlanId, UUID issueId) {
            return Optional.ofNullable(issues.get(issueId));
        }

        @Override
        public List<RehearsalIssueRecord> listIssues(UUID servicePlanId) {
            return issues.values().stream().filter(issue -> issue.servicePlanId().equals(servicePlanId)).toList();
        }

        @Override
        public List<RehearsalIssueActionRecord> listIssueActions(UUID servicePlanId) {
            return actions.values().stream().filter(action -> action.servicePlanId().equals(servicePlanId)).toList();
        }

        @Override
        public RehearsalIssueRecord createIssue(
                UUID servicePlanId,
                RehearsalTarget target,
                IssueCategoryCode categoryCode,
                IssueSeverityCode severityCode,
                IssueStatusCode statusCode,
                String title,
                String detail,
                String detectedBy) {
            RehearsalIssueRecord issue = new RehearsalIssueRecord(
                    UUID.randomUUID(), servicePlanId, target, categoryCode, severityCode, statusCode, title, detail,
                    detectedBy, null, null);
            issues.put(issue.issueId(), issue);
            return issue;
        }

        @Override
        public RehearsalIssueRecord updateIssueSeverity(
                UUID servicePlanId, UUID issueId, IssueSeverityCode severityCode, String updatedBy) {
            RehearsalIssueRecord issue = issues.get(issueId);
            RehearsalIssueRecord updated = new RehearsalIssueRecord(
                    issue.issueId(), issue.servicePlanId(), issue.target(), issue.categoryCode(), severityCode,
                    issue.statusCode(), issue.title(), issue.detail(), issue.detectedBy(), issue.resolvedAt(), issue.archivedAt());
            issues.put(issueId, updated);
            return updated;
        }

        @Override
        public RehearsalIssueRecord updateIssueStatus(
                UUID servicePlanId, UUID issueId, IssueStatusCode statusCode, String updatedBy) {
            RehearsalIssueRecord issue = issues.get(issueId);
            Instant resolvedAt = statusCode == IssueStatusCode.RESOLVED || statusCode == IssueStatusCode.DEFERRED
                    || statusCode == IssueStatusCode.CANCELLED ? Instant.now() : null;
            RehearsalIssueRecord updated = new RehearsalIssueRecord(
                    issue.issueId(), issue.servicePlanId(), issue.target(), issue.categoryCode(), issue.severityCode(),
                    statusCode, issue.title(), issue.detail(), issue.detectedBy(), resolvedAt, issue.archivedAt());
            issues.put(issueId, updated);
            return updated;
        }

        @Override
        public RehearsalIssueActionRecord addIssueAction(
                UUID servicePlanId,
                UUID issueId,
                IssueActionStatusCode statusCode,
                String actionSummary,
                IssueOwnerType ownerType,
                String ownerActor,
                String ownerTeamRoleCode,
                UUID ownerServiceAssignmentId,
                String createdBy) {
            RehearsalIssueActionRecord action = new RehearsalIssueActionRecord(
                    UUID.randomUUID(), issueId, servicePlanId, statusCode, actionSummary, ownerType, ownerActor,
                    ownerTeamRoleCode, ownerServiceAssignmentId, null);
            actions.put(action.actionId(), action);
            return action;
        }

        @Override
        public RehearsalIssueActionRecord updateIssueActionOwner(
                UUID servicePlanId,
                UUID actionId,
                IssueOwnerType ownerType,
                String ownerActor,
                String ownerTeamRoleCode,
                UUID ownerServiceAssignmentId,
                String updatedBy) {
            RehearsalIssueActionRecord action = actions.get(actionId);
            RehearsalIssueActionRecord updated = new RehearsalIssueActionRecord(
                    action.actionId(), action.issueId(), action.servicePlanId(), action.actionStatusCode(),
                    action.actionSummary(), ownerType, ownerActor, ownerTeamRoleCode, ownerServiceAssignmentId,
                    action.completedAt());
            actions.put(actionId, updated);
            return updated;
        }

        @Override
        public RehearsalIssueActionRecord updateIssueActionStatus(
                UUID servicePlanId, UUID actionId, IssueActionStatusCode statusCode, String updatedBy) {
            RehearsalIssueActionRecord action = actions.get(actionId);
            RehearsalIssueActionRecord updated = new RehearsalIssueActionRecord(
                    action.actionId(), action.issueId(), action.servicePlanId(), statusCode, action.actionSummary(),
                    action.ownerType(), action.ownerActor(), action.ownerTeamRoleCode(), action.ownerServiceAssignmentId(),
                    statusCode == IssueActionStatusCode.DONE || statusCode == IssueActionStatusCode.CANCELLED
                            ? Instant.now() : null);
            actions.put(actionId, updated);
            return updated;
        }

        @Override
        public ArrangementOverrideRecord createArrangementOverride(ArrangementOverrideRecord overrideRecord) {
            ArrangementOverrideRecord created = new ArrangementOverrideRecord(
                    overrideRecord.arrangementOverrideId() == null ? UUID.randomUUID() : overrideRecord.arrangementOverrideId(),
                    overrideRecord.servicePlanId(), overrideRecord.servicePlanBlockId(), overrideRecord.setlistVersionItemId(),
                    overrideRecord.sourceArrangementId(), overrideRecord.sourceArrangementVersionRef(), overrideRecord.effectiveKey(),
                    overrideRecord.effectiveMode(), overrideRecord.effectiveTempoBpm(), overrideRecord.effectiveTimeSignature(),
                    overrideRecord.effectiveDurationSeconds(), overrideRecord.effectiveEnergyLevel(),
                    overrideRecord.effectiveDifficultyLevel(), overrideRecord.effectiveNotes(), overrideRecord.rationale(),
                    overrideRecord.provenanceNote(), overrideRecord.createdBy(), overrideRecord.updatedBy());
            overrides.put(created.arrangementOverrideId(), created);
            return created;
        }

        @Override
        public ArrangementOverrideRecord updateArrangementOverride(ArrangementOverrideRecord overrideRecord) {
            overrides.put(overrideRecord.arrangementOverrideId(), overrideRecord);
            return overrideRecord;
        }

        @Override
        public void archiveArrangementOverride(UUID servicePlanId, UUID arrangementOverrideId, String archivedBy) {
            overrides.remove(arrangementOverrideId);
        }

        @Override
        public RehearsalAuditRecord recordAudit(RehearsalAuditRecord auditRecord) {
            RehearsalAuditRecord recorded = new RehearsalAuditRecord(
                    UUID.randomUUID(), auditRecord.actor(), Set.copyOf(auditRecord.actorRoles()), auditRecord.actionCode(),
                    auditRecord.targetType(), auditRecord.targetId(), auditRecord.servicePlanId(),
                    auditRecord.rehearsalSessionId(), auditRecord.occurredAt(), auditRecord.reason(), auditRecord.reference(),
                    auditRecord.beforeStateSnapshot(), auditRecord.afterStateSnapshot());
            audits.add(recorded);
            return recorded;
        }
    }
}
