package com.cadentia.rehearsal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalCompletedServiceHistoryRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportActionRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportIssueRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalReportServiceRow;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionArchiveResult;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalRetentionConfiguration;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RehearsalWorkflowReportServiceTest {

    private FakeReportRepository repository;
    private RehearsalWorkflowReportService service;
    private UUID servicePlanId;

    @BeforeEach
    void setUp() {
        repository = new FakeReportRepository();
        service = new RehearsalWorkflowReportService(
                repository,
                new RehearsalWorkflowAuthorizationPolicy(),
                new RehearsalWorkflowTelemetryRecorder(new SimpleMeterRegistry()));
        servicePlanId = UUID.randomUUID();
        authenticate(RbacAuthorities.ROLE_REPORTING_VIEWER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportQueriesReturnStructuredRowsWithoutParsingFreeFormComments() {
        // Arrange
        Instant now = Instant.parse("2026-06-09T12:00:00Z");
        UUID issueId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        repository.blockedServices = List.of(new RehearsalReportServiceRow(
                servicePlanId, ReadinessStateCode.READY, ReadinessStateCode.ISSUES_OPEN, now, 1, 1, 1, 1, 1, 1, 1));
        repository.blockers = List.of(new RehearsalReportIssueRow(issueId, servicePlanId, RehearsalTarget.service(),
                IssueCategoryCode.BLOCKER, IssueSeverityCode.BLOCKING, IssueStatusCode.OPEN, now, null));
        repository.transitions = List.of(new RehearsalReportIssueRow(UUID.randomUUID(), servicePlanId,
                RehearsalTarget.transition(UUID.randomUUID(), UUID.randomUUID()), IssueCategoryCode.UNRESOLVED_TRANSITION,
                IssueSeverityCode.HIGH, IssueStatusCode.IN_PROGRESS, now, null));
        repository.difficultSongs = List.of(new RehearsalReportIssueRow(UUID.randomUUID(), servicePlanId,
                RehearsalTarget.setlistItem(UUID.randomUUID(), null), IssueCategoryCode.DIFFICULT_SONG,
                IssueSeverityCode.MEDIUM, IssueStatusCode.OPEN, now, null));
        repository.overdueActions = List.of(new RehearsalReportActionRow(actionId, issueId, servicePlanId,
                IssueActionStatusCode.TODO, IssueOwnerType.TEAM_ROLE, "MUSIC_DIRECTOR", null, now.minusSeconds(60), null));
        repository.overrideServices = repository.blockedServices;
        repository.completedHistory = List.of(new RehearsalCompletedServiceHistoryRow(
                servicePlanId, now.minusSeconds(86_400), now, 2, 1, 3, 2, 1, 6));

        // Act / Assert
        assertThat(service.servicesBlockedFromReadiness(now)).hasSize(1)
                .first().extracting(RehearsalReportServiceRow::derivedStateCode)
                .isEqualTo(ReadinessStateCode.ISSUES_OPEN);
        assertThat(service.openBlockers(servicePlanId)).extracting(RehearsalReportIssueRow::categoryCode)
                .containsExactly(IssueCategoryCode.BLOCKER);
        assertThat(service.unresolvedTransitionIssues(servicePlanId)).extracting(RehearsalReportIssueRow::categoryCode)
                .containsExactly(IssueCategoryCode.UNRESOLVED_TRANSITION);
        assertThat(service.difficultSongs(servicePlanId)).extracting(RehearsalReportIssueRow::categoryCode)
                .containsExactly(IssueCategoryCode.DIFFICULT_SONG);
        assertThat(service.overdueOwnerActions(now, servicePlanId)).extracting(RehearsalReportActionRow::ownerTeamRoleCode)
                .containsExactly("MUSIC_DIRECTOR");
        assertThat(service.servicesWithActiveArrangementOverrides()).extracting(RehearsalReportServiceRow::activeOverrideCount)
                .containsExactly(1);
        assertThat(service.completedServiceHistory(now.minusSeconds(604_800), now.plusSeconds(1)))
                .extracting(RehearsalCompletedServiceHistoryRow::auditEventCount)
                .containsExactly(6);
    }

    @Test
    void reportsRequireReportingRoleAndRetentionRequiresAdministrator() {
        // Arrange
        authenticate(RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act / Assert
        assertThatThrownBy(() -> service.servicesBlockedFromReadiness(Instant.now()))
                .isInstanceOf(AccessDeniedException.class);

        authenticate(RbacAuthorities.ROLE_REPORTING_VIEWER);
        assertThatThrownBy(() -> service.archiveCompletedRehearsalData(RehearsalRetentionConfiguration.defaults(), Instant.now()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void retentionArchiveRejectsPolicyBelowMinimumsAndPreservesAuditAvailability() {
        // Arrange
        authenticate(RbacAuthorities.ROLE_ADMIN);
        Instant now = Instant.parse("2026-06-09T12:00:00Z");
        repository.archiveResult = new RehearsalRetentionArchiveResult(now, 2, 5, 3, 1, 9);
        RehearsalRetentionConfiguration invalid = new RehearsalRetentionConfiguration(1, 1, 1, 1, 1, 90, 30, 180, 180, 2555);

        // Act / Assert
        assertThatThrownBy(() -> service.archiveCompletedRehearsalData(invalid, now))
                .isInstanceOf(RehearsalWorkflowException.class)
                .hasMessageContaining("below minimum");

        RehearsalRetentionArchiveResult result = service.archiveCompletedRehearsalData(
                RehearsalRetentionConfiguration.defaults(), now);
        assertThat(result.archivedSessions()).isEqualTo(2);
        assertThat(result.retainedAuditRecords()).isEqualTo(9);
    }

    private void authenticate(String role) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("reporter", "password", role);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static final class FakeReportRepository implements RehearsalWorkflowReportRepository {
        private List<RehearsalReportServiceRow> blockedServices = List.of();
        private List<RehearsalReportIssueRow> blockers = List.of();
        private List<RehearsalReportIssueRow> transitions = List.of();
        private List<RehearsalReportIssueRow> difficultSongs = List.of();
        private List<RehearsalReportActionRow> overdueActions = List.of();
        private List<RehearsalReportServiceRow> overrideServices = List.of();
        private List<RehearsalCompletedServiceHistoryRow> completedHistory = List.of();
        private RehearsalRetentionArchiveResult archiveResult;

        @Override
        public List<RehearsalReportServiceRow> listServicesBlockedFromReadiness(Instant asOf) {
            return blockedServices;
        }

        @Override
        public List<RehearsalReportIssueRow> listOpenBlockersByService(UUID servicePlanId) {
            return blockers;
        }

        @Override
        public List<RehearsalReportIssueRow> listUnresolvedTransitionIssues(UUID servicePlanId) {
            return transitions;
        }

        @Override
        public List<RehearsalReportIssueRow> listDifficultSongs(UUID servicePlanId) {
            return difficultSongs;
        }

        @Override
        public List<RehearsalReportActionRow> listOverdueOwnerActions(Instant asOf, UUID servicePlanId) {
            return overdueActions;
        }

        @Override
        public List<RehearsalReportServiceRow> listServicesWithActiveArrangementOverrides() {
            return overrideServices;
        }

        @Override
        public List<RehearsalCompletedServiceHistoryRow> listCompletedServiceHistory(
                Instant completedSince,
                Instant completedBefore) {
            return completedHistory;
        }

        @Override
        public RehearsalRetentionArchiveResult archiveCompletedRehearsalData(
                RehearsalRetentionConfiguration retentionConfiguration,
                Instant asOf,
                String archivedBy) {
            if (retentionConfiguration.completedSessionsRetainDays() < retentionConfiguration.minCompletedSessionsRetainDays()) {
                throw new RehearsalWorkflowException(
                        "Rehearsal retention configuration is below minimum accountable history limits.");
            }
            return archiveResult;
        }
    }
}
