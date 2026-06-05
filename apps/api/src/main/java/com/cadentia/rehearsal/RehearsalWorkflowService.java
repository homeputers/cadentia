package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RehearsalWorkflowService {

    private static final Map<ReadinessStateCode, Set<ReadinessStateCode>> READINESS_TRANSITIONS =
            new EnumMap<>(ReadinessStateCode.class);

    static {
        READINESS_TRANSITIONS.put(ReadinessStateCode.DRAFT, EnumSet.of(ReadinessStateCode.PLANNED));
        READINESS_TRANSITIONS.put(ReadinessStateCode.PLANNED,
                EnumSet.of(ReadinessStateCode.DRAFT, ReadinessStateCode.REHEARSING));
        READINESS_TRANSITIONS.put(ReadinessStateCode.REHEARSING,
                EnumSet.of(ReadinessStateCode.PLANNED, ReadinessStateCode.ISSUES_OPEN, ReadinessStateCode.READY));
        READINESS_TRANSITIONS.put(ReadinessStateCode.ISSUES_OPEN,
                EnumSet.of(ReadinessStateCode.REHEARSING, ReadinessStateCode.READY));
        READINESS_TRANSITIONS.put(ReadinessStateCode.READY,
                EnumSet.of(ReadinessStateCode.REHEARSING, ReadinessStateCode.ISSUES_OPEN, ReadinessStateCode.COMPLETED));
        READINESS_TRANSITIONS.put(ReadinessStateCode.COMPLETED, EnumSet.noneOf(ReadinessStateCode.class));
    }

    private final RehearsalWorkflowRepository repository;
    private final RehearsalWorkflowAuthorizationPolicy authorizationPolicy;

    public RehearsalWorkflowService(
            RehearsalWorkflowRepository repository,
            RehearsalWorkflowAuthorizationPolicy authorizationPolicy) {
        this.repository = repository;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Transactional
    public RehearsalSessionRecord createSession(
            UUID servicePlanId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        String actor = authorizationPolicy.currentActor();
        RehearsalSessionRecord session = repository.createSession(
                servicePlanId, sessionCode, startsAt, endsAt, location, actor);
        audit("REHEARSAL_SESSION_CREATED", "rehearsal_session", session.rehearsalSessionId(), servicePlanId,
                session.rehearsalSessionId(), null, sessionSnapshot(session), reason, reference);
        return session;
    }

    @Transactional
    public RehearsalSessionRecord updateSession(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            String sessionCode,
            Instant startsAt,
            Instant endsAt,
            String location,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        String actor = authorizationPolicy.currentActor();
        RehearsalSessionRecord updated = repository.updateSession(
                servicePlanId, rehearsalSessionId, sessionCode, startsAt, endsAt, location, actor);
        audit("REHEARSAL_SESSION_UPDATED", "rehearsal_session", rehearsalSessionId, servicePlanId, rehearsalSessionId,
                null, sessionSnapshot(updated), reason, reference);
        return updated;
    }

    @Transactional
    public RehearsalNoteRecord addNote(
            UUID servicePlanId,
            RehearsalTarget target,
            String noteBody,
            String visibilityCode,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        RehearsalNoteRecord note = repository.addNote(
                servicePlanId, target, noteBody, visibilityCode, authorizationPolicy.currentActor());
        audit("REHEARSAL_NOTE_ADDED", "rehearsal_note", note.noteId(), servicePlanId, target.rehearsalSessionId(),
                null, "{\"noteId\":\"" + note.noteId() + "\",\"targetType\":\"" + target.targetTypeCode().code()
                        + "\"}", reason, reference);
        return note;
    }

    @Transactional
    public RehearsalIssueRecord openIssue(
            UUID servicePlanId,
            RehearsalTarget target,
            IssueCategoryCode categoryCode,
            IssueSeverityCode severityCode,
            String title,
            String detail,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        String actor = authorizationPolicy.currentActor();
        RehearsalIssueRecord issue = repository.createIssue(
                servicePlanId, target, categoryCode, severityCode, IssueStatusCode.OPEN, title, detail, actor);
        audit("REHEARSAL_ISSUE_OPENED", "rehearsal_issue", issue.issueId(), servicePlanId, target.rehearsalSessionId(),
                null, issueSnapshot(issue), reason, reference);
        moveToIssuesOpenWhenBlocking(issue, reason, reference);
        return issue;
    }

    @Transactional
    public RehearsalIssueActionRecord assignAction(
            UUID servicePlanId,
            UUID issueId,
            String actionSummary,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        requireIssue(servicePlanId, issueId);
        RehearsalIssueActionRecord action = repository.addIssueAction(
                servicePlanId,
                issueId,
                IssueActionStatusCode.TODO,
                actionSummary,
                ownerType,
                ownerActor,
                ownerTeamRoleCode,
                ownerServiceAssignmentId,
                authorizationPolicy.currentActor());
        audit("REHEARSAL_ACTION_ASSIGNED", "rehearsal_issue_action", action.actionId(), servicePlanId, null,
                null, actionSnapshot(action), reason, reference);
        return action;
    }

    @Transactional
    public RehearsalIssueActionRecord assignOwner(
            UUID servicePlanId,
            UUID actionId,
            IssueOwnerType ownerType,
            String ownerActor,
            String ownerTeamRoleCode,
            UUID ownerServiceAssignmentId,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        String before = actionSnapshot(findAction(servicePlanId, actionId));
        RehearsalIssueActionRecord updated = repository.updateIssueActionOwner(
                servicePlanId, actionId, ownerType, ownerActor, ownerTeamRoleCode, ownerServiceAssignmentId,
                authorizationPolicy.currentActor());
        audit("REHEARSAL_ACTION_OWNER_CHANGED", "rehearsal_issue_action", actionId, servicePlanId, null,
                before, actionSnapshot(updated), reason, reference);
        return updated;
    }

    @Transactional
    public RehearsalIssueActionRecord changeActionStatus(
            UUID servicePlanId,
            UUID actionId,
            IssueActionStatusCode statusCode,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        String before = actionSnapshot(findAction(servicePlanId, actionId));
        RehearsalIssueActionRecord updated = repository.updateIssueActionStatus(
                servicePlanId, actionId, statusCode, authorizationPolicy.currentActor());
        audit("REHEARSAL_ACTION_STATUS_CHANGED", "rehearsal_issue_action", actionId, servicePlanId, null,
                before, actionSnapshot(updated), reason, reference);
        return updated;
    }

    @Transactional
    public RehearsalIssueRecord markBlocker(UUID servicePlanId, UUID issueId, String reason, String reference) {
        authorizationPolicy.requireWorkflowMutation();
        RehearsalIssueRecord beforeIssue = requireIssue(servicePlanId, issueId);
        RehearsalIssueRecord updated = repository.updateIssueSeverity(
                servicePlanId, issueId, IssueSeverityCode.BLOCKING, authorizationPolicy.currentActor());
        audit("REHEARSAL_ISSUE_BLOCKER_MARKED", "rehearsal_issue", issueId, servicePlanId,
                updated.target().rehearsalSessionId(), issueSnapshot(beforeIssue), issueSnapshot(updated), reason, reference);
        moveToIssuesOpenWhenBlocking(updated, reason, reference);
        return updated;
    }

    @Transactional
    public RehearsalIssueRecord resolveIssue(UUID servicePlanId, UUID issueId, String reason, String reference) {
        return changeIssueStatus(servicePlanId, issueId, IssueStatusCode.RESOLVED, reason, reference);
    }

    @Transactional
    public RehearsalIssueRecord reopenIssue(UUID servicePlanId, UUID issueId, String reason, String reference) {
        return changeIssueStatus(servicePlanId, issueId, IssueStatusCode.OPEN, reason, reference);
    }

    @Transactional
    public RehearsalIssueRecord changeIssueStatus(
            UUID servicePlanId,
            UUID issueId,
            IssueStatusCode statusCode,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        RehearsalIssueRecord beforeIssue = requireIssue(servicePlanId, issueId);
        validateIssueTransition(beforeIssue.statusCode(), statusCode);
        RehearsalIssueRecord updated = repository.updateIssueStatus(
                servicePlanId, issueId, statusCode, authorizationPolicy.currentActor());
        audit("REHEARSAL_ISSUE_STATUS_CHANGED", "rehearsal_issue", issueId, servicePlanId,
                updated.target().rehearsalSessionId(), issueSnapshot(beforeIssue), issueSnapshot(updated), reason, reference);
        moveToIssuesOpenWhenBlocking(updated, reason, reference);
        return updated;
    }

    @Transactional
    public WorkflowStatus requestReadinessTransition(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            ReadinessStateCode newStateCode,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        return transitionReadiness(servicePlanId, rehearsalSessionId, newStateCode, reason, reference, false);
    }

    @Transactional
    public WorkflowStatus emergencyCorrectReadiness(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            ReadinessStateCode newStateCode,
            String reason,
            String reference) {
        authorizationPolicy.requireEmergencyCorrection();
        return transitionReadiness(servicePlanId, rehearsalSessionId, newStateCode, reason, reference, true);
    }

    @Transactional
    public ArrangementOverrideRecord createArrangementOverride(
            ArrangementOverrideRecord overrideRecord,
            String reason,
            String reference) {
        authorizationPolicy.requireWorkflowMutation();
        ArrangementOverrideRecord created = repository.createArrangementOverride(overrideRecord);
        audit("REHEARSAL_OVERRIDE_CREATED", "service_arrangement_override", created.arrangementOverrideId(),
                created.servicePlanId(), null, null, overrideSnapshot(created), reason, reference);
        return created;
    }

    public WorkflowStatus workflowStatus(UUID servicePlanId) {
        ReadinessStateCode explicit = explicitState(servicePlanId);
        int openBlockingIssues = openBlockingIssueCount(servicePlanId);
        int openRequiredActions = openRequiredActionCount(servicePlanId);
        ReadinessStateCode derived = derive(explicit, openBlockingIssues, openRequiredActions);
        return new WorkflowStatus(servicePlanId, explicit, derived, openBlockingIssues, openRequiredActions);
    }

    private WorkflowStatus transitionReadiness(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            ReadinessStateCode newStateCode,
            String reason,
            String reference,
            boolean emergencyCorrection) {
        ReadinessStateCode previous = explicitState(servicePlanId);
        if (!emergencyCorrection) {
            validateReadinessTransition(previous, newStateCode);
            validateReadyOrCompletedGate(servicePlanId, newStateCode);
        } else if (reason == null || reason.isBlank()) {
            throw new RehearsalWorkflowException("Emergency readiness corrections require an audited reason.");
        }
        repository.recordServiceReadiness(servicePlanId, rehearsalSessionId, newStateCode, reason, authorizationPolicy.currentActor());
        String actionCode = emergencyCorrection ? "REHEARSAL_READINESS_EMERGENCY_CORRECTED" : "REHEARSAL_READINESS_CHANGED";
        audit(actionCode, "service_rehearsal_workflow_state", servicePlanId, servicePlanId, rehearsalSessionId,
                readinessSnapshot(previous), readinessSnapshot(newStateCode), reason, reference);
        return workflowStatus(servicePlanId);
    }

    private void validateReadinessTransition(ReadinessStateCode previous, ReadinessStateCode next) {
        if (previous == next) {
            return;
        }
        Set<ReadinessStateCode> allowed = READINESS_TRANSITIONS.getOrDefault(previous, Set.of());
        if (!allowed.contains(next)) {
            throw new RehearsalWorkflowException("Readiness transition " + previous.code() + " -> " + next.code()
                    + " is not permitted by the rehearsal workflow state machine.");
        }
    }

    private void validateReadyOrCompletedGate(UUID servicePlanId, ReadinessStateCode next) {
        if (next != ReadinessStateCode.READY && next != ReadinessStateCode.COMPLETED) {
            return;
        }
        WorkflowStatus status = workflowStatus(servicePlanId);
        if (status.openBlockingIssueCount() > 0 || status.openRequiredActionCount() > 0) {
            throw new RehearsalWorkflowException("Cannot mark service " + next.code()
                    + " while unresolved blocking issues or required rehearsal actions remain open.");
        }
    }

    private void validateIssueTransition(IssueStatusCode previous, IssueStatusCode next) {
        if (previous == next) {
            return;
        }
        boolean allowed = switch (previous) {
            case OPEN -> next == IssueStatusCode.IN_PROGRESS || next == IssueStatusCode.RESOLVED
                    || next == IssueStatusCode.DEFERRED || next == IssueStatusCode.CANCELLED;
            case IN_PROGRESS -> next == IssueStatusCode.OPEN || next == IssueStatusCode.RESOLVED
                    || next == IssueStatusCode.DEFERRED || next == IssueStatusCode.CANCELLED;
            case RESOLVED, DEFERRED -> next == IssueStatusCode.OPEN;
            case CANCELLED -> false;
        };
        if (!allowed) {
            throw new RehearsalWorkflowException("Issue status transition " + previous.code() + " -> "
                    + next.code() + " is not permitted.");
        }
    }

    private void moveToIssuesOpenWhenBlocking(RehearsalIssueRecord issue, String reason, String reference) {
        if (!issue.blocking()) {
            return;
        }
        ReadinessStateCode explicit = explicitState(issue.servicePlanId());
        if (explicit == ReadinessStateCode.READY) {
            transitionReadiness(issue.servicePlanId(), issue.target().rehearsalSessionId(), ReadinessStateCode.ISSUES_OPEN,
                    reason, reference, false);
        }
    }

    private ReadinessStateCode explicitState(UUID servicePlanId) {
        return repository.findServiceReadiness(servicePlanId).orElse(ReadinessStateCode.DRAFT);
    }

    private ReadinessStateCode derive(ReadinessStateCode explicit, int openBlockingIssues, int openRequiredActions) {
        if ((explicit == ReadinessStateCode.READY || explicit == ReadinessStateCode.COMPLETED)
                && (openBlockingIssues > 0 || openRequiredActions > 0)) {
            return ReadinessStateCode.ISSUES_OPEN;
        }
        return explicit;
    }

    private int openBlockingIssueCount(UUID servicePlanId) {
        return (int) repository.listIssues(servicePlanId).stream().filter(RehearsalIssueRecord::blocking).count();
    }

    private int openRequiredActionCount(UUID servicePlanId) {
        return (int) repository.listIssueActions(servicePlanId).stream().filter(RehearsalIssueActionRecord::open).count();
    }

    private RehearsalIssueRecord requireIssue(UUID servicePlanId, UUID issueId) {
        return repository.findIssue(servicePlanId, issueId)
                .orElseThrow(() -> new RehearsalWorkflowException("Rehearsal issue not found: " + issueId));
    }

    private RehearsalIssueActionRecord findAction(UUID servicePlanId, UUID actionId) {
        return repository.listIssueActions(servicePlanId).stream()
                .filter(action -> action.actionId().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new RehearsalWorkflowException("Rehearsal issue action not found: " + actionId));
    }

    private void audit(
            String actionCode,
            String targetType,
            UUID targetId,
            UUID servicePlanId,
            UUID rehearsalSessionId,
            String beforeStateSnapshot,
            String afterStateSnapshot,
            String reason,
            String reference) {
        repository.recordAudit(new RehearsalAuditRecord(
                null,
                authorizationPolicy.currentActor(),
                authorizationPolicy.currentActorRoles(),
                actionCode,
                targetType,
                targetId,
                servicePlanId,
                rehearsalSessionId,
                Instant.now(),
                reason,
                reference,
                beforeStateSnapshot,
                afterStateSnapshot));
    }

    private String readinessSnapshot(ReadinessStateCode state) {
        return "{\"readinessState\":\"" + state.code() + "\"}";
    }

    private String issueSnapshot(RehearsalIssueRecord issue) {
        return "{\"status\":\"" + issue.statusCode().code() + "\",\"severity\":\""
                + issue.severityCode().code() + "\",\"category\":\"" + issue.categoryCode().code() + "\"}";
    }

    private String actionSnapshot(RehearsalIssueActionRecord action) {
        return "{\"status\":\"" + action.actionStatusCode().code() + "\",\"ownerType\":\""
                + action.ownerType().code() + "\"}";
    }

    private String sessionSnapshot(RehearsalSessionRecord session) {
        return "{\"sessionCode\":\"" + session.sessionCode() + "\",\"readinessState\":\""
                + session.readinessStateCode().code() + "\"}";
    }

    private String overrideSnapshot(ArrangementOverrideRecord overrideRecord) {
        return "{\"arrangementOverrideId\":\"" + overrideRecord.arrangementOverrideId()
                + "\",\"sourceArrangementId\":\"" + overrideRecord.sourceArrangementId() + "\"}";
    }
}
