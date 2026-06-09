package com.cadentia.api.controller;

import com.cadentia.generated.model.RehearsalIssueActionStatusCode;
import com.cadentia.generated.model.RehearsalIssueCategoryCode;
import com.cadentia.generated.model.RehearsalIssueOwnerType;
import com.cadentia.generated.model.RehearsalIssueSeverityCode;
import com.cadentia.generated.model.RehearsalIssueStatusCode;
import com.cadentia.generated.model.RehearsalReadinessStateCode;
import com.cadentia.generated.model.RehearsalWorkflowTarget;
import com.cadentia.generated.model.RehearsalWorkflowTargetTypeCode;
import com.cadentia.generated.model.WorkflowIssueActionIndicator;
import com.cadentia.generated.model.WorkflowIssueCount;
import com.cadentia.generated.model.WorkflowIssueIndicator;
import com.cadentia.generated.model.WorkflowOperationalSummary;
import com.cadentia.generated.model.WorkflowSummarySession;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummary;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class WorkflowSummaryResponseMapper {

    private WorkflowSummaryResponseMapper() {
    }

    public static WorkflowOperationalSummary toResponse(WorkflowSummary summary) {
        if (summary == null) {
            return null;
        }
        WorkflowOperationalSummary response = new WorkflowOperationalSummary(
                summary.servicePlanId(),
                readiness(summary.explicitStateCode().code()),
                readiness(summary.derivedStateCode().code()),
                summary.readyForService(),
                WorkflowOperationalSummary.CurrentPhaseEnum.fromValue(summary.currentPhase()),
                summary.rehearsalSessions().stream().map(WorkflowSummaryResponseMapper::session).toList(),
                summary.blockerCount(),
                summary.overdueActionCount(),
                summary.openIssueCounts().stream().map(WorkflowSummaryResponseMapper::issueCount).toList(),
                summary.unresolvedTransitionIssueCount(),
                summary.difficultSongIssueCount(),
                summary.serviceSpecificOverrideCount(),
                summary.hasServiceSpecificOverrides(),
                toIssueResponses(summary.openIssues()),
                summary.redacted());
        response.setNextRehearsalSession(session(summary.nextRehearsalSession()));
        response.setMostRecentPastRehearsalSession(session(summary.mostRecentPastRehearsalSession()));
        return response;
    }

    static List<WorkflowIssueIndicator> toIssueResponses(
            List<com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueIndicator> issues) {
        if (issues == null) {
            return List.of();
        }
        return issues.stream().map(WorkflowSummaryResponseMapper::issue).toList();
    }

    private static WorkflowSummarySession session(
            com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummarySession session) {
        if (session == null) {
            return null;
        }
        return new WorkflowSummarySession(
                session.rehearsalSessionId(),
                session.sessionCode(),
                atOffset(session.startsAt()),
                atOffset(session.endsAt()),
                readiness(session.readinessStateCode().code()))
                .location(session.location());
    }

    private static WorkflowIssueCount issueCount(
            com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueCount count) {
        return new WorkflowIssueCount(
                RehearsalIssueCategoryCode.fromValue(count.categoryCode().code()),
                RehearsalIssueSeverityCode.fromValue(count.severityCode().code()),
                count.count());
    }

    private static WorkflowIssueIndicator issue(
            com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueIndicator issue) {
        WorkflowIssueIndicator response = new WorkflowIssueIndicator(
                issue.issueId(),
                target(issue.target()),
                RehearsalIssueCategoryCode.fromValue(issue.categoryCode().code()),
                RehearsalIssueSeverityCode.fromValue(issue.severityCode().code()),
                RehearsalIssueStatusCode.fromValue(issue.statusCode().code()),
                issue.blocking(),
                issue.title(),
                issue.actions().stream().map(WorkflowSummaryResponseMapper::action).toList());
        response.setDetail(issue.detail());
        response.setDetectedBy(issue.detectedBy());
        return response;
    }

    private static WorkflowIssueActionIndicator action(
            com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueActionIndicator action) {
        WorkflowIssueActionIndicator response = new WorkflowIssueActionIndicator(
                action.actionId(),
                RehearsalIssueActionStatusCode.fromValue(action.actionStatusCode().code()),
                RehearsalIssueOwnerType.fromValue(action.ownerType().code()),
                action.open());
        response.setActionSummary(action.actionSummary());
        response.setOwnerActor(action.ownerActor());
        response.setOwnerTeamRoleCode(action.ownerTeamRoleCode());
        response.setOwnerServiceAssignmentId(action.ownerServiceAssignmentId());
        response.setCompletedAt(atOffset(action.completedAt()));
        return response;
    }

    private static RehearsalWorkflowTarget target(RehearsalTarget target) {
        RehearsalWorkflowTarget response = new RehearsalWorkflowTarget(
                RehearsalWorkflowTargetTypeCode.fromValue(target.targetTypeCode().code()));
        response.setRehearsalSessionId(target.rehearsalSessionId());
        response.setServicePlanBlockId(target.servicePlanBlockId());
        response.setSetlistVersionItemId(target.setlistVersionItemId());
        response.setTransitionFromBlockId(target.transitionFromBlockId());
        response.setTransitionToBlockId(target.transitionToBlockId());
        response.setArrangementId(target.arrangementId());
        response.setTeamRoleCode(target.teamRoleCode());
        response.setServiceTeamAssignmentId(target.serviceTeamAssignmentId());
        response.setRehearsalTeamAssignmentId(target.rehearsalTeamAssignmentId());
        response.setSongAssignmentOverrideId(target.songAssignmentOverrideId());
        return response;
    }

    private static RehearsalReadinessStateCode readiness(String value) {
        return RehearsalReadinessStateCode.fromValue(value);
    }

    private static OffsetDateTime atOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
