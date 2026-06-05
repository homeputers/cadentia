package com.cadentia.team;

import com.cadentia.serviceplan.ServicePlanModels.ReadinessStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReadinessModels {

    private ReadinessModels() {
    }

    public enum ReadinessScopeType {
        SERVICE_TEAM,
        REHEARSAL,
        MUSICIAN_ASSIGNMENT,
        SONG_ASSIGNMENT,
        ARRANGEMENT_CONFLICT
    }

    public enum ReadinessPrivacyClassification {
        PUBLIC,
        TEAM_PRIVATE,
        PASTORAL_PRIVATE
    }

    public enum RehearsalResponseState {
        NOT_REQUESTED,
        REQUESTED,
        ACKNOWLEDGED,
        ATTENDED,
        ABSENT,
        DECLINED,
        FOLLOW_UP_REQUIRED
    }

    public enum ReadinessAudience {
        PUBLIC,
        ASSIGNED_MUSICIAN,
        TEAM_LEADER,
        ADMIN
    }

    public record RecordReadinessCommand(
            ReadinessScopeType scopeType,
            UUID scopeId,
            UUID servicePlanId,
            UUID rehearsalEventId,
            UUID serviceAssignmentId,
            UUID songAssignmentOverrideId,
            UUID servicePlanBlockId,
            UUID arrangementId,
            ReadinessStatus readinessStatus,
            List<String> objectiveBlockers,
            List<String> missingPeople,
            List<String> unresolvedArrangementConflicts,
            RehearsalResponseState rehearsalResponseState,
            String humanNote,
            ReadinessPrivacyClassification privacyClassification,
            boolean overrideAction,
            String updatedBy) {
    }

    public record ReadinessNoteRecord(
            UUID readinessNoteId,
            ReadinessScopeType scopeType,
            UUID scopeId,
            UUID servicePlanId,
            ReadinessStatus readinessStatus,
            List<String> objectiveBlockers,
            List<String> missingPeople,
            List<String> unresolvedArrangementConflicts,
            RehearsalResponseState rehearsalResponseState,
            String humanNote,
            ReadinessPrivacyClassification privacyClassification,
            boolean overrideAction,
            String updatedBy,
            Instant updatedAt) {
    }
}
