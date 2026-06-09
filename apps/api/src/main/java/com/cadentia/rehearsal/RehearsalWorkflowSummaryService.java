package com.cadentia.rehearsal;

import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowIssueActionIndicator;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowIssueCount;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowIssueIndicator;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummaryAudience;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummarySession;
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

    public RehearsalWorkflowSummary summarize(UUID servicePlanId, RehearsalWorkflowSummaryAudience audience) {
        RehearsalWorkflowSummaryAudience effectiveAudience = audience == null ? RehearsalWorkflowSummaryAudience.PUBLIC : audience;
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
        RehearsalWorkflowSummarySession nextSession = sessions.stream()
                .filter(session -> !session.startsAt().isBefore(now))
                .findFirst()
                .map(this::sessionSummary)
                .orElse(null);
        RehearsalWorkflowSummarySession mostRecentPastSession = sessions.stream()
                .filter(session -> session.startsAt().isBefore(now))
                .max(Comparator.comparing(RehearsalSessionRecord::startsAt))
                .map(this::sessionSummary)
                .orElse(null);
        List<RehearsalWorkflowIssueCount> counts = issueCounts(openIssues);
        List<RehearsalWorkflowIssueIndicator> indicators = openIssues.stream()
                .sorted(Comparator.comparing(RehearsalIssueRecord::blocking).reversed()
                        .thenComparing(issue -> issue.severityCode().ordinal(), Comparator.reverseOrder())
                        .thenComparing(RehearsalIssueRecord::title))
                .map(issue -> issueIndicator(issue, actionsByIssueId.getOrDefault(issue.issueId(), List.of()), effectiveAudience))
                .toList();
        long openActionCount = actions.stream().filter(RehearsalIssueActionRecord::open).count();
        return new RehearsalWorkflowSummary(
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
                effectiveAudience != RehearsalWorkflowSummaryAudience.ADMIN);
    }

    public List<RehearsalWorkflowIssueIndicator> songIssues(UUID servicePlanId, UUID servicePlanBlockId, RehearsalWorkflowSummaryAudience audience) {
        return targetedIssues(servicePlanId, audience, target -> servicePlanBlockId != null
                && servicePlanBlockId.equals(target.servicePlanBlockId()));
    }

    public List<RehearsalWorkflowIssueIndicator> transitionIssues(
            UUID servicePlanId,
            UUID transitionFromBlockId,
            UUID transitionToBlockId,
            RehearsalWorkflowSummaryAudience audience) {
        return targetedIssues(servicePlanId, audience, target -> transitionFromBlockId != null
                && transitionFromBlockId.equals(target.transitionFromBlockId())
                && transitionToBlockId != null
                && transitionToBlockId.equals(target.transitionToBlockId()));
    }

    private List<RehearsalWorkflowIssueIndicator> targetedIssues(
            UUID servicePlanId,
            RehearsalWorkflowSummaryAudience audience,
            java.util.function.Predicate<RehearsalTarget> predicate) {
        RehearsalWorkflowSummaryAudience effectiveAudience = audience == null ? RehearsalWorkflowSummaryAudience.PUBLIC : audience;
        Map<UUID, List<RehearsalIssueActionRecord>> actionsByIssueId = workflowService.listIssueActions(servicePlanId).stream()
                .collect(Collectors.groupingBy(RehearsalIssueActionRecord::issueId));
        return workflowService.listIssues(servicePlanId).stream()
                .filter(RehearsalIssueRecord::open)
                .filter(issue -> issue.archivedAt() == null)
                .filter(issue -> predicate.test(issue.target()))
                .map(issue -> issueIndicator(issue, actionsByIssueId.getOrDefault(issue.issueId(), List.of()), effectiveAudience))
                .toList();
    }

    private List<RehearsalWorkflowIssueCount> issueCounts(List<RehearsalIssueRecord> openIssues) {
        return openIssues.stream()
                .collect(Collectors.groupingBy(issue -> new IssueCountKey(issue.categoryCode(), issue.severityCode()), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<IssueCountKey, Long>comparingByKey())
                .map(entry -> new RehearsalWorkflowIssueCount(entry.getKey().categoryCode(), entry.getKey().severityCode(), entry.getValue().intValue()))
                .toList();
    }

    private RehearsalWorkflowIssueIndicator issueIndicator(
            RehearsalIssueRecord issue,
            List<RehearsalIssueActionRecord> actions,
            RehearsalWorkflowSummaryAudience audience) {
        boolean redactPrivateDetails = audience == RehearsalWorkflowSummaryAudience.PUBLIC;
        return new RehearsalWorkflowIssueIndicator(
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

    private RehearsalWorkflowIssueActionIndicator actionIndicator(
            RehearsalIssueActionRecord action,
            boolean redactPrivateDetails) {
        return new RehearsalWorkflowIssueActionIndicator(
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

    private RehearsalWorkflowSummarySession sessionSummary(RehearsalSessionRecord session) {
        return new RehearsalWorkflowSummarySession(
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
