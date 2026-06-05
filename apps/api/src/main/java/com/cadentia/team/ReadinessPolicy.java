package com.cadentia.team;

import com.cadentia.team.ReadinessModels.ReadinessAudience;
import com.cadentia.team.ReadinessModels.ReadinessNoteRecord;
import com.cadentia.team.ReadinessModels.ReadinessPrivacyClassification;
import org.springframework.stereotype.Component;

@Component
public class ReadinessPolicy {

    public ReadinessNoteRecord redact(ReadinessNoteRecord note, ReadinessAudience audience) {
        if (note == null || canReadHumanNote(note.privacyClassification(), audience)) {
            return note;
        }
        return new ReadinessNoteRecord(
                note.readinessNoteId(),
                note.scopeType(),
                note.scopeId(),
                note.servicePlanId(),
                note.readinessStatus(),
                note.objectiveBlockers(),
                note.missingPeople(),
                note.unresolvedArrangementConflicts(),
                note.rehearsalResponseState(),
                null,
                note.privacyClassification(),
                note.overrideAction(),
                note.updatedBy(),
                note.updatedAt());
    }

    public boolean canReadHumanNote(ReadinessPrivacyClassification classification, ReadinessAudience audience) {
        if (classification == ReadinessPrivacyClassification.PUBLIC) {
            return true;
        }
        if (classification == ReadinessPrivacyClassification.TEAM_PRIVATE) {
            return audience == ReadinessAudience.TEAM_LEADER || audience == ReadinessAudience.ADMIN;
        }
        return audience == ReadinessAudience.ADMIN;
    }
}
