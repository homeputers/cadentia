package com.cadentia.team;

import com.cadentia.api.security.PersonnelAuthorizationPolicy;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditAction;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditTargetType;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizedTeamPlanningService {

    private static final String NO_EXISTENCE_LEAK_MESSAGE = "Access denied.";

    private final TeamPlanningRepository repository;
    private final PersonnelAuthorizationPolicy authorizationPolicy;
    private final PersonnelDataRedactor redactor;
    private final PersonnelAuditRecorder auditService;

    public AuthorizedTeamPlanningService(
            TeamPlanningRepository repository,
            PersonnelAuthorizationPolicy authorizationPolicy,
            PersonnelDataRedactor redactor,
            PersonnelAuditRecorder auditService) {
        this.repository = repository;
        this.authorizationPolicy = authorizationPolicy;
        this.redactor = redactor;
        this.auditService = auditService;
    }

    public Optional<MusicianRecord> findMusicianForRoster(UUID musicianId) {
        authorizationPolicy.requireRosterRead();
        return repository.findMusician(musicianId).map(redactor::redact);
    }

    public Optional<MusicianRecord> findMusicianProfile(UUID musicianId) {
        Optional<MusicianRecord> musician = repository.findMusician(musicianId);
        if (musician.isEmpty()) {
            return Optional.empty();
        }
        try {
            authorizationPolicy.requireMusicianProfileRead(musician.get());
        } catch (AccessDeniedException exception) {
            throw new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE);
        }
        return musician.map(redactor::redact);
    }

    @Transactional
    public MusicianRecord createMusician(CreateMusicianCommand command, String reasonCode, String reference) {
        authorizationPolicy.requireSkillRangeMaintenance();
        MusicianRecord musician = repository.createMusician(command);
        record(
                PersonnelAuditAction.PERSONNEL_CONTACT_CHANGED,
                PersonnelAuditTargetType.MUSICIAN,
                musician.musicianId(),
                reasonCode,
                reference,
                null,
                snapshotRef("musicians", musician.musicianId()),
                "contact,account,servingPreference");
        record(
                PersonnelAuditAction.PERSONNEL_VOCAL_RANGE_CHANGED,
                PersonnelAuditTargetType.MUSICIAN,
                musician.musicianId(),
                reasonCode,
                reference,
                null,
                snapshotRef("musicians", musician.musicianId()),
                "primaryVocalRange,comfortableRange");
        return redactor.redact(musician);
    }

    @Transactional
    public UUID assignRole(
            UUID musicianId,
            MusicianRoleCode roleCode,
            SkillLevelCode skillLevelCode,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireSkillRangeMaintenance();
        UUID assignmentId = repository.assignRole(musicianId, roleCode, skillLevelCode);
        record(
                PersonnelAuditAction.PERSONNEL_ROLE_CHANGED,
                PersonnelAuditTargetType.MUSICIAN,
                musicianId,
                reasonCode,
                reference,
                null,
                snapshotRef("musician_role_assignments", assignmentId),
                "roleCode");
        record(
                PersonnelAuditAction.PERSONNEL_SKILL_LEVEL_CHANGED,
                PersonnelAuditTargetType.MUSICIAN,
                musicianId,
                reasonCode,
                reference,
                null,
                snapshotRef("musician_role_assignments", assignmentId),
                "skillLevelCode");
        return assignmentId;
    }

    @Transactional
    public UUID assignInstrument(
            UUID musicianId,
            InstrumentCode instrumentCode,
            SkillLevelCode skillLevelCode,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireSkillRangeMaintenance();
        UUID assignmentId = repository.assignInstrument(musicianId, instrumentCode, skillLevelCode);
        record(
                PersonnelAuditAction.PERSONNEL_SKILL_LEVEL_CHANGED,
                PersonnelAuditTargetType.MUSICIAN,
                musicianId,
                reasonCode,
                reference,
                null,
                snapshotRef("musician_instrument_assignments", assignmentId),
                "instrumentCode,skillLevelCode");
        return assignmentId;
    }

    @Transactional
    public UUID assignVocalPart(
            UUID musicianId,
            VocalPartCode vocalPartCode,
            SkillLevelCode skillLevelCode,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireSkillRangeMaintenance();
        UUID assignmentId = repository.assignVocalPart(musicianId, vocalPartCode, skillLevelCode);
        record(
                PersonnelAuditAction.PERSONNEL_SKILL_LEVEL_CHANGED,
                PersonnelAuditTargetType.MUSICIAN,
                musicianId,
                reasonCode,
                reference,
                null,
                snapshotRef("musician_vocal_part_assignments", assignmentId),
                "vocalPartCode,skillLevelCode");
        return assignmentId;
    }

    @Transactional
    public AvailabilityWindowRecord createAvailabilityWindow(
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId,
            String reasonCode,
            String reference) {
        MusicianRecord musician = repository.findMusician(musicianId)
                .orElseThrow(() -> new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE));
        authorizationPolicy.requireAvailabilityManagement(musician);
        AvailabilityWindowRecord window = repository.createAvailabilityWindow(
                musicianId, startsAt, endsAt, statusCode, servicePlanId);
        record(
                PersonnelAuditAction.PERSONNEL_AVAILABILITY_CHANGED,
                PersonnelAuditTargetType.AVAILABILITY_WINDOW,
                window.availabilityWindowId(),
                reasonCode,
                reference,
                null,
                snapshotRef("musician_availability_windows", window.availabilityWindowId()),
                "statusCode,startsAt,endsAt,servicePlanId");
        return window;
    }

    @Transactional
    public ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        ServiceAssignmentRecord assignment = repository.createServiceAssignment(
                servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode);
        record(
                PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                PersonnelAuditTargetType.SERVICE_ASSIGNMENT,
                assignment.assignmentId(),
                reasonCode,
                reference,
                null,
                snapshotRef("service_team_assignments", assignment.assignmentId()),
                "musicianId,roleCode,instrumentCode,vocalPartCode,statusCode");
        return assignment;
    }

    @Transactional
    public RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        RehearsalAssignmentRecord assignment = repository.createRehearsalAssignment(
                rehearsalEventId, servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode);
        record(
                PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                PersonnelAuditTargetType.REHEARSAL_ASSIGNMENT,
                assignment.assignmentId(),
                reasonCode,
                reference,
                null,
                snapshotRef("rehearsal_team_assignments", assignment.assignmentId()),
                "musicianId,roleCode,instrumentCode,vocalPartCode,statusCode");
        return assignment;
    }

    @Transactional
    public SongAssignmentOverrideRecord createSongAssignmentOverride(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        SongAssignmentOverrideRecord override = repository.createSongAssignmentOverride(
                servicePlanId,
                servicePlanBlockId,
                baseServiceAssignmentId,
                musicianId,
                roleCode,
                instrumentCode,
                vocalPartCode,
                statusCode);
        record(
                statusCode == AssignmentStatusCode.SUBSTITUTE
                        ? PersonnelAuditAction.PERSONNEL_SUBSTITUTION_CHANGED
                        : PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                PersonnelAuditTargetType.SONG_ASSIGNMENT_OVERRIDE,
                override.overrideId(),
                reasonCode,
                reference,
                snapshotRef("service_team_assignments", baseServiceAssignmentId),
                snapshotRef("service_song_assignment_overrides", override.overrideId()),
                "musicianId,roleCode,instrumentCode,vocalPartCode,statusCode");
        return override;
    }

    public List<ServiceAssignmentRecord> listOwnUpcomingServiceAssignments(
            UUID musicianId, Instant fromInclusive) {
        MusicianRecord musician = repository.findMusician(musicianId)
                .orElseThrow(() -> new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE));
        authorizationPolicy.requireSelfServiceAssignmentResponse(musician);
        return repository.listUpcomingServiceAssignmentsForMusician(musicianId, fromInclusive);
    }

    @Transactional
    public Optional<ServiceAssignmentRecord> updateOwnServiceAssignmentResponse(
            UUID assignmentId, AssignmentStatusCode statusCode, String reasonCode, String reference) {
        ServiceAssignmentRecord existing = repository.findServiceAssignment(assignmentId)
                .orElseThrow(() -> new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE));
        MusicianRecord musician = repository.findMusician(existing.musicianId())
                .orElseThrow(() -> new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE));
        authorizationPolicy.requireSelfServiceAssignmentResponse(musician);
        Optional<ServiceAssignmentRecord> updated = repository.updateServiceAssignmentStatus(assignmentId, statusCode);
        updated.ifPresent(assignment -> record(
                PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                PersonnelAuditTargetType.SERVICE_ASSIGNMENT,
                assignment.assignmentId(),
                reasonCode,
                reference,
                snapshotRef("service_team_assignments", existing.assignmentId()),
                snapshotRef("service_team_assignments", assignment.assignmentId()),
                "statusCode"));
        return updated;
    }

    public void requireSelfServiceAssignmentResponse(UUID musicianId) {
        MusicianRecord musician = repository.findMusician(musicianId)
                .orElseThrow(() -> new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE));
        authorizationPolicy.requireSelfServiceAssignmentResponse(musician);
    }

    @Transactional
    public void recordTeamReadinessUpdate(UUID servicePlanId, String reasonCode, String reference) {
        authorizationPolicy.requireTeamReadinessUpdate();
        record(
                PersonnelAuditAction.PERSONNEL_READINESS_NOTE_CHANGED,
                PersonnelAuditTargetType.SERVICE_TEAM_READINESS,
                servicePlanId,
                reasonCode,
                reference,
                snapshotRef("service_team_readiness", servicePlanId),
                snapshotRef("service_team_readiness", servicePlanId),
                "readinessNoteRef");
    }

    private void record(
            PersonnelAuditAction action,
            PersonnelAuditTargetType targetType,
            UUID targetId,
            String reasonCode,
            String reference,
            String beforeStateRef,
            String afterStateRef,
            String changedFields) {
        String safeReasonCode = reasonCode == null || reasonCode.isBlank() ? "unspecified" : reasonCode.trim();
        auditService.record(new PersonnelAuditEvent(
                authorizationPolicy.currentActor(),
                authorizationPolicy.currentActorRoles(),
                action,
                targetType,
                targetId,
                safeReasonCode,
                reference,
                beforeStateRef,
                afterStateRef,
                hashRef(beforeStateRef),
                hashRef(afterStateRef),
                Map.of("fields", changedFields)));
    }

    private String snapshotRef(String tableName, UUID id) {
        return id == null ? null : "db://" + tableName + "/" + id;
    }

    private String hashRef(String ref) {
        return ref == null ? null : Integer.toHexString(ref.hashCode());
    }
}
