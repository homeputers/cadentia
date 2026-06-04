package com.cadentia.team;

import java.time.Instant;
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
            AssignmentStatusCode statusCode) {
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
            AssignmentStatusCode statusCode) {
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
}
