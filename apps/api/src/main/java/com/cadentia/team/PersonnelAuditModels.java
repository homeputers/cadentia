package com.cadentia.team;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PersonnelAuditModels {

    private PersonnelAuditModels() {
    }

    public record PersonnelAuditEvent(
            UUID id,
            String actor,
            Set<String> actorRoles,
            PersonnelAuditAction action,
            PersonnelAuditTargetType targetType,
            UUID targetId,
            Instant occurredAt,
            String reasonCode,
            String reference,
            String beforeStateRef,
            String afterStateRef,
            String beforeStateHash,
            String afterStateHash,
            Map<String, String> changedFields) {

        public PersonnelAuditEvent(
                String actor,
                Set<String> actorRoles,
                PersonnelAuditAction action,
                PersonnelAuditTargetType targetType,
                UUID targetId,
                String reasonCode,
                String reference,
                String beforeStateRef,
                String afterStateRef,
                String beforeStateHash,
                String afterStateHash,
                Map<String, String> changedFields) {
            this(
                    UUID.randomUUID(),
                    actor,
                    actorRoles == null ? Set.of() : Set.copyOf(actorRoles),
                    action,
                    targetType,
                    targetId,
                    Instant.now(),
                    reasonCode,
                    reference,
                    beforeStateRef,
                    afterStateRef,
                    beforeStateHash,
                    afterStateHash,
                    changedFields == null ? Map.of() : Map.copyOf(changedFields));
        }
    }

    public enum PersonnelAuditAction {
        PERSONNEL_ROLE_CHANGED,
        PERSONNEL_SKILL_LEVEL_CHANGED,
        PERSONNEL_VOCAL_RANGE_CHANGED,
        PERSONNEL_CONTACT_CHANGED,
        PERSONNEL_AVAILABILITY_CHANGED,
        PERSONNEL_ASSIGNMENT_CHANGED,
        PERSONNEL_SUBSTITUTION_CHANGED,
        PERSONNEL_READINESS_NOTE_CHANGED,
        PERSONNEL_READINESS_OVERRIDE_CHANGED
    }

    public enum PersonnelAuditTargetType {
        MUSICIAN,
        AVAILABILITY_WINDOW,
        SERVICE_ASSIGNMENT,
        REHEARSAL_ASSIGNMENT,
        SONG_ASSIGNMENT_OVERRIDE,
        SERVICE_TEAM_READINESS,
        READINESS_NOTE
    }
}
