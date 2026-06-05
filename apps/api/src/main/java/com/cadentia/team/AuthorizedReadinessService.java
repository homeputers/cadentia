package com.cadentia.team;

import com.cadentia.api.security.PersonnelAuthorizationPolicy;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditAction;
import com.cadentia.team.PersonnelAuditModels.PersonnelAuditTargetType;
import com.cadentia.team.ReadinessModels.ReadinessAudience;
import com.cadentia.team.ReadinessModels.ReadinessNoteRecord;
import com.cadentia.team.ReadinessModels.RecordReadinessCommand;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizedReadinessService {

    private final TeamPlanningRepository repository;
    private final PersonnelAuthorizationPolicy authorizationPolicy;
    private final PersonnelAuditRecorder auditRecorder;
    private final ReadinessPolicy readinessPolicy;

    public AuthorizedReadinessService(
            TeamPlanningRepository repository,
            PersonnelAuthorizationPolicy authorizationPolicy,
            PersonnelAuditRecorder auditRecorder,
            ReadinessPolicy readinessPolicy) {
        this.repository = repository;
        this.authorizationPolicy = authorizationPolicy;
        this.auditRecorder = auditRecorder;
        this.readinessPolicy = readinessPolicy;
    }

    @Transactional
    public ReadinessNoteRecord recordReadiness(RecordReadinessCommand command, String reasonCode, String reference) {
        authorizationPolicy.requireTeamReadinessUpdate();
        ReadinessNoteRecord note = repository.recordReadiness(command);
        auditRecorder.record(new PersonnelAuditModels.PersonnelAuditEvent(
                authorizationPolicy.currentActor(),
                authorizationPolicy.currentActorRoles(),
                command.overrideAction()
                        ? PersonnelAuditAction.PERSONNEL_READINESS_OVERRIDE_CHANGED
                        : PersonnelAuditAction.PERSONNEL_READINESS_NOTE_CHANGED,
                PersonnelAuditTargetType.READINESS_NOTE,
                note.readinessNoteId(),
                reasonCode,
                reference,
                "readiness_notes:" + command.scopeType() + ":" + command.scopeId(),
                "readiness_notes:" + note.readinessNoteId(),
                null,
                null,
                Map.of(
                        "scopeType", command.scopeType().name(),
                        "readinessStatus", command.readinessStatus().name(),
                        "privacyClassification", command.privacyClassification().name())));
        return readinessPolicy.redact(note, ReadinessAudience.TEAM_LEADER);
    }

    public List<ReadinessNoteRecord> listReadiness(UUID servicePlanId, ReadinessAudience audience) {
        authorizationPolicy.requireRosterRead();
        return repository.listReadinessNotes(servicePlanId).stream()
                .map(note -> readinessPolicy.redact(note, audience))
                .toList();
    }
}
