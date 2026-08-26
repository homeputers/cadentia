package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.api.security.PersonnelAuthorizationPolicy;
import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.api.security.SecurityObservabilityRecorder;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditAction;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditTargetType;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
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
        lenient().when(repository.isActiveMusician(any())).thenReturn(true);
        lenient().when(repository.isActiveVocabularyValue(anyString(), any())).thenReturn(true);
        lenient().when(repository.hasUnavailableWindow(any(), any())).thenReturn(false);
        lenient().when(repository.hasDuplicateServicePosition(any(), any(), any(), any(), any())).thenReturn(false);
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
                        AssignmentStatusCode.REQUESTED,
                        0,
                        null))
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
    void schedulerCannotAssignUnavailableMusicianWithoutOverride() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        UUID musicianId = UUID.randomUUID();
        authenticate("scheduler", RbacAuthorities.ROLE_TEAM_SCHEDULER);
        when(repository.hasUnavailableWindow(musicianId, servicePlanId)).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> service.createServiceAssignment(
                        servicePlanId,
                        musicianId,
                        MusicianRoleCode.INSTRUMENTALIST,
                        InstrumentCode.BASS,
                        null,
                        AssignmentStatusCode.REQUESTED,
                        "weekly_schedule",
                        "req-456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Musician is unavailable for this service without override.");
    }

    @Test
    void substituteAssignmentPreservesOriginalAndMarksActiveMusicianDeterministically() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        UUID originalMusicianId = UUID.randomUUID();
        UUID substituteMusicianId = UUID.randomUUID();
        UUID originalAssignmentId = UUID.randomUUID();
        UUID substituteAssignmentId = UUID.randomUUID();
        authenticate("scheduler", RbacAuthorities.ROLE_TEAM_SCHEDULER);
        ServiceAssignmentRecord original = new ServiceAssignmentRecord(
                originalAssignmentId,
                servicePlanId,
                originalMusicianId,
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.DRUMS,
                null,
                AssignmentStatusCode.ACCEPTED,
                2,
                null);
        ServiceAssignmentRecord substitute = new ServiceAssignmentRecord(
                substituteAssignmentId,
                servicePlanId,
                substituteMusicianId,
                MusicianRoleCode.INSTRUMENTALIST,
                InstrumentCode.DRUMS,
                null,
                AssignmentStatusCode.SUBSTITUTE,
                2,
                originalAssignmentId);
        when(repository.findServiceAssignment(originalAssignmentId)).thenReturn(Optional.of(original));
        when(repository.createServiceAssignment(
                        servicePlanId,
                        substituteMusicianId,
                        MusicianRoleCode.INSTRUMENTALIST,
                        InstrumentCode.DRUMS,
                        null,
                        AssignmentStatusCode.SUBSTITUTE,
                        2,
                        originalAssignmentId))
                .thenReturn(substitute);
        when(repository.listServiceRoster(servicePlanId)).thenReturn(List.of(original, substitute));

        // Act
        ServiceAssignmentRecord result = service.substituteServiceAssignment(
                originalAssignmentId,
                substituteMusicianId,
                AssignmentStatusCode.SUBSTITUTE,
                false,
                "late_change",
                "req-789");

        // Assert
        assertThat(result.substituteForAssignmentId()).isEqualTo(originalAssignmentId);
        assertThat(service.getServiceRoster(servicePlanId).assignments())
                .extracting(ServiceAssignmentRecord::musicianId)
                .containsExactly(substituteMusicianId);
        verify(repository).recordAssignmentHistory(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.eq("SUBSTITUTE"),
                any(),
                any(),
                any());
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

    @Test
    void rosterReaderListsMusiciansWithRedactedContactAndSkillFields() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        authenticate("reporter", RbacAuthorities.ROLE_REPORTING_VIEWER);
        when(repository.listMusicians()).thenReturn(List.of(new MusicianRecord(
                musicianId,
                "Avery Rivera",
                "avery@example.test",
                "avery@example.test",
                "555-0100",
                VocalRangeCode.MEDIUM,
                48,
                72,
                ServingPreferenceCode.PREFERRED,
                true)));

        // Act
        List<MusicianRecord> musicians = service.listMusiciansForRoster();

        // Assert
        assertThat(musicians).singleElement().satisfies(musician -> {
            assertThat(musician.displayName()).isEqualTo("Avery Rivera");
            assertThat(musician.accountPrincipal()).isNull();
            assertThat(musician.email()).isNull();
            assertThat(musician.phone()).isNull();
            assertThat(musician.primaryVocalRangeCode()).isNull();
            assertThat(musician.comfortableLowMidiNote()).isNull();
            assertThat(musician.comfortableHighMidiNote()).isNull();
            assertThat(musician.servingPreferenceCode()).isNull();
            assertThat(musician.active()).isTrue();
        });
    }

    @Test
    void schedulerListsMusiciansWithContactAndSkillFieldsVisible() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        authenticate("scheduler", RbacAuthorities.ROLE_TEAM_SCHEDULER);
        when(repository.listMusicians()).thenReturn(List.of(musician(musicianId, "avery@example.test")));

        // Act
        List<MusicianRecord> musicians = service.listMusiciansForRoster();

        // Assert
        assertThat(musicians).singleElement().satisfies(musician ->
                assertThat(musician.email()).isEqualTo("avery@example.test"));
    }

    @Test
    void assignedMusicianCannotListMusicianDirectory() {
        // Arrange
        authenticate("avery@example.test", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act / Assert
        assertThatThrownBy(() -> service.listMusiciansForRoster())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");
    }

    @Test
    void schedulerCreatesRehearsalEventAndWritesPrivilegedAudit() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        UUID rehearsalEventId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-06-04T23:00:00Z");
        Instant endsAt = Instant.parse("2026-06-05T01:00:00Z");
        authenticate("scheduler", RbacAuthorities.ROLE_TEAM_SCHEDULER);
        when(repository.createRehearsalEvent(servicePlanId, startsAt, endsAt, "Sanctuary"))
                .thenReturn(new RehearsalEventRecord(rehearsalEventId, servicePlanId, startsAt, endsAt, "Sanctuary"));

        // Act
        RehearsalEventRecord result = service.createRehearsalEvent(
                servicePlanId, startsAt, endsAt, "Sanctuary", "weekly_schedule", "req-321");

        // Assert
        assertThat(result.rehearsalEventId()).isEqualTo(rehearsalEventId);
        verify(auditService).record(auditEventCaptor.capture());
        PersonnelAuditEvent auditEvent = auditEventCaptor.getValue();
        assertThat(auditEvent.action()).isEqualTo(PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED);
        assertThat(auditEvent.targetType()).isEqualTo(PersonnelAuditTargetType.REHEARSAL_EVENT);
        assertThat(auditEvent.targetId()).isEqualTo(rehearsalEventId);
        assertThat(auditEvent.actor()).isEqualTo("scheduler");
        assertThat(auditEvent.reasonCode()).isEqualTo("weekly_schedule");
        assertThat(auditEvent.afterStateRef()).contains(rehearsalEventId.toString());
    }

    @Test
    void assignedMusicianCannotCreateRehearsalEvent() {
        // Arrange
        authenticate("avery@example.test", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act / Assert
        assertThatThrownBy(() -> service.createRehearsalEvent(
                        UUID.randomUUID(),
                        Instant.parse("2026-06-04T23:00:00Z"),
                        Instant.parse("2026-06-05T01:00:00Z"),
                        "Sanctuary",
                        "attempt",
                        "portal"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");
        verify(auditService, never()).record(any());
    }

    @Test
    void listRehearsalEventsRequiresRosterRead() {
        // Arrange
        authenticate("avery@example.test", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act / Assert
        assertThatThrownBy(() -> service.listRehearsalEvents(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");
    }

    @Test
    void worshipLeaderCreatesMusicianDefaultingCreatorToCurrentActorWithAudit() {
        // Arrange
        authenticate("leader", RbacAuthorities.ROLE_WORSHIP_LEADER);
        when(repository.createMusician(any())).thenAnswer(invocation -> {
            CreateMusicianCommand command = invocation.getArgument(0);
            return new MusicianRecord(
                    UUID.randomUUID(),
                    command.displayName(),
                    command.accountPrincipal(),
                    command.email(),
                    command.phone(),
                    command.primaryVocalRangeCode(),
                    command.comfortableLowMidiNote(),
                    command.comfortableHighMidiNote(),
                    command.servingPreferenceCode(),
                    true);
        });

        // Act
        MusicianRecord result = service.createMusician(
                new CreateMusicianCommand(
                        "Jordan Lee", null, null, null, null, null, null, ServingPreferenceCode.AVAILABLE, null),
                "roster_onboarding",
                "req-654");

        // Assert
        assertThat(result.displayName()).isEqualTo("Jordan Lee");
        ArgumentCaptor<CreateMusicianCommand> commandCaptor = ArgumentCaptor.forClass(CreateMusicianCommand.class);
        verify(repository).createMusician(commandCaptor.capture());
        assertThat(commandCaptor.getValue().createdBy()).isEqualTo("leader");
        verify(auditService, times(2)).record(auditEventCaptor.capture());
        assertThat(auditEventCaptor.getAllValues())
                .extracting(PersonnelAuditEvent::targetType)
                .containsOnly(PersonnelAuditTargetType.MUSICIAN);
    }

    private void authenticate(String principal, String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(principal, "n/a", List.of(() -> authority)));
    }

    private MusicianRecord musician(UUID musicianId, String accountPrincipal) {
        return new MusicianRecord(musicianId, "Avery Rivera", accountPrincipal, accountPrincipal, "555-0100", null, null, null, null, true);
    }
}
