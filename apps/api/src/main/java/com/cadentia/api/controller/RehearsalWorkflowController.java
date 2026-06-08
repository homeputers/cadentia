package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.RehearsalWorkflowApi;
import com.cadentia.generated.model.ArrangementOverrideResponse;
import com.cadentia.generated.model.CreateArrangementOverrideRequest;
import com.cadentia.generated.model.CreateIssueActionRequest;
import com.cadentia.generated.model.EffectiveArrangementProvenanceResponse;
import com.cadentia.generated.model.EffectiveArrangementResponse;
import com.cadentia.generated.model.EffectiveArrangementValueResponse;
import com.cadentia.generated.model.CreateRehearsalIssueRequest;
import com.cadentia.generated.model.CreateRehearsalNoteRequest;
import com.cadentia.generated.model.CreateRehearsalSessionRequest;
import com.cadentia.generated.model.ReadinessTransitionRequest;
import com.cadentia.generated.model.RehearsalIssueActionOwner;
import com.cadentia.generated.model.RehearsalIssueActionResponse;
import com.cadentia.generated.model.RehearsalIssueActionStatusCode;
import com.cadentia.generated.model.RehearsalIssueCategoryCode;
import com.cadentia.generated.model.RehearsalIssueOwnerType;
import com.cadentia.generated.model.RehearsalIssueResponse;
import com.cadentia.generated.model.RehearsalIssueSeverityCode;
import com.cadentia.generated.model.RehearsalIssueStatusCode;
import com.cadentia.generated.model.RehearsalNoteResponse;
import com.cadentia.generated.model.RehearsalNoteVisibilityCode;
import com.cadentia.generated.model.RehearsalReadinessStateCode;
import com.cadentia.generated.model.RehearsalSessionResponse;
import com.cadentia.generated.model.RehearsalWorkflowTarget;
import com.cadentia.generated.model.RehearsalWorkflowTargetTypeCode;
import com.cadentia.generated.model.UpdateArrangementOverrideRequest;
import com.cadentia.generated.model.UpdateIssueActionOwnerRequest;
import com.cadentia.generated.model.UpdateIssueActionStatusRequest;
import com.cadentia.generated.model.UpdateIssueStatusRequest;
import com.cadentia.generated.model.UpdateRehearsalSessionRequest;
import com.cadentia.generated.model.WorkflowStatusResponse;
import com.cadentia.rehearsal.EffectiveArrangementRenderingService;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ArrangementOverrideRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementProvenance;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementRendering;
import com.cadentia.rehearsal.RehearsalWorkflowModels.EffectiveArrangementValue;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueActionStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueCategoryCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueOwnerType;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueSeverityCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.IssueStatusCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueActionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalIssueRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalNoteRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalSessionRecord;
import com.cadentia.rehearsal.RehearsalWorkflowModels.WorkflowStatus;
import com.cadentia.rehearsal.RehearsalWorkflowService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class RehearsalWorkflowController implements RehearsalWorkflowApi {

    private static final long CURRENT_VERSION = 0L;

    private final RehearsalWorkflowService service;
    private final EffectiveArrangementRenderingService renderingService;

    public RehearsalWorkflowController(RehearsalWorkflowService service) {
        this(service, null);
    }

    @Autowired
    public RehearsalWorkflowController(
            RehearsalWorkflowService service,
            EffectiveArrangementRenderingService renderingService) {
        this.service = service;
        this.renderingService = renderingService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER)")
    public ResponseEntity<List<RehearsalSessionResponse>> listRehearsalSessions(UUID servicePlanId) {
        return ResponseEntity.ok(service.listSessions(servicePlanId).stream().map(this::toSession).toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<RehearsalSessionResponse> createRehearsalSession(
            UUID servicePlanId,
            CreateRehearsalSessionRequest request) {
        RehearsalSessionRecord created = service.createSession(
                servicePlanId,
                request.getSessionCode(),
                request.getStartsAt().toInstant(),
                request.getEndsAt().toInstant(),
                request.getLocation(),
                request.getReason(),
                request.getReference());
        return ResponseEntity.status(201).body(toSession(created));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<RehearsalSessionResponse> updateRehearsalSession(
            UUID servicePlanId,
            UUID rehearsalSessionId,
            UpdateRehearsalSessionRequest request) {
        assertCurrentVersion(request.getExpectedVersion());
        return ResponseEntity.ok(toSession(service.updateSession(
                servicePlanId,
                rehearsalSessionId,
                request.getSessionCode(),
                request.getStartsAt().toInstant(),
                request.getEndsAt().toInstant(),
                request.getLocation(),
                request.getReason(),
                request.getReference())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<RehearsalSessionResponse> archiveRehearsalSession(UUID servicePlanId, UUID rehearsalSessionId) {
        return ResponseEntity.ok(toSession(service.archiveSession(servicePlanId, rehearsalSessionId, "archived", null)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER)")
    public ResponseEntity<WorkflowStatusResponse> getServiceWorkflowStatus(UUID servicePlanId) {
        return ResponseEntity.ok(toWorkflowStatus(service.workflowStatus(servicePlanId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER)")
    public ResponseEntity<WorkflowStatusResponse> transitionReadinessState(
            UUID servicePlanId,
            ReadinessTransitionRequest request) {
        assertCurrentVersion(request.getExpectedVersion());
        WorkflowStatus status = service.requestReadinessTransition(
                servicePlanId,
                request.getRehearsalSessionId(),
                toReadiness(request.getNewStateCode()),
                request.getReason(),
                request.getReference());
        return ResponseEntity.ok(toWorkflowStatus(status));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER)")
    public ResponseEntity<List<RehearsalNoteResponse>> listRehearsalNotes(UUID servicePlanId) {
        return ResponseEntity.ok(service.listNotes(servicePlanId).stream().map(this::toNote).toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER)")
    public ResponseEntity<RehearsalNoteResponse> createRehearsalNote(UUID servicePlanId, CreateRehearsalNoteRequest request) {
        RehearsalNoteRecord created = service.addNote(
                servicePlanId,
                toTarget(request.getTarget()),
                request.getNoteBody(),
                request.getVisibilityCode().getValue(),
                request.getReason(),
                request.getReference());
        return ResponseEntity.status(201).body(toNote(created));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER)")
    public ResponseEntity<List<RehearsalIssueResponse>> listRehearsalIssues(UUID servicePlanId) {
        Map<UUID, List<RehearsalIssueActionRecord>> actionsByIssue = actionsByIssue(servicePlanId);
        return ResponseEntity.ok(service.listIssues(servicePlanId).stream()
                .map(issue -> toIssue(issue, actionsByIssue.getOrDefault(issue.issueId(), List.of())))
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER)")
    public ResponseEntity<RehearsalIssueResponse> createRehearsalIssue(UUID servicePlanId, CreateRehearsalIssueRequest request) {
        RehearsalIssueRecord issue = service.openIssue(
                servicePlanId,
                toTarget(request.getTarget()),
                toCategory(request.getCategoryCode()),
                toSeverity(request.getSeverityCode()),
                request.getTitle(),
                request.getDetail(),
                request.getReason(),
                request.getReference());
        return ResponseEntity.status(201).body(toIssue(issue, List.of()));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER)")
    public ResponseEntity<RehearsalIssueResponse> updateRehearsalIssueStatus(
            UUID servicePlanId,
            UUID issueId,
            UpdateIssueStatusRequest request) {
        assertCurrentVersion(request.getExpectedVersion());
        RehearsalIssueRecord issue = service.changeIssueStatus(
                servicePlanId, issueId, toIssueStatus(request.getStatusCode()), request.getReason(), request.getReference());
        return ResponseEntity.ok(toIssue(issue, actionsByIssue(servicePlanId).getOrDefault(issue.issueId(), List.of())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER)")
    public ResponseEntity<RehearsalIssueActionResponse> createRehearsalIssueAction(
            UUID servicePlanId,
            UUID issueId,
            CreateIssueActionRequest request) {
        RehearsalIssueActionOwner owner = request.getOwner();
        RehearsalIssueActionRecord action = service.assignAction(
                servicePlanId,
                issueId,
                request.getActionSummary(),
                toOwnerType(owner.getOwnerType()),
                owner.getOwnerActor(),
                owner.getOwnerTeamRoleCode(),
                owner.getOwnerServiceAssignmentId(),
                request.getReason(),
                request.getReference());
        return ResponseEntity.status(201).body(toAction(action));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<RehearsalIssueActionResponse> updateRehearsalIssueActionOwner(
            UUID servicePlanId,
            UUID actionId,
            UpdateIssueActionOwnerRequest request) {
        assertCurrentVersion(request.getExpectedVersion());
        RehearsalIssueActionOwner owner = request.getOwner();
        return ResponseEntity.ok(toAction(service.assignOwner(
                servicePlanId,
                actionId,
                toOwnerType(owner.getOwnerType()),
                owner.getOwnerActor(),
                owner.getOwnerTeamRoleCode(),
                owner.getOwnerServiceAssignmentId(),
                request.getReason(),
                request.getReference())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN)")
    public ResponseEntity<RehearsalIssueActionResponse> updateRehearsalIssueActionStatus(
            UUID servicePlanId,
            UUID actionId,
            UpdateIssueActionStatusRequest request) {
        assertCurrentVersion(request.getExpectedVersion());
        return ResponseEntity.ok(toAction(service.changeActionStatus(
                servicePlanId,
                actionId,
                toActionStatus(request.getActionStatusCode()),
                request.getReason(),
                request.getReference())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER)")
    public ResponseEntity<EffectiveArrangementResponse> renderEffectiveArrangement(
            UUID servicePlanId,
            UUID arrangementId,
            UUID servicePlanBlockId,
            UUID setlistVersionItemId) {
        return ResponseEntity.ok(toEffectiveArrangement(renderingService.renderEffectiveArrangement(
                servicePlanId, servicePlanBlockId, setlistVersionItemId, arrangementId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ASSIGNED_MUSICIAN, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_REPORTING_VIEWER)")
    public ResponseEntity<List<ArrangementOverrideResponse>> listArrangementOverrides(UUID servicePlanId) {
        return ResponseEntity.ok(service.listArrangementOverrides(servicePlanId).stream().map(this::toOverride).toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<ArrangementOverrideResponse> createArrangementOverride(
            UUID servicePlanId,
            CreateArrangementOverrideRequest request) {
        return ResponseEntity.status(201).body(toOverride(service.createArrangementOverride(
                toOverrideRecord(null, servicePlanId, request), request.getReason(), request.getReference())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<ArrangementOverrideResponse> updateArrangementOverride(
            UUID servicePlanId,
            UUID arrangementOverrideId,
            UpdateArrangementOverrideRequest request) {
        assertCurrentVersion(request.getExpectedVersion());
        return ResponseEntity.ok(toOverride(service.updateArrangementOverride(
                toOverrideRecord(arrangementOverrideId, servicePlanId, request), request.getReason(), request.getReference())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, T(com.cadentia.api.security.RbacAuthorities).ROLE_WORSHIP_LEADER, T(com.cadentia.api.security.RbacAuthorities).ROLE_TEAM_SCHEDULER)")
    public ResponseEntity<Void> archiveArrangementOverride(UUID servicePlanId, UUID arrangementOverrideId) {
        service.archiveArrangementOverride(servicePlanId, arrangementOverrideId, "archived", null);
        return ResponseEntity.noContent().build();
    }

    private void assertCurrentVersion(Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != CURRENT_VERSION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VERSION_CONFLICT");
        }
    }

    private Map<UUID, List<RehearsalIssueActionRecord>> actionsByIssue(UUID servicePlanId) {
        return service.listIssueActions(servicePlanId).stream()
                .collect(Collectors.groupingBy(RehearsalIssueActionRecord::issueId));
    }

    private RehearsalSessionResponse toSession(RehearsalSessionRecord record) {
        return new RehearsalSessionResponse(
                record.rehearsalSessionId(),
                record.servicePlanId(),
                record.sessionCode(),
                offset(record.startsAt()),
                offset(record.endsAt()),
                toGenerated(record.readinessStateCode()),
                CURRENT_VERSION)
                .location(record.location())
                .archivedAt(offset(record.archivedAt()));
    }

    private WorkflowStatusResponse toWorkflowStatus(WorkflowStatus status) {
        return new WorkflowStatusResponse(
                status.servicePlanId(),
                toGenerated(status.explicitStateCode()),
                toGenerated(status.derivedStateCode()),
                status.openBlockingIssueCount(),
                status.openRequiredActionCount(),
                status.readyForService());
    }

    private RehearsalNoteResponse toNote(RehearsalNoteRecord note) {
        boolean redacted = isRedacted(note.visibilityCode());
        RehearsalNoteVisibilityCode visibility = RehearsalNoteVisibilityCode.fromValue(note.visibilityCode());
        return new RehearsalNoteResponse(note.noteId(), note.servicePlanId(), toGenerated(note.target()), visibility, redacted,
                offset(note.createdAt()))
                .noteBody(redacted ? null : note.noteBody())
                .createdBy(redacted ? null : note.createdBy());
    }

    private RehearsalIssueResponse toIssue(RehearsalIssueRecord issue, List<RehearsalIssueActionRecord> actions) {
        return new RehearsalIssueResponse(
                issue.issueId(),
                issue.servicePlanId(),
                toGenerated(issue.target()),
                RehearsalIssueCategoryCode.fromValue(issue.categoryCode().code()),
                RehearsalIssueSeverityCode.fromValue(issue.severityCode().code()),
                RehearsalIssueStatusCode.fromValue(issue.statusCode().code()),
                issue.title(),
                actions.stream().map(this::toAction).toList(),
                CURRENT_VERSION)
                .detail(issue.detail())
                .detectedBy(issue.detectedBy())
                .resolvedAt(offset(issue.resolvedAt()))
                .archivedAt(offset(issue.archivedAt()))
                .history(List.of());
    }

    private RehearsalIssueActionResponse toAction(RehearsalIssueActionRecord action) {
        return new RehearsalIssueActionResponse(
                action.actionId(),
                action.issueId(),
                action.servicePlanId(),
                RehearsalIssueActionStatusCode.fromValue(action.actionStatusCode().code()),
                action.actionSummary(),
                new RehearsalIssueActionOwner(RehearsalIssueOwnerType.fromValue(action.ownerType().code()))
                        .ownerActor(action.ownerActor())
                        .ownerTeamRoleCode(action.ownerTeamRoleCode())
                        .ownerServiceAssignmentId(action.ownerServiceAssignmentId()),
                CURRENT_VERSION)
                .completedAt(offset(action.completedAt()));
    }

    private EffectiveArrangementResponse toEffectiveArrangement(EffectiveArrangementRendering rendering) {
        return new EffectiveArrangementResponse(
                rendering.servicePlanId(),
                rendering.sourceArrangementId(),
                rendering.arrangementName(),
                rendering.hasServiceOverride(),
                toEffectiveValue(rendering.musicalKey()),
                toEffectiveValue(rendering.keyMode()),
                toEffectiveValue(rendering.tempoBpm()),
                toEffectiveValue(rendering.timeSignature()),
                toEffectiveValue(rendering.durationSeconds()),
                toEffectiveValue(rendering.energyLevel()),
                toEffectiveValue(rendering.difficultyLevel()),
                rendering.renderedTranspositionInterval(),
                rendering.transpositionSource(),
                toEffectiveProvenance(rendering.provenance()))
                .servicePlanBlockId(rendering.servicePlanBlockId())
                .setlistVersionItemId(rendering.setlistVersionItemId())
                .rehearsalNotes(toEffectiveValue(rendering.rehearsalNotes()))
                .capoFret(toEffectiveValue(rendering.capoFret()))
                .transpositionSemitones(toEffectiveValue(rendering.transpositionSemitones()))
                .chartAnnotations(toEffectiveValue(rendering.chartAnnotations()))
                .sectionOrderNotes(toEffectiveValue(rendering.sectionOrderNotes()))
                .transitionCues(toEffectiveValue(rendering.transitionCues()))
                .instrumentationNotes(toEffectiveValue(rendering.instrumentationNotes()))
                .assetSelectionNotes(toEffectiveValue(rendering.assetSelectionNotes()))
                .renderedLyricsContent(rendering.renderedLyricsContent())
                .renderedChordMapJson(rendering.renderedChordMapJson());
    }

    private EffectiveArrangementValueResponse toEffectiveValue(EffectiveArrangementValue<?> value) {
        return new EffectiveArrangementValueResponse(EffectiveArrangementValueResponse.ValueSourceEnum.fromValue(value.valueSource().name()))
                .sourceValue(value.sourceValue())
                .overrideValue(value.overrideValue())
                .effectiveValue(value.effectiveValue());
    }

    private EffectiveArrangementProvenanceResponse toEffectiveProvenance(EffectiveArrangementProvenance provenance) {
        return new EffectiveArrangementProvenanceResponse(provenance.sourceArrangementId(), provenance.auditReference())
                .sourceArrangementVersionRef(provenance.sourceArrangementVersionRef())
                .arrangementOverrideId(provenance.arrangementOverrideId())
                .provenanceNote(provenance.provenanceNote())
                .rationale(provenance.rationale())
                .createdBy(provenance.createdBy())
                .updatedBy(provenance.updatedBy());
    }

    private ArrangementOverrideResponse toOverride(ArrangementOverrideRecord record) {
        return new ArrangementOverrideResponse(
                record.arrangementOverrideId(),
                record.servicePlanId(),
                record.sourceArrangementId(),
                record.rationale(),
                record.provenanceNote(),
                CURRENT_VERSION)
                .servicePlanBlockId(record.servicePlanBlockId())
                .setlistVersionItemId(record.setlistVersionItemId())
                .sourceArrangementVersionRef(record.sourceArrangementVersionRef())
                .effectiveKey(record.effectiveKey())
                .effectiveMode(record.effectiveMode())
                .effectiveTempoBpm(record.effectiveTempoBpm())
                .effectiveTimeSignature(record.effectiveTimeSignature())
                .effectiveDurationSeconds(record.effectiveDurationSeconds())
                .effectiveEnergyLevel(record.effectiveEnergyLevel())
                .effectiveDifficultyLevel(record.effectiveDifficultyLevel())
                .effectiveNotes(record.effectiveNotes())
                .capoFret(record.capoFret())
                .transpositionSemitones(record.transpositionSemitones())
                .chartAnnotations(record.chartAnnotations())
                .sectionOrderNotes(record.sectionOrderNotes())
                .transitionCues(record.transitionCues())
                .instrumentationNotes(record.instrumentationNotes())
                .assetSelectionNotes(record.assetSelectionNotes())
                .createdBy(record.createdBy())
                .updatedBy(record.updatedBy());
    }

    private ArrangementOverrideRecord toOverrideRecord(UUID arrangementOverrideId, UUID servicePlanId, CreateArrangementOverrideRequest request) {
        return new ArrangementOverrideRecord(
                arrangementOverrideId,
                servicePlanId,
                request.getServicePlanBlockId(),
                request.getSetlistVersionItemId(),
                request.getSourceArrangementId(),
                request.getSourceArrangementVersionRef(),
                request.getEffectiveKey(),
                request.getEffectiveMode(),
                request.getEffectiveTempoBpm(),
                request.getEffectiveTimeSignature(),
                request.getEffectiveDurationSeconds(),
                request.getEffectiveEnergyLevel(),
                request.getEffectiveDifficultyLevel(),
                request.getEffectiveNotes(),
                request.getCapoFret(),
                request.getTranspositionSemitones(),
                request.getChartAnnotations(),
                request.getSectionOrderNotes(),
                request.getTransitionCues(),
                request.getInstrumentationNotes(),
                request.getAssetSelectionNotes(),
                request.getRationale(),
                request.getProvenanceNote(),
                currentActor(),
                currentActor());
    }


    private ArrangementOverrideRecord toOverrideRecord(UUID arrangementOverrideId, UUID servicePlanId, UpdateArrangementOverrideRequest request) {
        return new ArrangementOverrideRecord(
                arrangementOverrideId,
                servicePlanId,
                request.getServicePlanBlockId(),
                request.getSetlistVersionItemId(),
                request.getSourceArrangementId(),
                request.getSourceArrangementVersionRef(),
                request.getEffectiveKey(),
                request.getEffectiveMode(),
                request.getEffectiveTempoBpm(),
                request.getEffectiveTimeSignature(),
                request.getEffectiveDurationSeconds(),
                request.getEffectiveEnergyLevel(),
                request.getEffectiveDifficultyLevel(),
                request.getEffectiveNotes(),
                request.getCapoFret(),
                request.getTranspositionSemitones(),
                request.getChartAnnotations(),
                request.getSectionOrderNotes(),
                request.getTransitionCues(),
                request.getInstrumentationNotes(),
                request.getAssetSelectionNotes(),
                request.getRationale(),
                request.getProvenanceNote(),
                currentActor(),
                currentActor());
    }

    private boolean isRedacted(String visibilityCode) {
        return switch (visibilityCode) {
            case "public" -> false;
            case "team_private" -> !hasAnyRole(
                    RbacAuthorities.ROLE_ADMIN,
                    RbacAuthorities.ROLE_WORSHIP_LEADER,
                    RbacAuthorities.ROLE_TEAM_SCHEDULER,
                    RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                    RbacAuthorities.ROLE_MUSICAL_REVIEWER);
            case "pastoral_private" -> !hasAnyRole(RbacAuthorities.ROLE_ADMIN, RbacAuthorities.ROLE_WORSHIP_LEADER);
            default -> true;
        };
    }

    private boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> List.of(roles).contains(granted.getAuthority()));
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }

    private OffsetDateTime offset(java.time.Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private ReadinessStateCode toReadiness(RehearsalReadinessStateCode value) {
        return ReadinessStateCode.fromCode(value.getValue());
    }

    private RehearsalReadinessStateCode toGenerated(ReadinessStateCode value) {
        return RehearsalReadinessStateCode.fromValue(value.code());
    }

    private IssueCategoryCode toCategory(RehearsalIssueCategoryCode value) {
        return IssueCategoryCode.fromCode(value.getValue());
    }

    private IssueSeverityCode toSeverity(RehearsalIssueSeverityCode value) {
        return IssueSeverityCode.fromCode(value.getValue());
    }

    private IssueStatusCode toIssueStatus(RehearsalIssueStatusCode value) {
        return IssueStatusCode.fromCode(value.getValue());
    }

    private IssueActionStatusCode toActionStatus(RehearsalIssueActionStatusCode value) {
        return IssueActionStatusCode.fromCode(value.getValue());
    }

    private IssueOwnerType toOwnerType(RehearsalIssueOwnerType value) {
        return IssueOwnerType.fromCode(value.getValue());
    }

    private com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget toTarget(RehearsalWorkflowTarget target) {
        return new com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget(
                com.cadentia.rehearsal.RehearsalWorkflowModels.TargetTypeCode.fromCode(target.getTargetTypeCode().getValue()),
                target.getRehearsalSessionId(),
                target.getServicePlanBlockId(),
                target.getSetlistVersionItemId(),
                target.getTransitionFromBlockId(),
                target.getTransitionToBlockId(),
                target.getArrangementId(),
                target.getTeamRoleCode(),
                target.getServiceTeamAssignmentId(),
                target.getRehearsalTeamAssignmentId(),
                target.getSongAssignmentOverrideId());
    }

    private RehearsalWorkflowTarget toGenerated(com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalTarget target) {
        return new RehearsalWorkflowTarget(RehearsalWorkflowTargetTypeCode.fromValue(target.targetTypeCode().code()))
                .rehearsalSessionId(target.rehearsalSessionId())
                .servicePlanBlockId(target.servicePlanBlockId())
                .setlistVersionItemId(target.setlistVersionItemId())
                .transitionFromBlockId(target.transitionFromBlockId())
                .transitionToBlockId(target.transitionToBlockId())
                .arrangementId(target.arrangementId())
                .teamRoleCode(target.teamRoleCode())
                .serviceTeamAssignmentId(target.serviceTeamAssignmentId())
                .rehearsalTeamAssignmentId(target.rehearsalTeamAssignmentId())
                .songAssignmentOverrideId(target.songAssignmentOverrideId());
    }
}
