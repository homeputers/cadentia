package com.cadentia.api.controller;

import com.cadentia.generated.model.RehearsalIssueActionStatusCode;
import com.cadentia.generated.model.RehearsalIssueCategoryCode;
import com.cadentia.generated.model.RehearsalIssueOwnerType;
import com.cadentia.generated.model.RehearsalIssueSeverityCode;
import com.cadentia.generated.model.RehearsalIssueStatusCode;
import com.cadentia.generated.model.RehearsalReadinessStateCode;
import com.cadentia.generated.model.RehearsalWorkflowTarget;
import com.cadentia.generated.model.RehearsalWorkflowTargetTypeCode;
import com.cadentia.generated.model.ServicePlanWorkflowIssueActionIndicator;
import com.cadentia.generated.model.ServicePlanWorkflowIssueCount;
import com.cadentia.generated.model.ServicePlanWorkflowIssueIndicator;
import com.cadentia.generated.model.ServicePlanWorkflowSummary;
import com.cadentia.generated.model.ServicePlanWorkflowSessionSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowIssueActionIndicator;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowIssueCount;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowIssueIndicator;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummarySession;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class ServicePlanWorkflowSummaryResponseMapper {

    private ServicePlanWorkflowSummaryResponseMapper() {
    }

    public static ServicePlanWorkflowSummary toResponse(RehearsalWorkflowSummary summary) {
        if (summary == null) {
            return null;
        }
        ServicePlanWorkflowSummary response = new ServicePlanWorkflowSummary(
                summary.servicePlanId(),
                readiness(summary.explicitStateCode().code()),
                readiness(summary.derivedStateCode().code()),
                summary.readyForService(),
                ServicePlanWorkflowSummary.CurrentPhaseEnum.fromValue(summary.currentPhase()),
                summary.rehearsalSessions().stream().map(ServicePlanWorkflowSummaryResponseMapper::session).toList(),
                summary.blockerCount(),
                summary.overdueActionCount(),
                summary.openIssueCounts().stream().map(ServicePlanWorkflowSummaryResponseMapper::issueCount).toList(),
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

    static List<ServicePlanWorkflowIssueIndicator> toIssueResponses(
            List<RehearsalWorkflowIssueIndicator> issues) {
        if (issues == null) {
            return List.of();
        }
        return issues.stream().map(ServicePlanWorkflowSummaryResponseMapper::issue).toList();
    }

    private static ServicePlanWorkflowSessionSummary session(
            RehearsalWorkflowSummarySession session) {
        if (session == null) {
            return null;
        }
        return new ServicePlanWorkflowSessionSummary(
                session.rehearsalSessionId(),
                session.sessionCode(),
                atOffset(session.startsAt()),
                atOffset(session.endsAt()),
                readiness(session.readinessStateCode().code()))
                .location(session.location());
    }

    private static ServicePlanWorkflowIssueCount issueCount(
            RehearsalWorkflowIssueCount count) {
        return new ServicePlanWorkflowIssueCount(
                RehearsalIssueCategoryCode.fromValue(count.categoryCode().code()),
                RehearsalIssueSeverityCode.fromValue(count.severityCode().code()),
                count.count());
    }

    private static ServicePlanWorkflowIssueIndicator issue(
            RehearsalWorkflowIssueIndicator issue) {
        ServicePlanWorkflowIssueIndicator response = new ServicePlanWorkflowIssueIndicator(
                issue.issueId(),
                target(issue.target()),
                RehearsalIssueCategoryCode.fromValue(issue.categoryCode().code()),
                RehearsalIssueSeverityCode.fromValue(issue.severityCode().code()),
                RehearsalIssueStatusCode.fromValue(issue.statusCode().code()),
                issue.blocking(),
                issue.title(),
                issue.actions().stream().map(ServicePlanWorkflowSummaryResponseMapper::action).toList());
        response.setDetail(issue.detail());
        response.setDetectedBy(issue.detectedBy());
        return response;
    }

    private static ServicePlanWorkflowIssueActionIndicator action(
            RehearsalWorkflowIssueActionIndicator action) {
        ServicePlanWorkflowIssueActionIndicator response = new ServicePlanWorkflowIssueActionIndicator(
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
