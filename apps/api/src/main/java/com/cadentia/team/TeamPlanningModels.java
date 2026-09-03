package com.cadentia.team;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamPlanningModels {

    private TeamPlanningModels() {
    }

    public enum MusicianRoleCode {
        WORSHIP_LEADER,
        VOCALIST,
        INSTRUMENTALIST,
        MUSIC_DIRECTOR,
        TECH
    }

    public enum InstrumentCode {
        ACOUSTIC_GUITAR,
        ELECTRIC_GUITAR,
        PIANO,
        KEYS,
        BASS,
        DRUMS,
        PERCUSSION,
        BRASS,
        WINDS,
        OTHER
    }

    public enum VocalPartCode {
        LEAD,
        ALTO,
        TENOR,
        BARITONE,
        SOPRANO,
        BACKGROUND
    }

    public enum VocalRangeCode {
        LOW,
        MEDIUM,
        HIGH,
        UNKNOWN
    }

    public enum SkillLevelCode {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED,
        DIRECTOR
    }

    public enum ServingPreferenceCode {
        PREFERRED,
        AVAILABLE,
        LIMITED,
        DO_NOT_SCHEDULE
    }

    public enum AssignmentStatusCode {
        REQUESTED,
        TENTATIVE,
        ACCEPTED,
        DECLINED,
        UNAVAILABLE,
        SUBSTITUTE
    }

    public enum AssignmentType {
        SERVICE,
        REHEARSAL,
        SONG_OVERRIDE
    }

    public record ControlledVocabularyEntry(
            String code,
            String displayName,
            boolean active,
            int sortOrder,
            boolean systemDefault,
            boolean localExtension) {
    }

    public record CreateMusicianCommand(
            String displayName,
            String accountPrincipal,
            String email,
            String phone,
            VocalRangeCode primaryVocalRangeCode,
            Integer comfortableLowMidiNote,
            Integer comfortableHighMidiNote,
            ServingPreferenceCode servingPreferenceCode,
            String createdBy) {
    }

    public record MusicianRecord(
            UUID musicianId,
            String displayName,
            String accountPrincipal,
            String email,
            String phone,
            VocalRangeCode primaryVocalRangeCode,
            Integer comfortableLowMidiNote,
            Integer comfortableHighMidiNote,
            ServingPreferenceCode servingPreferenceCode,
            boolean active) {
    }

    public enum SkillAssignmentDomain {
        ROLE,
        INSTRUMENT,
        VOCAL_PART
    }

    public record MusicianSkillAssignmentRecord(
            UUID assignmentId,
            UUID musicianId,
            SkillAssignmentDomain domain,
            String code,
            SkillLevelCode skillLevelCode) {
    }

    public record AvailabilityWindowRecord(
            UUID availabilityWindowId,
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId) {
    }

    public record ServiceAssignmentRecord(
            UUID assignmentId,
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            Integer assignmentOrder,
            UUID substituteForAssignmentId) {

        public ServiceAssignmentRecord(
                UUID assignmentId,
                UUID servicePlanId,
                UUID musicianId,
                MusicianRoleCode roleCode,
                InstrumentCode instrumentCode,
                VocalPartCode vocalPartCode,
                AssignmentStatusCode statusCode) {
            this(assignmentId, servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode, statusCode, 0, null);
        }
    }

    public record RehearsalEventRecord(
            UUID rehearsalEventId,
            UUID servicePlanId,
            Instant startsAt,
            Instant endsAt,
            String location) {
    }

    public record RehearsalAssignmentRecord(
            UUID assignmentId,
            UUID rehearsalEventId,
            UUID servicePlanId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            UUID serviceAssignmentId,
            UUID substituteForAssignmentId) {

        public RehearsalAssignmentRecord(
                UUID assignmentId,
                UUID rehearsalEventId,
                UUID servicePlanId,
                UUID musicianId,
                MusicianRoleCode roleCode,
                InstrumentCode instrumentCode,
                VocalPartCode vocalPartCode,
                AssignmentStatusCode statusCode) {
            this(assignmentId, rehearsalEventId, servicePlanId, musicianId, roleCode, instrumentCode, vocalPartCode,
                    statusCode, null, null);
        }
    }

    public record SongAssignmentOverrideRecord(
            UUID overrideId,
            UUID servicePlanId,
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode) {
    }

    public record ServiceRoster(
            UUID servicePlanId,
            List<ServiceAssignmentRecord> assignments,
            List<String> staffingGaps,
            List<String> availabilityConflicts) {
    }

    public record AssignmentChangeHistoryRecord(
            UUID historyId,
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
            String reference,
            Instant changedAt) {
    }
}
