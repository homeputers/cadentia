package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueActionIndicator;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueCount;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowIssueIndicator;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummaryAudience;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummarySession;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RehearsalWorkflowSummaryService implements RehearsalWorkflowSummaryProvider {

    private final RehearsalWorkflowReader workflowService;
    private final Clock clock;

    public RehearsalWorkflowSummaryService(RehearsalWorkflowReader workflowService) {
        this(workflowService, Clock.systemUTC());
    }

    RehearsalWorkflowSummaryService(RehearsalWorkflowReader workflowService, Clock clock) {
        this.workflowService = workflowService;
        this.clock = clock;
    }

    public WorkflowSummary summarize(UUID servicePlanId, WorkflowSummaryAudience audience) {
        WorkflowSummaryAudience effectiveAudience = audience == null ? WorkflowSummaryAudience.PUBLIC : audience;
        var status = workflowService.workflowStatus(servicePlanId);
        List<RehearsalSessionRecord> sessions = workflowService.listSessions(servicePlanId).stream()
                .filter(session -> session.archivedAt() == null)
                .sorted(Comparator.comparing(RehearsalSessionRecord::startsAt))
                .toList();
        List<RehearsalIssueRecord> openIssues = workflowService.listIssues(servicePlanId).stream()
                .filter(RehearsalIssueRecord::open)
                .filter(issue -> issue.archivedAt() == null)
                .toList();
        List<RehearsalIssueActionRecord> actions = workflowService.listIssueActions(servicePlanId);
        Map<UUID, List<RehearsalIssueActionRecord>> actionsByIssueId = actions.stream()
                .collect(Collectors.groupingBy(RehearsalIssueActionRecord::issueId));
        List<ArrangementOverrideRecord> overrides = workflowService.listArrangementOverrides(servicePlanId);
        Instant now = Instant.now(clock);
        WorkflowSummarySession nextSession = sessions.stream()
                .filter(session -> !session.startsAt().isBefore(now))
                .findFirst()
                .map(this::sessionSummary)
                .orElse(null);
        WorkflowSummarySession mostRecentPastSession = sessions.stream()
                .filter(session -> session.startsAt().isBefore(now))
                .max(Comparator.comparing(RehearsalSessionRecord::startsAt))
                .map(this::sessionSummary)
                .orElse(null);
        List<WorkflowIssueCount> counts = issueCounts(openIssues);
        List<WorkflowIssueIndicator> indicators = openIssues.stream()
                .sorted(Comparator.comparing(RehearsalIssueRecord::blocking).reversed()
                        .thenComparing(issue -> issue.severityCode().ordinal(), Comparator.reverseOrder())
                        .thenComparing(RehearsalIssueRecord::title))
                .map(issue -> issueIndicator(issue, actionsByIssueId.getOrDefault(issue.issueId(), List.of()), effectiveAudience))
                .toList();
        long openActionCount = actions.stream().filter(RehearsalIssueActionRecord::open).count();
        return new WorkflowSummary(
                servicePlanId,
                status.explicitStateCode(),
                status.derivedStateCode(),
                status.readyForService(),
                phaseFrom(status.derivedStateCode()),
                nextSession,
                mostRecentPastSession,
                sessions.stream().map(this::sessionSummary).toList(),
                status.openBlockingIssueCount(),
                (int) openActionCount,
                counts,
                (int) openIssues.stream()
                        .filter(issue -> issue.categoryCode() == IssueCategoryCode.UNRESOLVED_TRANSITION)
                        .count(),
                (int) openIssues.stream()
                        .filter(issue -> issue.categoryCode() == IssueCategoryCode.DIFFICULT_SONG)
                        .count(),
                overrides.size(),
                !overrides.isEmpty(),
                indicators,
                effectiveAudience != WorkflowSummaryAudience.ADMIN);
    }

    public List<WorkflowIssueIndicator> songIssues(UUID servicePlanId, UUID servicePlanBlockId, WorkflowSummaryAudience audience) {
        return targetedIssues(servicePlanId, audience, target -> servicePlanBlockId != null
                && servicePlanBlockId.equals(target.servicePlanBlockId()));
    }

    public List<WorkflowIssueIndicator> transitionIssues(
            UUID servicePlanId,
            UUID transitionFromBlockId,
            UUID transitionToBlockId,
            WorkflowSummaryAudience audience) {
        return targetedIssues(servicePlanId, audience, target -> transitionFromBlockId != null
                && transitionFromBlockId.equals(target.transitionFromBlockId())
                && transitionToBlockId != null
                && transitionToBlockId.equals(target.transitionToBlockId()));
    }

    private List<WorkflowIssueIndicator> targetedIssues(
            UUID servicePlanId,
            WorkflowSummaryAudience audience,
            java.util.function.Predicate<RehearsalTarget> predicate) {
        WorkflowSummaryAudience effectiveAudience = audience == null ? WorkflowSummaryAudience.PUBLIC : audience;
        Map<UUID, List<RehearsalIssueActionRecord>> actionsByIssueId = workflowService.listIssueActions(servicePlanId).stream()
                .collect(Collectors.groupingBy(RehearsalIssueActionRecord::issueId));
        return workflowService.listIssues(servicePlanId).stream()
                .filter(RehearsalIssueRecord::open)
                .filter(issue -> issue.archivedAt() == null)
                .filter(issue -> predicate.test(issue.target()))
                .map(issue -> issueIndicator(issue, actionsByIssueId.getOrDefault(issue.issueId(), List.of()), effectiveAudience))
                .toList();
    }

    private List<WorkflowIssueCount> issueCounts(List<RehearsalIssueRecord> openIssues) {
        return openIssues.stream()
                .collect(Collectors.groupingBy(issue -> new IssueCountKey(issue.categoryCode(), issue.severityCode()), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<IssueCountKey, Long>comparingByKey())
                .map(entry -> new WorkflowIssueCount(entry.getKey().categoryCode(), entry.getKey().severityCode(), entry.getValue().intValue()))
                .toList();
    }

    private WorkflowIssueIndicator issueIndicator(
            RehearsalIssueRecord issue,
            List<RehearsalIssueActionRecord> actions,
            WorkflowSummaryAudience audience) {
        boolean redactPrivateDetails = audience == WorkflowSummaryAudience.PUBLIC;
        return new WorkflowIssueIndicator(
                issue.issueId(),
                issue.target(),
                issue.categoryCode(),
                issue.severityCode(),
                issue.statusCode(),
                issue.blocking(),
                issue.title(),
                redactPrivateDetails ? null : issue.detail(),
                redactPrivateDetails ? null : issue.detectedBy(),
                actions.stream()
                        .map(action -> actionIndicator(action, redactPrivateDetails))
                        .toList());
    }

    private WorkflowIssueActionIndicator actionIndicator(
            RehearsalIssueActionRecord action,
            boolean redactPrivateDetails) {
        return new WorkflowIssueActionIndicator(
                action.actionId(),
                action.actionStatusCode(),
                redactPrivateDetails ? null : action.actionSummary(),
                ownerTypeFor(action, redactPrivateDetails),
                redactPrivateDetails ? null : action.ownerActor(),
                action.ownerTeamRoleCode(),
                redactPrivateDetails ? null : action.ownerServiceAssignmentId(),
                action.completedAt(),
                action.open());
    }

    private IssueOwnerType ownerTypeFor(RehearsalIssueActionRecord action, boolean redactPrivateDetails) {
        if (redactPrivateDetails && (action.ownerType() == IssueOwnerType.ACTOR
                || action.ownerType() == IssueOwnerType.SERVICE_ASSIGNMENT)) {
            return IssueOwnerType.UNASSIGNED;
        }
        return action.ownerType();
    }

    private WorkflowSummarySession sessionSummary(RehearsalSessionRecord session) {
        return new WorkflowSummarySession(
                session.rehearsalSessionId(),
                session.sessionCode(),
                session.startsAt(),
                session.endsAt(),
                session.location(),
                session.readinessStateCode());
    }

    private String phaseFrom(RehearsalWorkflowModels.ReadinessStateCode readinessStateCode) {
        return switch (readinessStateCode) {
            case DRAFT -> "planning";
            case PLANNED -> "planned";
            case REHEARSING -> "rehearsal_active";
            case ISSUES_OPEN -> "issues_open";
            case READY -> "ready_for_service";
            case COMPLETED -> "completed";
        };
    }

    private record IssueCountKey(IssueCategoryCode categoryCode, IssueSeverityCode severityCode)
            implements Comparable<IssueCountKey> {

        @Override
        public int compareTo(IssueCountKey other) {
            int category = Integer.compare(categoryCode.ordinal(), other.categoryCode.ordinal());
            if (category != 0) {
                return category;
            }
            return Integer.compare(severityCode.ordinal(), other.severityCode.ordinal());
        }
    }
}
