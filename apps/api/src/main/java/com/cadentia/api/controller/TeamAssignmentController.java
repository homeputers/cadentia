package com.cadentia.api.controller;

import com.cadentia.team.AuthorizedTeamPlanningService;
import com.cadentia.team.TeamPlanningModels.AssignmentChangeHistoryRecord;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.InstrumentCode;
import com.cadentia.team.TeamPlanningModels.MusicianRoleCode;
import com.cadentia.team.TeamPlanningModels.RehearsalAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceAssignmentRecord;
import com.cadentia.team.TeamPlanningModels.ServiceRoster;
import com.cadentia.team.TeamPlanningModels.SongAssignmentOverrideRecord;
import com.cadentia.team.TeamPlanningModels.VocalPartCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team-assignments")
public class TeamAssignmentController {

    private final AuthorizedTeamPlanningService service;

    public TeamAssignmentController(AuthorizedTeamPlanningService service) {
        this.service = service;
    }

    @PostMapping("/services/{servicePlanId}")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServiceAssignmentRecord> createServiceAssignment(
            @PathVariable UUID servicePlanId,
            @RequestBody ServiceAssignmentRequest request) {
        ServiceAssignmentRecord assignment = service.createServiceAssignment(
                servicePlanId,
                request.musicianId(),
                request.roleCode(),
                request.instrumentCode(),
                request.vocalPartCode(),
                request.statusCode(),
                request.assignmentOrder() == null ? 0 : request.assignmentOrder(),
                Boolean.TRUE.equals(request.overrideUnavailable()),
                request.reasonCode(),
                request.reference());
        return ResponseEntity.status(201).body(assignment);
    }

    @PutMapping("/services/{servicePlanId}/assignments/{assignmentId}")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServiceAssignmentRecord> updateServiceAssignment(
            @PathVariable UUID assignmentId,
            @RequestBody ServiceAssignmentRequest request) {
        return ResponseEntity.ok(service.updateServiceAssignment(
                assignmentId,
                request.musicianId(),
                request.roleCode(),
                request.instrumentCode(),
                request.vocalPartCode(),
                request.statusCode(),
                request.assignmentOrder() == null ? 0 : request.assignmentOrder(),
                Boolean.TRUE.equals(request.overrideUnavailable()),
                request.reasonCode(),
                request.reference()));
    }

    @DeleteMapping("/services/{servicePlanId}/assignments/{assignmentId}")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<Void> removeServiceAssignment(
            @PathVariable UUID assignmentId,
            @RequestParam(defaultValue = "remove_assignment") String reasonCode,
            @RequestParam(required = false) String reference) {
        service.removeServiceAssignment(assignmentId, reasonCode, reference);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/services/{servicePlanId}/reorder")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<ServiceAssignmentRecord>> reorderServiceAssignments(
            @PathVariable UUID servicePlanId,
            @RequestBody ReorderAssignmentsRequest request) {
        return ResponseEntity.ok(service.reorderServiceAssignments(
                servicePlanId, request.orderedAssignmentIds(), request.reasonCode(), request.reference()));
    }

    @PostMapping("/services/{servicePlanId}/assignments/{assignmentId}/substitute")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServiceAssignmentRecord> substituteServiceAssignment(
            @PathVariable UUID assignmentId,
            @RequestBody SubstituteAssignmentRequest request) {
        return ResponseEntity.status(201).body(service.substituteServiceAssignment(
                assignmentId,
                request.substituteMusicianId(),
                request.statusCode() == null ? AssignmentStatusCode.SUBSTITUTE : request.statusCode(),
                Boolean.TRUE.equals(request.overrideUnavailable()),
                request.reasonCode(),
                request.reference()));
    }

    @PostMapping("/rehearsals/{rehearsalEventId}")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<RehearsalAssignmentRecord> createRehearsalAssignment(
            @PathVariable UUID rehearsalEventId,
            @RequestBody RehearsalAssignmentRequest request) {
        return ResponseEntity.status(201).body(service.createRehearsalAssignment(
                rehearsalEventId,
                request.servicePlanId(),
                request.serviceAssignmentId(),
                request.musicianId(),
                request.roleCode(),
                request.instrumentCode(),
                request.vocalPartCode(),
                request.statusCode(),
                request.substituteForAssignmentId(),
                Boolean.TRUE.equals(request.overrideUnavailable()),
                request.reasonCode(),
                request.reference()));
    }

    @PostMapping("/services/{servicePlanId}/song-overrides")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<SongAssignmentOverrideRecord> createSongAssignmentOverride(
            @PathVariable UUID servicePlanId,
            @RequestBody SongAssignmentOverrideRequest request) {
        return ResponseEntity.status(201).body(service.createSongAssignmentOverride(
                servicePlanId,
                request.servicePlanBlockId(),
                request.baseServiceAssignmentId(),
                request.musicianId(),
                request.roleCode(),
                request.instrumentCode(),
                request.vocalPartCode(),
                request.statusCode(),
                request.reasonCode(),
                request.reference()));
    }

    @GetMapping("/services/{servicePlanId}/roster")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ServiceRoster> getServiceRoster(@PathVariable UUID servicePlanId) {
        return ResponseEntity.ok(service.getServiceRoster(servicePlanId));
    }

    @GetMapping("/musicians/{musicianId}/upcoming")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<ServiceAssignmentRecord>> listUpcomingAssignments(
            @PathVariable UUID musicianId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromInclusive) {
        return ResponseEntity.ok(service.listUpcomingAssignmentsForMusician(musicianId, fromInclusive));
    }

    @GetMapping("/services/{servicePlanId}/history")
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<AssignmentChangeHistoryRecord>> listAssignmentHistory(@PathVariable UUID servicePlanId) {
        return ResponseEntity.ok(service.listAssignmentHistory(servicePlanId));
    }

    public record ServiceAssignmentRequest(
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            Integer assignmentOrder,
            Boolean overrideUnavailable,
            String reasonCode,
            String reference) {
    }

    public record RehearsalAssignmentRequest(
            UUID servicePlanId,
            UUID serviceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            UUID substituteForAssignmentId,
            Boolean overrideUnavailable,
            String reasonCode,
            String reference) {
    }

    public record SongAssignmentOverrideRequest(
            UUID servicePlanBlockId,
            UUID baseServiceAssignmentId,
            UUID musicianId,
            MusicianRoleCode roleCode,
            InstrumentCode instrumentCode,
            VocalPartCode vocalPartCode,
            AssignmentStatusCode statusCode,
            String reasonCode,
            String reference) {
    }

    public record SubstituteAssignmentRequest(
            UUID substituteMusicianId,
            AssignmentStatusCode statusCode,
            Boolean overrideUnavailable,
            String reasonCode,
            String reference) {
    }

    public record ReorderAssignmentsRequest(
            List<UUID> orderedAssignmentIds,
            String reasonCode,
            String reference) {
    }
}
