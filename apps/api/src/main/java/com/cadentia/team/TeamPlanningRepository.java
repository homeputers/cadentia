package com.cadentia.team;

import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.ControlledVocabularyEntry;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
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

    UUID assignRole(UUID musicianId, MusicianRoleCode roleCode, SkillLevelCode skillLevelCode);

    UUID assignInstrument(UUID musicianId, InstrumentCode instrumentCode, SkillLevelCode skillLevelCode);

    UUID assignVocalPart(UUID musicianId, VocalPartCode vocalPartCode, SkillLevelCode skillLevelCode);

    AvailabilityWindowRecord createAvailabilityWindow(
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId);

    ServiceAssignmentRecord createServiceAssignment(
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode);

    Optional<ServiceAssignmentRecord> findServiceAssignment(UUID assignmentId);

    List<ServiceAssignmentRecord> listUpcomingServiceAssignmentsForMusician(UUID musicianId, Instant fromInclusive);

    Optional<ServiceAssignmentRecord> updateServiceAssignmentStatus(UUID assignmentId, AssignmentStatusCode statusCode);

    RehearsalEventRecord createRehearsalEvent(
            UUID servicePlanId,
            Instant startsAt,
            Instant endsAt,
            String location);

    RehearsalAssignmentRecord createRehearsalAssignment(
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode);

    SongAssignmentOverrideRecord createSongAssignmentOverride(
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode);
}
