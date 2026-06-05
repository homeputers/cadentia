package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.model.CreateRehearsalSessionRequest;
import com.cadentia.generated.model.RehearsalIssueActionStatusCode;
import com.cadentia.generated.model.RehearsalIssueOwnerType;
import com.cadentia.generated.model.RehearsalIssueStatusCode;
import com.cadentia.generated.model.RehearsalNoteVisibilityCode;
import com.cadentia.generated.model.RehearsalReadinessStateCode;
import com.cadentia.generated.model.RehearsalTargetTypeCode;
import com.cadentia.generated.model.UpdateIssueActionStatusRequest;
import com.cadentia.generated.model.UpdateIssueStatusRequest;
import com.cadentia.rehearsal.RehearsalWorkflowAuthorizationPolicy;
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
import com.cadentia.rehearsal.RehearsalWorkflowRepository;
import com.cadentia.rehearsal.RehearsalWorkflowService;
import java.time.Instant;
import java.time.OffsetDateTime;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class RehearsalWorkflowControllerTest {

    private FakeRepository repository;
    private RehearsalWorkflowController controller;
    private UUID servicePlanId;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        controller = new RehearsalWorkflowController(
                new RehearsalWorkflowService(repository, new RehearsalWorkflowAuthorizationPolicy()));
        servicePlanId = UUID.randomUUID();
        authenticate(RbacAuthorities.ROLE_WORSHIP_LEADER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsWorkflowStatusWithStableReadinessCodes() {
        // Arrange
        repository.readiness.put(servicePlanId, ReadinessStateCode.REHEARSING);
        repository.createIssue(servicePlanId, RehearsalTarget.service(), IssueCategoryCode.BLOCKER,
                IssueSeverityCode.BLOCKING, IssueStatusCode.OPEN, "Blocker", "Detail", "leader");

        // Act
        var response = controller.getServiceWorkflowStatus(servicePlanId).getBody();

        // Assert
        assertThat(response.getExplicitStateCode()).isEqualTo(RehearsalReadinessStateCode.REHEARSING);
        assertThat(response.getDerivedStateCode()).isEqualTo(RehearsalReadinessStateCode.REHEARSING);
        assertThat(response.getOpenBlockingIssueCount()).isEqualTo(1);
    }

    @Test
    void assignedMusicianCanUpdateActionStatusWithoutOwnerOrWorkflowMutationPayload() {
        // Arrange
        UUID issueId = repository.createIssue(servicePlanId, RehearsalTarget.service(), IssueCategoryCode.GENERAL_FOLLOW_UP,
                IssueSeverityCode.MEDIUM, IssueStatusCode.OPEN, "Cue", "Confirm capo", "leader").issueId();
        UUID actionId = repository.addIssueAction(servicePlanId, issueId, IssueActionStatusCode.TODO, "Confirm capo",
                IssueOwnerType.SERVICE_ASSIGNMENT, null, null, UUID.randomUUID(), "leader").actionId();
        authenticate(RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act
        var response = controller.updateRehearsalIssueActionStatus(servicePlanId, actionId,
                new UpdateIssueActionStatusRequest(RehearsalIssueActionStatusCode.DONE)
                        .reason("completed")
                        .reference("mobile")).getBody();

        // Assert
        assertThat(response.getActionStatusCode()).isEqualTo(RehearsalIssueActionStatusCode.DONE);
        assertThat(response.getOwner().getOwnerType()).isEqualTo(RehearsalIssueOwnerType.SERVICE_ASSIGNMENT);
    }

    @Test
    void redactsPrivateNotesForReportingUsers() {
        // Arrange
        repository.addNote(servicePlanId, RehearsalTarget.service(), "Pastoral care context",
                "pastoral_private", "pastor");
        authenticate(RbacAuthorities.ROLE_REPORTING_VIEWER);

        // Act
        var response = controller.listRehearsalNotes(servicePlanId).getBody();

        // Assert
        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getRedacted()).isTrue();
        assertThat(response.getFirst().getNoteBody()).isNull();
        assertThat(response.getFirst().getCreatedBy()).isNull();
        assertThat(response.getFirst().getVisibilityCode()).isEqualTo(RehearsalNoteVisibilityCode.PASTORAL_PRIVATE);
    }

    @Test
    void rejectsStaleExpectedVersionBeforeMutatingIssueStatus() {
        // Arrange
        UUID issueId = repository.createIssue(servicePlanId, RehearsalTarget.service(), IssueCategoryCode.GENERAL_FOLLOW_UP,
                IssueSeverityCode.LOW, IssueStatusCode.OPEN, "Follow up", "Detail", "leader").issueId();

        // Act / Assert
        assertThatThrownBy(() -> controller.updateRehearsalIssueStatus(servicePlanId, issueId,
                new UpdateIssueStatusRequest(RehearsalIssueStatusCode.RESOLVED).expectedVersion(99L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VERSION_CONFLICT");
    }

    @Test
    void mapsIssueActionsIntoIssueResponses() {
        // Arrange
        UUID issueId = repository.createIssue(servicePlanId, RehearsalTarget.session(UUID.randomUUID()),
                IssueCategoryCode.DIFFICULT_SONG, IssueSeverityCode.HIGH, IssueStatusCode.OPEN,
                "Ending", "Needs clean stop", "leader").issueId();
        UUID actionId = repository.addIssueAction(servicePlanId, issueId, IssueActionStatusCode.TODO, "Practice ending",
                IssueOwnerType.TEAM_ROLE, null, "DRUMS", null, "leader").actionId();

        // Act
        var response = controller.listRehearsalIssues(servicePlanId).getBody();

        // Assert
        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getTarget().getTargetTypeCode()).isEqualTo(RehearsalTargetTypeCode.REHEARSAL_SESSION);
        assertThat(response.getFirst().getActions()).hasSize(1);
        assertThat(response.getFirst().getActions().getFirst().getActionId()).isEqualTo(actionId);
    }

    @Test
    void exposesMachineReadableProblemForInvalidWorkflowPayloads() {
        // Arrange
        RehearsalWorkflowExceptionHandler handler = new RehearsalWorkflowExceptionHandler();

        // Act
        var response = handler.handleValidation(new IllegalArgumentException("bogus status"));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode().getValue()).isEqualTo("INVALID_STATUS_CODE");
    }

    @Test
    void mapsSessionCreateResponseWithoutExposingPersistenceEntities() {
        // Act
        var response = controller.createRehearsalSession(servicePlanId, new CreateRehearsalSessionRequest(
                "midweek", OffsetDateTime.parse("2026-06-05T23:00:00Z"),
                OffsetDateTime.parse("2026-06-06T01:00:00Z"))
                .location("Sanctuary")
                .reason("scheduled")
                .reference("planner")).getBody();

        // Assert
        assertThat(response.getRehearsalSessionId()).isNotNull();
        assertThat(response.getReadinessStateCode()).isEqualTo(RehearsalReadinessStateCode.DRAFT);
        assertThat(response.getVersion()).isZero();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("caller", "n/a", role));
    }

    private static class FakeRepository implements RehearsalWorkflowRepository {
        private final Map<UUID, ReadinessStateCode> readiness = new LinkedHashMap<>();
        private final Map<UUID, RehearsalSessionRecord> sessions = new LinkedHashMap<>();
        private final Map<UUID, RehearsalNoteRecord> notes = new LinkedHashMap<>();
        private final Map<UUID, RehearsalIssueRecord> issues = new LinkedHashMap<>();
        private final Map<UUID, RehearsalIssueActionRecord> actions = new LinkedHashMap<>();

        public List<ControlledVocabularyEntry> listReadinessStates() { return List.of(); }
        public List<ControlledVocabularyEntry> listIssueCategories() { return List.of(); }
        public List<ControlledVocabularyEntry> listIssueStatuses() { return List.of(); }
        public Optional<ReadinessStateCode> findServiceReadiness(UUID servicePlanId) { return Optional.ofNullable(readiness.get(servicePlanId)); }
        public List<RehearsalSessionRecord> listSessions(UUID servicePlanId) { return sessions.values().stream().filter(s -> s.servicePlanId().equals(servicePlanId)).toList(); }
        public List<RehearsalNoteRecord> listNotes(UUID servicePlanId) { return notes.values().stream().filter(n -> n.servicePlanId().equals(servicePlanId)).toList(); }
        public List<ArrangementOverrideRecord> listArrangementOverrides(UUID servicePlanId) { return List.of(); }
        public RehearsalSessionRecord createSession(UUID servicePlanId, String sessionCode, Instant startsAt, Instant endsAt, String location, String createdBy) {
            RehearsalSessionRecord session = new RehearsalSessionRecord(UUID.randomUUID(), servicePlanId, sessionCode, startsAt, endsAt, location, ReadinessStateCode.DRAFT, null);
            sessions.put(session.rehearsalSessionId(), session);
            return session;
        }
        public RehearsalSessionRecord updateSession(UUID servicePlanId, UUID rehearsalSessionId, String sessionCode, Instant startsAt, Instant endsAt, String location, String updatedBy) { return createSession(servicePlanId, sessionCode, startsAt, endsAt, location, updatedBy); }
        public RehearsalSessionRecord archiveSession(UUID servicePlanId, UUID rehearsalSessionId, String archivedBy) { return sessions.get(rehearsalSessionId); }
        public void recordServiceReadiness(UUID servicePlanId, UUID rehearsalSessionId, ReadinessStateCode newStateCode, String rationale, String changedBy) { readiness.put(servicePlanId, newStateCode); }
        public RehearsalNoteRecord addNote(UUID servicePlanId, RehearsalTarget target, String noteBody, String visibilityCode, String createdBy) {
            RehearsalNoteRecord note = new RehearsalNoteRecord(UUID.randomUUID(), servicePlanId, target, noteBody, visibilityCode, createdBy, Instant.now());
            notes.put(note.noteId(), note);
            return note;
        }
        public Optional<RehearsalIssueRecord> findIssue(UUID servicePlanId, UUID issueId) { return Optional.ofNullable(issues.get(issueId)); }
        public List<RehearsalIssueRecord> listIssues(UUID servicePlanId) { return issues.values().stream().filter(i -> i.servicePlanId().equals(servicePlanId)).toList(); }
        public List<RehearsalIssueActionRecord> listIssueActions(UUID servicePlanId) { return actions.values().stream().filter(a -> a.servicePlanId().equals(servicePlanId)).toList(); }
        public RehearsalIssueRecord createIssue(UUID servicePlanId, RehearsalTarget target, IssueCategoryCode categoryCode, IssueSeverityCode severityCode, IssueStatusCode statusCode, String title, String detail, String detectedBy) {
            RehearsalIssueRecord issue = new RehearsalIssueRecord(UUID.randomUUID(), servicePlanId, target, categoryCode, severityCode, statusCode, title, detail, detectedBy, null, null);
            issues.put(issue.issueId(), issue);
            return issue;
        }
        public RehearsalIssueRecord updateIssueSeverity(UUID servicePlanId, UUID issueId, IssueSeverityCode severityCode, String updatedBy) { return issues.get(issueId); }
        public RehearsalIssueRecord updateIssueStatus(UUID servicePlanId, UUID issueId, IssueStatusCode statusCode, String updatedBy) {
            RehearsalIssueRecord issue = issues.get(issueId);
            RehearsalIssueRecord updated = new RehearsalIssueRecord(issue.issueId(), issue.servicePlanId(), issue.target(), issue.categoryCode(), issue.severityCode(), statusCode, issue.title(), issue.detail(), issue.detectedBy(), null, issue.archivedAt());
            issues.put(issueId, updated);
            return updated;
        }
        public RehearsalIssueActionRecord addIssueAction(UUID servicePlanId, UUID issueId, IssueActionStatusCode statusCode, String actionSummary, IssueOwnerType ownerType, String ownerActor, String ownerTeamRoleCode, UUID ownerServiceAssignmentId, String createdBy) {
            RehearsalIssueActionRecord action = new RehearsalIssueActionRecord(UUID.randomUUID(), issueId, servicePlanId, statusCode, actionSummary, ownerType, ownerActor, ownerTeamRoleCode, ownerServiceAssignmentId, null);
            actions.put(action.actionId(), action);
            return action;
        }
        public RehearsalIssueActionRecord updateIssueActionOwner(UUID servicePlanId, UUID actionId, IssueOwnerType ownerType, String ownerActor, String ownerTeamRoleCode, UUID ownerServiceAssignmentId, String updatedBy) { return actions.get(actionId); }
        public RehearsalIssueActionRecord updateIssueActionStatus(UUID servicePlanId, UUID actionId, IssueActionStatusCode statusCode, String updatedBy) {
            RehearsalIssueActionRecord action = actions.get(actionId);
            RehearsalIssueActionRecord updated = new RehearsalIssueActionRecord(action.actionId(), action.issueId(), action.servicePlanId(), statusCode, action.actionSummary(), action.ownerType(), action.ownerActor(), action.ownerTeamRoleCode(), action.ownerServiceAssignmentId(), Instant.now());
            actions.put(actionId, updated);
            return updated;
        }
        public ArrangementOverrideRecord createArrangementOverride(ArrangementOverrideRecord overrideRecord) { return overrideRecord; }
        public ArrangementOverrideRecord updateArrangementOverride(ArrangementOverrideRecord overrideRecord) { return overrideRecord; }
        public void archiveArrangementOverride(UUID servicePlanId, UUID arrangementOverrideId, String archivedBy) {}
        public RehearsalAuditRecord recordAudit(RehearsalAuditRecord auditRecord) { return auditRecord; }
    }
}
