package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.TeamAssignmentsApi;
import com.cadentia.generated.model.CreateTeamMusicianRequest;
import com.cadentia.generated.model.TeamAssignmentHistoryResponse;
import com.cadentia.generated.model.TeamAssignmentStatusCode;
import com.cadentia.generated.model.TeamAssignmentType;
import com.cadentia.generated.model.TeamAvailabilityWindowRequest;
import com.cadentia.generated.model.TeamAvailabilityWindowResponse;
import com.cadentia.generated.model.TeamInstrumentCode;
import com.cadentia.generated.model.TeamMusicianInstrumentAssignmentRequest;
import com.cadentia.generated.model.TeamMusicianResponse;
import com.cadentia.generated.model.TeamMusicianRoleAssignmentRequest;
import com.cadentia.generated.model.TeamMusicianRoleCode;
import com.cadentia.generated.model.TeamMusicianSkillAssignmentResponse;
import com.cadentia.generated.model.TeamMusicianSkillsResponse;
import com.cadentia.generated.model.TeamMusicianVocalPartAssignmentRequest;
import com.cadentia.generated.model.TeamRehearsalAssignmentRequest;
import com.cadentia.generated.model.TeamRehearsalAssignmentResponse;
import com.cadentia.generated.model.TeamRehearsalEventRequest;
import com.cadentia.generated.model.TeamRehearsalEventResponse;
import com.cadentia.generated.model.TeamReorderAssignmentsRequest;
import com.cadentia.generated.model.TeamServiceAssignmentRequest;
import com.cadentia.generated.model.TeamServiceAssignmentResponse;
import com.cadentia.generated.model.TeamServiceRosterResponse;
import com.cadentia.generated.model.TeamSongAssignmentOverrideRequest;
import com.cadentia.generated.model.TeamSongAssignmentOverrideResponse;
import com.cadentia.generated.model.TeamSubstituteAssignmentRequest;
import com.cadentia.generated.model.TeamServingPreferenceCode;
import com.cadentia.generated.model.TeamSkillAssignmentDomain;
import com.cadentia.generated.model.TeamSkillLevelCode;
import com.cadentia.generated.model.TeamVocalPartCode;
import com.cadentia.generated.model.TeamVocalRangeCode;
import com.cadentia.team.AuthorizedTeamPlanningService;
import com.cadentia.team.TeamPlanningModels.AssignmentChangeHistoryRecord;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AssignmentType;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.MusicianSkillAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.SkillAssignmentDomain;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceRoster;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.SkillLevelCode;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
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

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<TeamMusicianResponse>> listTeamMusicians() {
        return ResponseEntity.ok(service.listMusiciansForRoster().stream()
                .map(this::musician)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamMusicianResponse> createTeamMusician(CreateTeamMusicianRequest request) {
        MusicianRecord musician = service.createMusician(
                new CreateMusicianCommand(
                        request.getDisplayName(),
                        request.getAccountPrincipal(),
                        request.getEmail(),
                        request.getPhone(),
                        vocalRange(request.getPrimaryVocalRangeCode()),
                        request.getComfortableLowMidiNote(),
                        request.getComfortableHighMidiNote(),
                        servingPreference(request.getServingPreferenceCode()),
                        null),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(musician(musician));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamMusicianResponse> getTeamMusician(UUID musicianId) {
        return service.findMusicianProfile(musicianId)
                .map(this::musician)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamAvailabilityWindowResponse> createTeamAvailabilityWindow(
            UUID musicianId,
            TeamAvailabilityWindowRequest request) {
        AvailabilityWindowRecord window = service.createAvailabilityWindow(
                musicianId,
                request.getStartsAt().toInstant(),
                request.getEndsAt().toInstant(),
                status(request.getStatusCode()),
                request.getServicePlanId(),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(availabilityWindow(window));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamRehearsalEventResponse> createTeamRehearsalEvent(TeamRehearsalEventRequest request) {
        RehearsalEventRecord event = service.createRehearsalEvent(
                request.getServicePlanId(),
                request.getStartsAt().toInstant(),
                request.getEndsAt().toInstant(),
                request.getLocation(),
                null,
                null);
        return ResponseEntity.status(201).body(rehearsalEvent(event));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<TeamRehearsalEventResponse>> listTeamRehearsalEvents(UUID servicePlanId) {
        return ResponseEntity.ok(service.listRehearsalEvents(servicePlanId).stream()
                .map(this::rehearsalEvent)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamMusicianSkillsResponse> getTeamMusicianSkills(UUID musicianId) {
        TeamMusicianSkillsResponse response = new TeamMusicianSkillsResponse(
                musicianId,
                service.listMusicianSkillAssignments(musicianId).stream()
                        .map(this::skillAssignment)
                        .toList());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamMusicianSkillAssignmentResponse> assignTeamMusicianRole(
            UUID musicianId,
            TeamMusicianRoleAssignmentRequest request) {
        UUID assignmentId = service.assignRole(
                musicianId,
                role(request.getRoleCode()),
                skillLevel(request.getSkillLevelCode()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(skillAssignment(
                assignmentId, musicianId, SkillAssignmentDomain.ROLE, request.getRoleCode().getValue(),
                skillLevel(request.getSkillLevelCode())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamMusicianSkillAssignmentResponse> assignTeamMusicianInstrument(
            UUID musicianId,
            TeamMusicianInstrumentAssignmentRequest request) {
        UUID assignmentId = service.assignInstrument(
                musicianId,
                instrument(request.getInstrumentCode()),
                skillLevel(request.getSkillLevelCode()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(skillAssignment(
                assignmentId, musicianId, SkillAssignmentDomain.INSTRUMENT, request.getInstrumentCode().getValue(),
                skillLevel(request.getSkillLevelCode())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<TeamMusicianSkillAssignmentResponse> assignTeamMusicianVocalPart(
            UUID musicianId,
            TeamMusicianVocalPartAssignmentRequest request) {
        UUID assignmentId = service.assignVocalPart(
                musicianId,
                vocalPart(request.getVocalPartCode()),
                skillLevel(request.getSkillLevelCode()),
                request.getReasonCode(),
                request.getReference());
        return ResponseEntity.status(201).body(skillAssignment(
                assignmentId, musicianId, SkillAssignmentDomain.VOCAL_PART, request.getVocalPartCode().getValue(),
                skillLevel(request.getSkillLevelCode())));
    }

    private TeamMusicianSkillAssignmentResponse skillAssignment(MusicianSkillAssignmentRecord assignment) {
        return skillAssignment(
                assignment.assignmentId(),
                assignment.musicianId(),
                assignment.domain(),
                assignment.code(),
                assignment.skillLevelCode());
    }

    private TeamMusicianSkillAssignmentResponse skillAssignment(
            UUID assignmentId,
            UUID musicianId,
            SkillAssignmentDomain domain,
            String code,
            SkillLevelCode skillLevelCode) {
        TeamMusicianSkillAssignmentResponse response = new TeamMusicianSkillAssignmentResponse(
                assignmentId,
                musicianId,
                TeamSkillAssignmentDomain.fromValue(domain.name()),
                code);
        response.setSkillLevelCode(skillLevel(skillLevelCode));
        return response;
    }

    private SkillLevelCode skillLevel(TeamSkillLevelCode code) {
        return code == null ? null : SkillLevelCode.valueOf(code.getValue());
    }

    private TeamSkillLevelCode skillLevel(SkillLevelCode code) {
        return code == null ? null : TeamSkillLevelCode.fromValue(code.name());
    }

    private TeamMusicianResponse musician(MusicianRecord musician) {
        TeamMusicianResponse response = new TeamMusicianResponse(
                musician.musicianId(),
                musician.displayName(),
                musician.active());
        response.setAccountPrincipal(musician.accountPrincipal());
        response.setEmail(musician.email());
        response.setPhone(musician.phone());
        response.setPrimaryVocalRangeCode(vocalRange(musician.primaryVocalRangeCode()));
        response.setComfortableLowMidiNote(musician.comfortableLowMidiNote());
        response.setComfortableHighMidiNote(musician.comfortableHighMidiNote());
        response.setServingPreferenceCode(servingPreference(musician.servingPreferenceCode()));
        return response;
    }

    private TeamAvailabilityWindowResponse availabilityWindow(AvailabilityWindowRecord window) {
        TeamAvailabilityWindowResponse response = new TeamAvailabilityWindowResponse(
                window.availabilityWindowId(),
                window.musicianId(),
                OffsetDateTime.ofInstant(window.startsAt(), java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(window.endsAt(), java.time.ZoneOffset.UTC),
                status(window.statusCode()));
        response.setServicePlanId(window.servicePlanId());
        return response;
    }

    private TeamRehearsalEventResponse rehearsalEvent(RehearsalEventRecord event) {
        TeamRehearsalEventResponse response = new TeamRehearsalEventResponse(
                event.rehearsalEventId(),
                event.servicePlanId(),
                OffsetDateTime.ofInstant(event.startsAt(), java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(event.endsAt(), java.time.ZoneOffset.UTC));
        response.setLocation(event.location());
        return response;
    }

    private VocalRangeCode vocalRange(TeamVocalRangeCode code) {
        return code == null ? null : VocalRangeCode.valueOf(code.getValue());
    }

    private TeamVocalRangeCode vocalRange(VocalRangeCode code) {
        return code == null ? null : TeamVocalRangeCode.fromValue(code.name());
    }

    private ServingPreferenceCode servingPreference(TeamServingPreferenceCode code) {
        return code == null ? null : ServingPreferenceCode.valueOf(code.getValue());
    }

    private TeamServingPreferenceCode servingPreference(ServingPreferenceCode code) {
        return code == null ? null : TeamServingPreferenceCode.fromValue(code.name());
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
