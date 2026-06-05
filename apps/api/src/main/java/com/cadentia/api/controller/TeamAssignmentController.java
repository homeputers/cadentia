package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.TeamAssignmentsApi;
import com.cadentia.generated.model.TeamAssignmentHistoryResponse;
import com.cadentia.generated.model.TeamAssignmentStatusCode;
import com.cadentia.generated.model.TeamAssignmentType;
import com.cadentia.generated.model.TeamInstrumentCode;
import com.cadentia.generated.model.TeamMusicianRoleCode;
import com.cadentia.generated.model.TeamRehearsalAssignmentRequest;
import com.cadentia.generated.model.TeamRehearsalAssignmentResponse;
import com.cadentia.generated.model.TeamReorderAssignmentsRequest;
import com.cadentia.generated.model.TeamServiceAssignmentRequest;
import com.cadentia.generated.model.TeamServiceAssignmentResponse;
import com.cadentia.generated.model.TeamServiceRosterResponse;
import com.cadentia.generated.model.TeamSongAssignmentOverrideRequest;
import com.cadentia.generated.model.TeamSongAssignmentOverrideResponse;
import com.cadentia.generated.model.TeamSubstituteAssignmentRequest;
import com.cadentia.generated.model.TeamVocalPartCode;
import com.cadentia.team.AuthorizedTeamPlanningService;
import com.cadentia.team.TeamPlanningModels.AssignmentChangeHistoryRecord;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AssignmentType;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceRoster;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamAssignmentController implements TeamAssignmentsApi {

    private final AuthorizedTeamPlanningService service;

    public TeamAssignmentController(AuthorizedTeamPlanningService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamServiceAssignmentResponse> createServiceTeamAssignment(
            UUID servicePlanId,
            TeamServiceAssignmentRequest request) {
        ServiceAssignmentRecord assignment = service.createServiceAssignment(
                servicePlanId,
                request.getMusicianId(),
                role(request.getRoleCode()),
                instrument(request.getInstrumentCode()),
                vocalPart(request.getVocalPartCode()),
                status(request.getStatusCode()),
                request.getAssignmentOrder() == null ? 0 : request.getAssignmentOrder(),
                Boolean.TRUE.equals(request.getOverrideUnavailable()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(serviceAssignment(assignment));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamServiceAssignmentResponse> updateServiceTeamAssignment(
            UUID servicePlanId,
            UUID assignmentId,
            TeamServiceAssignmentRequest request) {
        ServiceAssignmentRecord assignment = service.updateServiceAssignment(
                assignmentId,
                request.getMusicianId(),
                role(request.getRoleCode()),
                instrument(request.getInstrumentCode()),
                vocalPart(request.getVocalPartCode()),
                status(request.getStatusCode()),
                request.getAssignmentOrder() == null ? 0 : request.getAssignmentOrder(),
                Boolean.TRUE.equals(request.getOverrideUnavailable()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.ok(serviceAssignment(assignment));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<Void> removeServiceTeamAssignment(
            UUID servicePlanId,
            UUID assignmentId,
            String reasonCode,
            String reference) {
        service.removeServiceAssignment(assignmentId, reasonCode, reference);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<TeamServiceAssignmentResponse>> reorderServiceTeamAssignments(
            UUID servicePlanId,
            TeamReorderAssignmentsRequest request) {
        return ResponseEntity.ok(service.reorderServiceAssignments(
                        servicePlanId,
                        request.getOrderedAssignmentIds(),
                        request.getReasonCode(),
                        request.getReference())
                .stream()
                .map(this::serviceAssignment)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamServiceAssignmentResponse> substituteServiceTeamAssignment(
            UUID servicePlanId,
            UUID assignmentId,
            TeamSubstituteAssignmentRequest request) {
        ServiceAssignmentRecord assignment = service.substituteServiceAssignment(
                assignmentId,
                request.getSubstituteMusicianId(),
                request.getStatusCode() == null ? AssignmentStatusCode.SUBSTITUTE : status(request.getStatusCode()),
                Boolean.TRUE.equals(request.getOverrideUnavailable()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(serviceAssignment(assignment));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamRehearsalAssignmentResponse> createRehearsalTeamAssignment(
            UUID rehearsalEventId,
            TeamRehearsalAssignmentRequest request) {
        RehearsalAssignmentRecord assignment = service.createRehearsalAssignment(
                rehearsalEventId,
                request.getServicePlanId(),
                request.getServiceAssignmentId(),
                request.getMusicianId(),
                role(request.getRoleCode()),
                instrument(request.getInstrumentCode()),
                vocalPart(request.getVocalPartCode()),
                status(request.getStatusCode()),
                request.getSubstituteForAssignmentId(),
                Boolean.TRUE.equals(request.getOverrideUnavailable()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(rehearsalAssignment(assignment));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamSongAssignmentOverrideResponse> createSongTeamAssignmentOverride(
            UUID servicePlanId,
            TeamSongAssignmentOverrideRequest request) {
        SongAssignmentOverrideRecord override = service.createSongAssignmentOverride(
                servicePlanId,
                request.getServicePlanBlockId(),
                request.getBaseServiceAssignmentId(),
                request.getMusicianId(),
                role(request.getRoleCode()),
                instrument(request.getInstrumentCode()),
                vocalPart(request.getVocalPartCode()),
                status(request.getStatusCode()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(songOverride(override));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamServiceRosterResponse> getServiceTeamRoster(UUID servicePlanId) {
        return ResponseEntity.ok(roster(service.getServiceRoster(servicePlanId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<TeamServiceAssignmentResponse>> listUpcomingTeamAssignmentsForMusician(
            UUID musicianId,
            OffsetDateTime fromInclusive) {
        return ResponseEntity.ok(service.listUpcomingAssignmentsForMusician(musicianId, fromInclusive.toInstant())
                .stream()
                .map(this::serviceAssignment)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<TeamAssignmentHistoryResponse>> listTeamAssignmentHistory(UUID servicePlanId) {
        return ResponseEntity.ok(service.listAssignmentHistory(servicePlanId).stream()
                .map(this::history)
                .toList());
    }

    private TeamServiceAssignmentResponse serviceAssignment(ServiceAssignmentRecord assignment) {
        TeamServiceAssignmentResponse response = new TeamServiceAssignmentResponse(
                assignment.assignmentId(),
                assignment.servicePlanId(),
                assignment.musicianId(),
                role(assignment.roleCode()),
                status(assignment.statusCode()));
        response.setInstrumentCode(instrument(assignment.instrumentCode()));
        response.setVocalPartCode(vocalPart(assignment.vocalPartCode()));
        response.setAssignmentOrder(assignment.assignmentOrder());
        response.setSubstituteForAssignmentId(assignment.substituteForAssignmentId());
        return response;
    }

    private TeamRehearsalAssignmentResponse rehearsalAssignment(RehearsalAssignmentRecord assignment) {
        TeamRehearsalAssignmentResponse response = new TeamRehearsalAssignmentResponse(
                assignment.assignmentId(),
                assignment.rehearsalEventId(),
                assignment.servicePlanId(),
                assignment.musicianId(),
                role(assignment.roleCode()),
                status(assignment.statusCode()));
        response.setInstrumentCode(instrument(assignment.instrumentCode()));
        response.setVocalPartCode(vocalPart(assignment.vocalPartCode()));
        response.setServiceAssignmentId(assignment.serviceAssignmentId());
        response.setSubstituteForAssignmentId(assignment.substituteForAssignmentId());
        return response;
    }

    private TeamSongAssignmentOverrideResponse songOverride(SongAssignmentOverrideRecord override) {
        TeamSongAssignmentOverrideResponse response = new TeamSongAssignmentOverrideResponse(
                override.overrideId(),
                override.servicePlanId(),
                override.servicePlanBlockId(),
                override.baseServiceAssignmentId(),
                override.musicianId(),
                role(override.roleCode()),
                status(override.statusCode()));
        response.setInstrumentCode(instrument(override.instrumentCode()));
        response.setVocalPartCode(vocalPart(override.vocalPartCode()));
        return response;
    }

    private TeamServiceRosterResponse roster(ServiceRoster roster) {
        TeamServiceRosterResponse response = new TeamServiceRosterResponse(
                roster.servicePlanId(),
                roster.assignments().stream().map(this::serviceAssignment).toList(),
                roster.staffingGaps(),
                roster.availabilityConflicts());
        return response;
    }

    private TeamAssignmentHistoryResponse history(AssignmentChangeHistoryRecord history) {
        TeamAssignmentHistoryResponse response = new TeamAssignmentHistoryResponse(
                history.historyId(),
                type(history.assignmentType()),
                history.assignmentId(),
                history.servicePlanId(),
                history.changeAction(),
                history.changedBy(),
                history.reasonCode(),
                OffsetDateTime.ofInstant(history.changedAt(), java.time.ZoneOffset.UTC));
        response.setRehearsalEventId(history.rehearsalEventId());
        response.setMusicianId(history.musicianId());
        response.setRoleCode(role(history.roleCode()));
        response.setInstrumentCode(instrument(history.instrumentCode()));
        response.setVocalPartCode(vocalPart(history.vocalPartCode()));
        response.setStatusCode(status(history.statusCode()));
        response.setAssignmentOrder(history.assignmentOrder());
        response.setSubstituteForAssignmentId(history.substituteForAssignmentId());
        response.setServiceAssignmentId(history.serviceAssignmentId());
        response.setReference(history.reference());
        return response;
    }

    private MusicianRoleCode role(TeamMusicianRoleCode code) {
        return code == null ? null : MusicianRoleCode.valueOf(code.getValue());
    }

    private TeamMusicianRoleCode role(MusicianRoleCode code) {
        return code == null ? null : TeamMusicianRoleCode.fromValue(code.name());
    }

    private InstrumentCode instrument(TeamInstrumentCode code) {
        return code == null ? null : InstrumentCode.valueOf(code.getValue());
    }

    private TeamInstrumentCode instrument(InstrumentCode code) {
        return code == null ? null : TeamInstrumentCode.fromValue(code.name());
    }

    private VocalPartCode vocalPart(TeamVocalPartCode code) {
        return code == null ? null : VocalPartCode.valueOf(code.getValue());
    }

    private TeamVocalPartCode vocalPart(VocalPartCode code) {
        return code == null ? null : TeamVocalPartCode.fromValue(code.name());
    }

    private AssignmentStatusCode status(TeamAssignmentStatusCode code) {
        return code == null ? null : AssignmentStatusCode.valueOf(code.getValue());
    }

    private TeamAssignmentStatusCode status(AssignmentStatusCode code) {
        return code == null ? null : TeamAssignmentStatusCode.fromValue(code.name());
    }

    private TeamAssignmentType type(AssignmentType type) {
        return type == null ? null : TeamAssignmentType.fromValue(type.name());
    }
}
