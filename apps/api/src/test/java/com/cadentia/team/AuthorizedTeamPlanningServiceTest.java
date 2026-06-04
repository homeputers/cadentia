package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.api.security.PersonnelAuthorizationPolicy;
import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.api.security.SecurityObservabilityRecorder;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditAction;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthorizedTeamPlanningServiceTest {

    @Mock
    private TeamPlanningRepository repository;

    @Mock
    private PersonnelAuditRecorder auditService;

    @Captor
    private ArgumentCaptor<PersonnelAuditEvent> auditEventCaptor;

    private AuthorizedTeamPlanningService service;

    @BeforeEach
    void setUp() {
        PersonnelAuthorizationPolicy policy =
                new PersonnelAuthorizationPolicy(new SecurityObservabilityRecorder(new SimpleMeterRegistry()));
        service = new AuthorizedTeamPlanningService(repository, policy, new PersonnelDataRedactor(policy), auditService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void schedulerCreatesAssignmentAndWritesPrivilegedAuditWithoutSensitiveSummary() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        UUID musicianId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        authenticate("scheduler", RbacAuthorities.ROLE_TEAM_SCHEDULER);
        ServiceAssignmentRecord assignment = new ServiceAssignmentRecord(
                assignmentId,
                servicePlanId,
                musicianId,
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.PIANO,
                null,
                AssignmentStatusCode.REQUESTED);
        when(repository.createServiceAssignment(
                        servicePlanId,
                        musicianId,
                        MusicianRoleCode.INSTRUMENTALIST,
                        InstrumentCode.PIANO,
                        null,
                        AssignmentStatusCode.REQUESTED))
                .thenReturn(assignment);

        // Act
        ServiceAssignmentRecord result = service.createServiceAssignment(
                servicePlanId,
                musicianId,
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.PIANO,
                null,
                AssignmentStatusCode.REQUESTED,
                "weekly_schedule",
                "req-123");

        // Assert
        assertThat(result.assignmentId()).isEqualTo(assignmentId);
        verify(auditService).record(auditEventCaptor.capture());
        PersonnelAuditEvent auditEvent = auditEventCaptor.getValue();
        assertThat(auditEvent.action()).isEqualTo(PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED);
        assertThat(auditEvent.actor()).isEqualTo("scheduler");
        assertThat(auditEvent.reasonCode()).isEqualTo("weekly_schedule");
        assertThat(auditEvent.changedFields().toString()).doesNotContain("555", "example.test", "pastoral");
        assertThat(auditEvent.beforeStateRef()).isNull();
        assertThat(auditEvent.afterStateRef()).contains(assignmentId.toString());
    }

    @Test
    void assignedMusicianCanUpdateOwnAssignmentResponseButCannotManageRosterAssignment() {
        // Arrange
        UUID assignmentId = UUID.randomUUID();
        UUID musicianId = UUID.randomUUID();
        UUID servicePlanId = UUID.randomUUID();
        MusicianRecord musician = musician(musicianId, "avery@example.test");
        ServiceAssignmentRecord existing = new ServiceAssignmentRecord(
                assignmentId,
                servicePlanId,
                musicianId,
                MusicianRoleCode.VOCALIST,
                null,
                null,
                AssignmentStatusCode.REQUESTED);
        ServiceAssignmentRecord declined = new ServiceAssignmentRecord(
                assignmentId,
                servicePlanId,
                musicianId,
                MusicianRoleCode.VOCALIST,
                null,
                null,
                AssignmentStatusCode.DECLINED);
        authenticate("avery@example.test", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);
        when(repository.findServiceAssignment(assignmentId)).thenReturn(Optional.of(existing));
        when(repository.findMusician(musicianId)).thenReturn(Optional.of(musician));
        when(repository.updateServiceAssignmentStatus(assignmentId, AssignmentStatusCode.DECLINED))
                .thenReturn(Optional.of(declined));

        // Act
        Optional<ServiceAssignmentRecord> result = service.updateOwnServiceAssignmentResponse(
                assignmentId, AssignmentStatusCode.DECLINED, "musician_response", "portal");

        // Assert
        assertThat(result).hasValueSatisfying(response ->
                assertThat(response.statusCode()).isEqualTo(AssignmentStatusCode.DECLINED));
        verify(auditService).record(auditEventCaptor.capture());
        assertThat(auditEventCaptor.getValue().changedFields()).containsEntry("fields", "statusCode");
        when(repository.listUpcomingServiceAssignmentsForMusician(musicianId, java.time.Instant.parse("2026-06-04T00:00:00Z")))
                .thenReturn(List.of(declined));
        assertThat(service.listOwnUpcomingServiceAssignments(musicianId, java.time.Instant.parse("2026-06-04T00:00:00Z")))
                .containsExactly(declined);
        assertThatThrownBy(() -> service.createServiceAssignment(
                        servicePlanId,
                        musicianId,
                        MusicianRoleCode.VOCALIST,
                        null,
                        null,
                        AssignmentStatusCode.ACCEPTED,
                        "attempt",
                        "portal"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");
    }

    @Test
    void unauthorizedProfileReadUsesGenericDeniedMessageAndDoesNotAuditMutation() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        authenticate("stranger", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);
        when(repository.findMusician(musicianId)).thenReturn(Optional.of(musician(musicianId, "owner@example.test")));

        // Act / Assert
        assertThatThrownBy(() -> service.findMusicianProfile(musicianId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");
        verify(auditService, never()).record(any());
    }

    private void authenticate(String principal, String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(principal, "n/a", List.of(() -> authority)));
    }

    private MusicianRecord musician(UUID musicianId, String accountPrincipal) {
        return new MusicianRecord(musicianId, "Avery Rivera", accountPrincipal, accountPrincipal, "555-0100", null, null, null, null, true);
    }
}
