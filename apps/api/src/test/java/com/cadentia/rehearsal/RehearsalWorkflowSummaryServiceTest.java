package com.cadentia.rehearsal;

import static org.assertj.core.api.Assertions.assertThat;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowStatus;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowSummaryAudience;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RehearsalWorkflowSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-08T12:00:00Z");

    @Test
    void summarizesWorkflowCountsSessionsOverridesAndOpenIssues() {
        UUID servicePlanId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID fromBlockId = UUID.randomUUID();
        UUID toBlockId = UUID.randomUUID();
        UUID transitionIssueId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        FakeWorkflowReader workflowReader = new FakeWorkflowReader();
        RehearsalWorkflowSummaryService service = new RehearsalWorkflowSummaryService(
                workflowReader,
                Clock.fixed(NOW, ZoneOffset.UTC));
        workflowReader.status = new WorkflowStatus(servicePlanId, ReadinessStateCode.READY, ReadinessStateCode.ISSUES_OPEN, 1, 1);
        workflowReader.sessions = List.of(
                new RehearsalSessionRecord(UUID.randomUUID(), servicePlanId, "past", NOW.minusSeconds(7200),
                        NOW.minusSeconds(3600), "Room A", ReadinessStateCode.REHEARSING, null),
                new RehearsalSessionRecord(UUID.randomUUID(), servicePlanId, "next", NOW.plusSeconds(3600),
                        NOW.plusSeconds(7200), "Sanctuary", ReadinessStateCode.PLANNED, null));
        workflowReader.issues = List.of(
                new RehearsalIssueRecord(transitionIssueId, servicePlanId, RehearsalTarget.transition(fromBlockId, toBlockId),
                        IssueCategoryCode.UNRESOLVED_TRANSITION, IssueSeverityCode.BLOCKING, IssueStatusCode.OPEN,
                        "Tighten modulation", "Needs piano cue", "leader@example.test", null, null),
                new RehearsalIssueRecord(UUID.randomUUID(), servicePlanId, RehearsalTarget.setlistItem(blockId, UUID.randomUUID()),
                        IssueCategoryCode.DIFFICULT_SONG, IssueSeverityCode.HIGH, IssueStatusCode.IN_PROGRESS,
                        "Bridge rhythm", "Band needs reps", "leader@example.test", null, null),
                new RehearsalIssueRecord(UUID.randomUUID(), servicePlanId, RehearsalTarget.service(),
                        IssueCategoryCode.GENERAL_FOLLOW_UP, IssueSeverityCode.LOW, IssueStatusCode.RESOLVED,
                        "Closed", "done", "leader@example.test", NOW, null));
        workflowReader.actions = List.of(
                new RehearsalIssueActionRecord(actionId, transitionIssueId, servicePlanId, IssueActionStatusCode.TODO,
                        "Assign MD cue", IssueOwnerType.ACTOR, "md@example.test", null, null, null));
        workflowReader.overrides = List.of(new ArrangementOverrideRecord(
                UUID.randomUUID(), servicePlanId, blockId, UUID.randomUUID(), UUID.randomUUID(), "v1", "G", null,
                null, null, null, null, null, null, "capo 2", "service key", "WL", "WL"));

        var summary = service.summarize(servicePlanId, WorkflowSummaryAudience.WORSHIP_LEADER);

        assertThat(summary.derivedStateCode()).isEqualTo(ReadinessStateCode.ISSUES_OPEN);
        assertThat(summary.currentPhase()).isEqualTo("issues_open");
        assertThat(summary.nextRehearsalSession().sessionCode()).isEqualTo("next");
        assertThat(summary.mostRecentPastRehearsalSession().sessionCode()).isEqualTo("past");
        assertThat(summary.blockerCount()).isEqualTo(1);
        assertThat(summary.overdueActionCount()).isEqualTo(1);
        assertThat(summary.unresolvedTransitionIssueCount()).isEqualTo(1);
        assertThat(summary.difficultSongIssueCount()).isEqualTo(1);
        assertThat(summary.hasServiceSpecificOverrides()).isTrue();
        assertThat(summary.openIssueCounts()).extracting("count").contains(1, 1);
        assertThat(summary.openIssues()).extracting("title").containsExactly("Tighten modulation", "Bridge rhythm");
        assertThat(summary.openIssues().get(0).actions().get(0).ownerActor()).isEqualTo("md@example.test");
    }

    @Test
    void filtersSongAndTransitionIssuesByServiceContextAndRedactsPublicDetails() {
        UUID servicePlanId = UUID.randomUUID();
        UUID songBlockId = UUID.randomUUID();
        UUID otherBlockId = UUID.randomUUID();
        UUID fromBlockId = UUID.randomUUID();
        UUID toBlockId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        FakeWorkflowReader workflowReader = new FakeWorkflowReader();
        RehearsalWorkflowSummaryService service = new RehearsalWorkflowSummaryService(
                workflowReader,
                Clock.fixed(NOW, ZoneOffset.UTC));
        workflowReader.actions = List.of(
                new RehearsalIssueActionRecord(UUID.randomUUID(), issueId, servicePlanId, IssueActionStatusCode.IN_PROGRESS,
                        "Private owner note", IssueOwnerType.SERVICE_ASSIGNMENT, null, "drums", UUID.randomUUID(), null));
        workflowReader.issues = List.of(
                new RehearsalIssueRecord(issueId, servicePlanId, RehearsalTarget.setlistItem(songBlockId, UUID.randomUUID()),
                        IssueCategoryCode.DIFFICULT_SONG, IssueSeverityCode.HIGH, IssueStatusCode.OPEN,
                        "Hard song", "private detail", "person@example.test", null, null),
                new RehearsalIssueRecord(UUID.randomUUID(), servicePlanId, RehearsalTarget.setlistItem(otherBlockId, UUID.randomUUID()),
                        IssueCategoryCode.DIFFICULT_SONG, IssueSeverityCode.HIGH, IssueStatusCode.OPEN,
                        "Other song", "private detail", "person@example.test", null, null),
                new RehearsalIssueRecord(UUID.randomUUID(), servicePlanId, RehearsalTarget.transition(fromBlockId, toBlockId),
                        IssueCategoryCode.UNRESOLVED_TRANSITION, IssueSeverityCode.MEDIUM, IssueStatusCode.OPEN,
                        "Open transition", "transition detail", "person@example.test", null, null));

        var songIssues = service.songIssues(servicePlanId, songBlockId, WorkflowSummaryAudience.PUBLIC);
        var transitionIssues = service.transitionIssues(servicePlanId, fromBlockId, toBlockId, WorkflowSummaryAudience.ADMIN);

        assertThat(songIssues).hasSize(1);
        assertThat(songIssues.get(0).title()).isEqualTo("Hard song");
        assertThat(songIssues.get(0).detail()).isNull();
        assertThat(songIssues.get(0).detectedBy()).isNull();
        assertThat(songIssues.get(0).actions().get(0).ownerType()).isEqualTo(IssueOwnerType.UNASSIGNED);
        assertThat(songIssues.get(0).actions().get(0).ownerServiceAssignmentId()).isNull();
        assertThat(transitionIssues).hasSize(1);
        assertThat(transitionIssues.get(0).detail()).isEqualTo("transition detail");
    }

    private static final class FakeWorkflowReader implements RehearsalWorkflowReader {
        private WorkflowStatus status;
        private List<RehearsalSessionRecord> sessions = List.of();
        private List<RehearsalIssueRecord> issues = List.of();
        private List<RehearsalIssueActionRecord> actions = List.of();
        private List<ArrangementOverrideRecord> overrides = List.of();

        @Override
        public List<RehearsalSessionRecord> listSessions(UUID servicePlanId) {
            return sessions;
        }

        @Override
        public List<RehearsalIssueRecord> listIssues(UUID servicePlanId) {
            return issues;
        }

        @Override
        public List<RehearsalIssueActionRecord> listIssueActions(UUID servicePlanId) {
            return actions;
        }

        @Override
        public List<ArrangementOverrideRecord> listArrangementOverrides(UUID servicePlanId) {
            return overrides;
        }

        @Override
        public WorkflowStatus workflowStatus(UUID servicePlanId) {
            return status == null ? new WorkflowStatus(servicePlanId, ReadinessStateCode.DRAFT, ReadinessStateCode.DRAFT, 0, 0) : status;
        }
    }
}
