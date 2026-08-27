package com.cadentia.team;

import com.cadentia.team.TeamPlanningModels.AssignmentChangeHistoryRecord;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AssignmentType;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.ControlledVocabularyEntry;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.MusicianSkillAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import com.cadentia.team.ReadinessModels.ReadinessNoteRecord;
import com.cadentia.team.ReadinessModels.RecordReadinessCommand;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamPlanningRepository {

    List<ControlledVocabularyEntry> listAssignmentStatuses();

    List<ControlledVocabularyEntry> listInstruments();

    List<ControlledVocabularyEntry> listMusicianRoles();

    MusicianRecord createMusician(CreateMusicianCommand command);

    Optional<MusicianRecord> findMusician(UUID musicianId);

    List<MusicianRecord> listMusicians();

    UUID assignRole(UUID musicianId, MusicianRoleCode roleCode, SkillLevelCode skillLevelCode);

    UUID assignInstrument(UUID musicianId, InstrumentCode instrumentCode, SkillLevelCode skillLevelCode);

    UUID assignVocalPart(UUID musicianId, VocalPartCode vocalPartCode, SkillLevelCode skillLevelCode);

    List<MusicianSkillAssignmentRecord> listMusicianSkillAssignments(UUID musicianId);

    AvailabilityWindowRecord createAvailabilityWindow(
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId);

    boolean isActiveMusician(UUID musicianId);

    boolean isActiveVocabularyValue(String tableName, String code);

    boolean hasUnavailableWindow(UUID musicianId, UUID servicePlanId);

    boolean hasDuplicateServicePosition(
            UUID servicePlanId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            UUID excludingAssignmentId);

    ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            int assignmentOrder,
            UUID substituteForAssignmentId);

    default ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        return createServiceAssignment(
                servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode, 0, null);
    }

    Optional<ServiceAssignmentRecord> findServiceAssignment(UUID assignmentId);

    List<ServiceAssignmentRecord> listServiceRoster(UUID servicePlanId);

    List<ServiceAssignmentRecord> listUpcomingServiceAssignmentsForMusician(UUID musicianId, Instant fromInclusive);

    Optional<ServiceAssignmentRecord> updateServiceAssignmentStatus(UUID assignmentId, AssignmentStatusCode statusCode);

    Optional<ServiceAssignmentRecord> updateServiceAssignment(
            UUID assignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            int assignmentOrder);

    boolean removeServiceAssignment(UUID assignmentId);

    void reorderServiceAssignments(UUID servicePlanId, List<UUID> orderedAssignmentIds);

    RehearsalEventRecord createRehearsalEvent(
            UUID servicePlanId,
            Instant startsAt,
            Instant endsAt,
            String location);

    List<RehearsalEventRecord> listRehearsalEvents(UUID servicePlanId);

    RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID serviceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            UUID substituteForAssignmentId);

    default RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
        return createRehearsalAssignment(
                rehearsalEventId, servicePlanId, null, musicianId, roleCode, instrumentCode, vocalPartCode,
                statusCode, null);
    }

    SongAssignmentOverrideRecord createSongAssignmentOverride(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode);

    void recordAssignmentHistory(
            AssignmentType assignmentType,
            UUID assignmentId,
            UUID servicePlanId,
            UUID rehearsalEventId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            Integer assignmentOrder,
            UUID substituteForAssignmentId,
            UUID serviceAssignmentId,
            String changeAction,
            String changedBy,
            String reasonCode,
            String reference);

    List<AssignmentChangeHistoryRecord> listAssignmentHistory(UUID servicePlanId);

    ReadinessNoteRecord recordReadiness(RecordReadinessCommand command);

    List<ReadinessNoteRecord> listReadinessNotes(UUID servicePlanId);
}
