package com.cadentia.team;

import com.cadentia.api.security.PersonnelAuthorizationPolicy;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditAction;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditEvent;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditTargetType;
import com.cadentia.team.TeamPlanningModels.AssignmentChangeHistoryRecord;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AssignmentType;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceRoster;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
        return createServiceAssignment(
                servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode, 0, false, reasonCode,
                reference);
    }

    @Transactional
    public ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            int assignmentOrder,
            boolean overrideUnavailable,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        validateServiceAssignment(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode, null,
                overrideUnavailable);
        ServiceAssignmentRecord assignment = repository.createServiceAssignment(
                servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode, assignmentOrder, null);
        recordServiceAssignmentChange(assignment, "CREATE", reasonCode, reference, null, "musicianId,roleCode,instrumentCode,vocalPartCode,statusCode,assignmentOrder");
        return assignment;
    }

    @Transactional
    public ServiceAssignmentRecord updateServiceAssignment(
            UUID assignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            int assignmentOrder,
            boolean overrideUnavailable,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        ServiceAssignmentRecord existing = repository.findServiceAssignment(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service assignment."));
        validateServiceAssignment(existing.servicePlanId(), musicianId, roleCode, instrumentCode, vocalPartCode,
                statusCode, assignmentId, overrideUnavailable);
        ServiceAssignmentRecord updated = repository.updateServiceAssignment(
                        assignmentId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode, assignmentOrder)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service assignment."));
        recordServiceAssignmentChange(updated, "UPDATE", reasonCode, reference,
                snapshotRef("service_team_assignments", existing.assignmentId()),
                "musicianId,roleCode,instrumentCode,vocalPartCode,statusCode,assignmentOrder");
        return updated;
    }

    @Transactional
    public void removeServiceAssignment(UUID assignmentId, String reasonCode, String reference) {
        authorizationPolicy.requireAssignmentManagement();
        ServiceAssignmentRecord existing = repository.findServiceAssignment(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service assignment."));
        repository.removeServiceAssignment(assignmentId);
        recordServiceAssignmentChange(existing, "REMOVE", reasonCode, reference,
                snapshotRef("service_team_assignments", existing.assignmentId()), "statusCode");
    }

    @Transactional
    public List<ServiceAssignmentRecord> reorderServiceAssignments(
            UUID servicePlanId,
            List<UUID> orderedAssignmentIds,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        repository.reorderServiceAssignments(servicePlanId, orderedAssignmentIds);
        List<ServiceAssignmentRecord> roster = repository.listServiceRoster(servicePlanId);
        for (ServiceAssignmentRecord assignment : roster) {
            repository.recordAssignmentHistory(
                    AssignmentType.SERVICE,
                    assignment.assignmentId(),
                    assignment.servicePlanId(),
                    null,
                    assignment.musicianId(),
                    assignment.roleCode(),
                    assignment.instrumentCode(),
                    assignment.vocalPartCode(),
                    assignment.statusCode(),
                    assignment.assignmentOrder(),
                    assignment.substituteForAssignmentId(),
                    null,
                    "REORDER",
                    authorizationPolicy.currentActor(),
                    safeReasonCode(reasonCode),
                    reference);
        }
        return roster;
    }

    @Transactional
    public ServiceAssignmentRecord substituteServiceAssignment(
            UUID originalAssignmentId,
            UUID substituteMusicianId,
            AssignmentStatusCode substituteStatusCode,
            boolean overrideUnavailable,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        ServiceAssignmentRecord original = repository.findServiceAssignment(originalAssignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service assignment."));
        validateServiceAssignment(original.servicePlanId(), substituteMusicianId, original.roleCode(),
                original.instrumentCode(), original.vocalPartCode(), substituteStatusCode, originalAssignmentId,
                overrideUnavailable);
        ServiceAssignmentRecord substitute = repository.createServiceAssignment(
                original.servicePlanId(), substituteMusicianId, original.roleCode(), original.instrumentCode(),
                original.vocalPartCode(), substituteStatusCode, original.assignmentOrder(), original.assignmentId());
        recordServiceAssignmentChange(substitute, "SUBSTITUTE", reasonCode, reference,
                snapshotRef("service_team_assignments", original.assignmentId()),
                "musicianId,substituteForAssignmentId,statusCode");
        return substitute;
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
        return createRehearsalAssignment(rehearsalEventId, servicePlanId, null, musicianId, roleCode, instrumentCode,
                vocalPartCode, statusCode, null, false, reasonCode, reference);
    }

    @Transactional
    public RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID serviceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            UUID substituteForAssignmentId,
            boolean overrideUnavailable,
            String reasonCode,
            String reference) {
        authorizationPolicy.requireAssignmentManagement();
        validateAssignmentVocabularyAndMusician(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode,
                statusCode, overrideUnavailable);
        RehearsalAssignmentRecord assignment = repository.createRehearsalAssignment(rehearsalEventId, servicePlanId,
                serviceAssignmentId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode,
                substituteForAssignmentId);
        record(
                PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                PersonnelAuditTargetType.REHEARSAL_ASSIGNMENT,
                assignment.assignmentId(),
                reasonCode,
                reference,
                null,
                snapshotRef("rehearsal_team_assignments", assignment.assignmentId()),
                "serviceAssignmentId,musicianId,roleCode,instrumentCode,vocalPartCode,statusCode");
        repository.recordAssignmentHistory(AssignmentType.REHEARSAL, assignment.assignmentId(), assignment.servicePlanId(),
                assignment.rehearsalEventId(), assignment.musicianId(), assignment.roleCode(), assignment.instrumentCode(),
                assignment.vocalPartCode(), assignment.statusCode(), null, assignment.substituteForAssignmentId(),
                assignment.serviceAssignmentId(), "CREATE", authorizationPolicy.currentActor(), safeReasonCode(reasonCode),
                reference);
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
        validateAssignmentVocabularyAndMusician(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode,
                statusCode, false);
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
        repository.recordAssignmentHistory(AssignmentType.SONG_OVERRIDE, override.overrideId(), override.servicePlanId(),
                null, override.musicianId(), override.roleCode(), override.instrumentCode(), override.vocalPartCode(),
                override.statusCode(), null, null, override.baseServiceAssignmentId(), "CREATE",
                authorizationPolicy.currentActor(), safeReasonCode(reasonCode), reference);
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
        updated.ifPresent(assignment -> {
            record(
                    PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                    PersonnelAuditTargetType.SERVICE_ASSIGNMENT,
                    assignment.assignmentId(),
                    reasonCode,
                    reference,
                    snapshotRef("service_team_assignments", existing.assignmentId()),
                    snapshotRef("service_team_assignments", assignment.assignmentId()),
                    "statusCode");
            repository.recordAssignmentHistory(AssignmentType.SERVICE, assignment.assignmentId(), assignment.servicePlanId(),
                    null, assignment.musicianId(), assignment.roleCode(), assignment.instrumentCode(),
                    assignment.vocalPartCode(), assignment.statusCode(), assignment.assignmentOrder(),
                    assignment.substituteForAssignmentId(), null, "STATUS", authorizationPolicy.currentActor(),
                    safeReasonCode(reasonCode), reference);
        });
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

    public ServiceRoster getServiceRoster(UUID servicePlanId) {
        authorizationPolicy.requireRosterRead();
        List<ServiceAssignmentRecord> assignments = repository.listServiceRoster(servicePlanId);
        return new ServiceRoster(
                servicePlanId,
                activeAssignments(assignments),
                staffingGaps(assignments),
                availabilityConflicts(assignments));
    }

    public List<ServiceAssignmentRecord> listUpcomingAssignmentsForMusician(UUID musicianId, Instant fromInclusive) {
        MusicianRecord musician = repository.findMusician(musicianId)
                .orElseThrow(() -> new AccessDeniedException(NO_EXISTENCE_LEAK_MESSAGE));
        authorizationPolicy.requireMusicianProfileRead(musician);
        return repository.listUpcomingServiceAssignmentsForMusician(musicianId, fromInclusive);
    }

    public List<AssignmentChangeHistoryRecord> listAssignmentHistory(UUID servicePlanId) {
        authorizationPolicy.requireRosterRead();
        return repository.listAssignmentHistory(servicePlanId);
    }

    private void validateServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            UUID excludingAssignmentId,
            boolean overrideUnavailable) {
        validateAssignmentVocabularyAndMusician(servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode,
                statusCode, overrideUnavailable);
        if (repository.hasDuplicateServicePosition(servicePlanId, roleCode, instrumentCode, vocalPartCode,
                excludingAssignmentId)) {
            throw new IllegalArgumentException("Duplicate mutually exclusive service position.");
        }
    }

    private void validateAssignmentVocabularyAndMusician(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            boolean overrideUnavailable) {
        if (!repository.isActiveMusician(musicianId)) {
            throw new IllegalArgumentException("Cannot assign an inactive or unknown musician.");
        }
        if (!repository.isActiveVocabularyValue("musician_roles", roleCode == null ? null : roleCode.name())
                || !repository.isActiveVocabularyValue("instruments", instrumentCode == null ? null : instrumentCode.name())
                || !repository.isActiveVocabularyValue("vocal_parts", vocalPartCode == null ? null : vocalPartCode.name())
                || !repository.isActiveVocabularyValue("assignment_statuses", statusCode == null ? null : statusCode.name())) {
            throw new IllegalArgumentException("Cannot assign inactive controlled vocabulary values.");
        }
        if (!overrideUnavailable && repository.hasUnavailableWindow(musicianId, servicePlanId)) {
            throw new IllegalArgumentException("Musician is unavailable for this service without override.");
        }
    }

    private List<ServiceAssignmentRecord> activeAssignments(List<ServiceAssignmentRecord> assignments) {
        return assignments.stream()
                .filter(assignment -> assignment.statusCode() != AssignmentStatusCode.DECLINED
                        && assignment.statusCode() != AssignmentStatusCode.UNAVAILABLE)
                .filter(assignment -> assignments.stream()
                        .noneMatch(candidate -> assignment.assignmentId().equals(candidate.substituteForAssignmentId())
                                && candidate.statusCode() != AssignmentStatusCode.DECLINED
                                && candidate.statusCode() != AssignmentStatusCode.UNAVAILABLE))
                .sorted(Comparator.comparing(assignment -> assignment.assignmentOrder() == null ? 0 : assignment.assignmentOrder()))
                .toList();
    }

    private List<String> staffingGaps(List<ServiceAssignmentRecord> assignments) {
        List<ServiceAssignmentRecord> active = activeAssignments(assignments);
        List<String> gaps = new ArrayList<>();
        if (active.stream().noneMatch(assignment -> assignment.roleCode() == MusicianRoleCode.WORSHIP_LEADER)) {
            gaps.add("WORSHIP_LEADER");
        }
        if (active.stream().noneMatch(assignment -> assignment.instrumentCode() == InstrumentCode.DRUMS)) {
            gaps.add("DRUMS");
        }
        if (active.stream().noneMatch(assignment -> assignment.instrumentCode() == InstrumentCode.BASS)) {
            gaps.add("BASS");
        }
        return gaps;
    }

    private List<String> availabilityConflicts(List<ServiceAssignmentRecord> assignments) {
        return activeAssignments(assignments).stream()
                .filter(assignment -> repository.hasUnavailableWindow(assignment.musicianId(), assignment.servicePlanId()))
                .map(assignment -> assignment.assignmentId().toString())
                .toList();
    }

    private void recordServiceAssignmentChange(
            ServiceAssignmentRecord assignment,
            String changeAction,
            String reasonCode,
            String reference,
            String beforeStateRef,
            String changedFields) {
        record(
                changeAction.equals("SUBSTITUTE")
                        ? PersonnelAuditAction.PERSONNEL_SUBSTITUTION_CHANGED
                        : PersonnelAuditAction.PERSONNEL_ASSIGNMENT_CHANGED,
                PersonnelAuditTargetType.SERVICE_ASSIGNMENT,
                assignment.assignmentId(),
                reasonCode,
                reference,
                beforeStateRef,
                snapshotRef("service_team_assignments", assignment.assignmentId()),
                changedFields);
        repository.recordAssignmentHistory(AssignmentType.SERVICE, assignment.assignmentId(), assignment.servicePlanId(),
                null, assignment.musicianId(), assignment.roleCode(), assignment.instrumentCode(),
                assignment.vocalPartCode(), assignment.statusCode(), assignment.assignmentOrder(),
                assignment.substituteForAssignmentId(), null, changeAction, authorizationPolicy.currentActor(),
                safeReasonCode(reasonCode), reference);
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
        String safeReasonCode = safeReasonCode(reasonCode);
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

    private String safeReasonCode(String reasonCode) {
        return reasonCode == null || reasonCode.isBlank() ? "unspecified" : reasonCode.trim();
    }

    private String snapshotRef(String tableName, UUID id) {
        return id == null ? null : "db://" + tableName + "/" + id;
    }

    private String hashRef(String ref) {
        return ref == null ? null : Integer.toHexString(ref.hashCode());
    }
}
