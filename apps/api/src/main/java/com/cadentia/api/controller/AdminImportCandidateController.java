package com.cadentia.api.controller;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.generated.api.AdminReviewApi;
import com.cadentia.generated.model.AdminApprovalState;
import com.cadentia.generated.model.AdminApprovalStateStatusesInner;
import com.cadentia.generated.model.AdminAuditHistoryItem;
import com.cadentia.generated.model.AdminDuplicateMatch;
import com.cadentia.generated.model.AdminDuplicateSummary;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminImportCandidateQueueItem;
import com.cadentia.generated.model.AdminImportCandidateQueueResponse;
import com.cadentia.generated.model.AdminParserEvidence;
import com.cadentia.generated.model.AdminProvenanceReference;
import com.cadentia.generated.model.AdminReviewHistoryItem;
import com.cadentia.generated.model.AdminReviewNote;
import com.cadentia.generated.model.AllowedImportCandidateAction;
import com.cadentia.generated.model.ApprovalActionRequest;
import com.cadentia.generated.model.ApprovalReadiness;
import com.cadentia.generated.model.AssignModerationFlagRequest;
import com.cadentia.generated.model.CreateAdminReviewNoteRequest;
import com.cadentia.generated.model.CreateRollbackPreviewRequest;
import com.cadentia.generated.model.DuplicateConfidence;
import com.cadentia.generated.model.EscalateModerationFlagRequest;
import com.cadentia.generated.model.ExecuteRollbackRequest;
import com.cadentia.generated.model.ImportCandidateStatus;
import com.cadentia.generated.model.ModerationFlagResponse;
import com.cadentia.generated.model.ModerationFlagStatus;
import com.cadentia.generated.model.ModerationState;
import com.cadentia.generated.model.MergeDecisionRequest;
import com.cadentia.generated.model.OpenModerationFlagRequest;
import com.cadentia.generated.model.ParserSeverity;
import com.cadentia.generated.model.ProvenanceStatus;
import com.cadentia.generated.model.ResolveModerationFlagRequest;
import com.cadentia.generated.model.ReviewPriority;
import com.cadentia.generated.model.RollbackExecutionResponse;
import com.cadentia.generated.model.RollbackImpactedRecord;
import com.cadentia.generated.model.RollbackPreviewResponse;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.ApplyApprovalActionCommand;
import com.cadentia.scraperadmin.ApprovalReviewAction;
import com.cadentia.scraperadmin.ModerationFlag;
import com.cadentia.scraperadmin.ModerationFlagSeverity;
import com.cadentia.scraperadmin.ModerationFlagType;
import com.cadentia.scraperadmin.RollbackExecutionResult;
import com.cadentia.scraperadmin.RollbackPreview;
import com.cadentia.scraperadmin.RollbackTargetType;
import com.cadentia.scraperadmin.StructuredReviewNote;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminImportCandidateController implements AdminReviewApi {

    private final AdminImportReviewService reviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminImportCandidateController(AdminImportReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminImportCandidateQueueResponse> listAdminImportCandidates(
            @RequestParam(required = false) ImportCandidateStatus status,
            @RequestParam(required = false) String connectorKey,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) LocalDate submittedFrom,
            @RequestParam(required = false) LocalDate submittedTo,
            @RequestParam(required = false) String assignedReviewerId,
            @RequestParam(required = false) ParserSeverity parserSeverity,
            @RequestParam(required = false) ProvenanceStatus provenanceStatus,
            @RequestParam(required = false) DuplicateConfidence duplicateConfidence,
            @RequestParam(required = false) ModerationState moderationState,
            @RequestParam(required = false) ReviewPriority reviewPriority,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer pageSize) {
        List<AdminImportCandidateQueueItem> items = batchId == null
                ? List.of()
                : reviewService.findCandidatesForBatch(
                        batchId,
                        status == null ? null : com.cadentia.catalog.model.ImportCandidateStatus.valueOf(status.getValue()))
                .stream()
                .map(candidate -> new AdminImportCandidateQueueItem()
                        .candidateId(candidate.id())
                        .importBatchId(candidate.importBatchId())
                        .connectorKey("import")
                        .rawTitle(candidate.rawTitle())
                        .normalizedTitle(candidate.normalizedTitle())
                        .sourceArtistName(candidate.sourceArtistName())
                        .status(ImportCandidateStatus.fromValue(candidate.status().name()))
                        .submittedAt(OffsetDateTime.ofInstant(candidate.createdAt(), ZoneOffset.UTC))
                        .updatedAt(OffsetDateTime.ofInstant(candidate.updatedAt(), ZoneOffset.UTC))
                        .parserSeverity(ParserSeverity.NONE)
                        .parserWarningCount(0)
                        .provenanceStatus(ProvenanceStatus.NEEDS_REVIEW)
                        .duplicateConfidence(DuplicateConfidence.NONE)
                        .duplicateMatchCount(0)
                        .moderationState(ModerationState.CLEAR)
                        .reviewPriority(ReviewPriority.NORMAL)
                        .approvalReadiness(ApprovalReadiness.NEEDS_REVIEW)
                        .allowedActions(List.of(AllowedImportCandidateAction.VIEW_DETAIL)))
                .toList();
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 25 : pageSize;
        AdminImportCandidateQueueResponse response = new AdminImportCandidateQueueResponse()
                .items(items)
                .page(safePage)
                .pageSize(safePageSize)
                .totalItems(items.size())
                .totalPages(items.isEmpty() ? 0 : 1)
                .sort(sort == null || sort.isBlank() ? "updatedAt:desc" : sort);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminImportCandidateDetailResponse> getAdminImportCandidateDetail(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(toDetail(detail));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminReviewNote> createAdminImportCandidateReviewNote(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody CreateAdminReviewNoteRequest request) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        String expectedEtag = etagFor(detail);
        if (!expectedEtag.equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate version is stale");
        }
        ImportCandidateReview review = reviewService.addStructuredNote(
                candidateId,
                request.getActor(),
                new StructuredReviewNote(
                        request.getCategory() == null ? "GENERAL" : request.getCategory(),
                        request.getBody(),
                        null));
        return ResponseEntity.ok(toReviewNote(review));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<AdminDuplicateMatch>> getAdminImportCandidateDuplicates(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_DOCTRINAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_MUSICAL_REVIEWER, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<List<AdminAuditHistoryItem>> getAdminImportCandidateAuditHistory(@PathVariable UUID candidateId) {
        return ResponseEntity.ok(reviewService.getAuditHistory(candidateId).stream()
                .map(AdminImportCandidateController::toAuditHistoryItem)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<AdminImportCandidateDetailResponse> submitAdminImportCandidateMergeDecision(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody MergeDecisionRequest request) {
        requireFreshCandidate(candidateId, ifMatch);
        reviewService.submitMergeDecision(
                candidateId,
                request.getActor(),
                request.getDecision().getValue(),
                request.getDuplicateMatchId(),
                request.getRationale());
        return ResponseEntity.ok(toDetail(reviewService.getCandidateDetail(candidateId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<AdminImportCandidateDetailResponse> submitAdminImportCandidateApprovalAction(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody ApprovalActionRequest request) {
        AdminImportCandidateDetail detail = requireFreshCandidate(candidateId, ifMatch);
        if (detail.candidate().mergedSongId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate must be merged before approval actions");
        }
        reviewService.applyApprovalAction(new ApplyApprovalActionCommand(
                detail.candidate().mergedSongId(),
                null,
                null,
                ApprovalType.valueOf(request.getApprovalType()),
                approvalActionFor(request.getAction()),
                request.getActor(),
                request.getRationale()));
        return ResponseEntity.ok(toDetail(reviewService.getCandidateDetail(candidateId)));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> openAdminModerationFlag(
            @PathVariable UUID candidateId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody OpenModerationFlagRequest request) {
        requireFreshCandidate(candidateId, ifMatch);
        ModerationFlag flag = reviewService.openModerationFlag(
                candidateId,
                ModerationFlagType.valueOf(request.getType().getValue()),
                Boolean.TRUE.equals(request.getExcludeFromRecommendation()) ? ModerationFlagSeverity.HIGH : ModerationFlagSeverity.WARNING,
                request.getOpenedBy(),
                request.getReason());
        return ResponseEntity.ok(toModerationFlagResponse(flag));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> assignAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody AssignModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.assignModerationFlag(flagId, request.getAssignedTo(), request.getActor(), request.getReason())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_CATALOG_EDITOR, T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN, 'catalog.admin.review', 'catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> resolveAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody ResolveModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.resolveModerationFlag(flagId, request.getActor(), request.getResolutionNotes())));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<ModerationFlagResponse> escalateAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody EscalateModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.escalateModerationFlag(flagId, request.getActor(), request.getReason())));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<RollbackPreviewResponse> createAdminRollbackPreview(@RequestBody CreateRollbackPreviewRequest request) {
        RollbackPreview preview = reviewService.previewRollback(
                RollbackTargetType.valueOf(request.getTargetType().getValue()),
                request.getTargetId(),
                request.getActor(),
                request.getImportBatchId());
        RollbackPreviewResponse response = new RollbackPreviewResponse()
                .rollbackRequestId(preview.rollbackRequestId())
                .targetType(com.cadentia.generated.model.RollbackTargetType.fromValue(preview.targetType().name()))
                .targetId(preview.targetId())
                .importBatchId(preview.importBatchId())
                .eligibilityImpacted(preview.eligibilityImpacted())
                .blockers(preview.blockingCodes())
                .impactedRecords(preview.impactedRecords().stream().map(record -> new RollbackImpactedRecord()
                        .entityType(String.valueOf(record.get("entityType")))
                        .entityId(String.valueOf(record.get("entityId")))
                        .status(String.valueOf(record.get("status"))))
                        .toList());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<RollbackExecutionResponse> executeAdminRollback(@RequestBody ExecuteRollbackRequest request) {
        RollbackExecutionResult result =
                reviewService.executeRollback(request.getRollbackRequestId(), request.getActor(), request.getReason());
        return ResponseEntity.ok(new RollbackExecutionResponse()
                .rollbackRequestId(result.rollbackRequestId())
                .action(result.action())
                .auditEventId(result.auditEventId()));
    }

    private AdminImportCandidateDetailResponse toDetail(AdminImportCandidateDetail detail) {
        List<AdminReviewNote> reviewNotes = detail.reviewHistory().stream()
                .filter(review -> review.decision() == com.cadentia.catalog.model.ImportCandidateReviewDecision.NEEDS_MORE_INFO)
                .map(this::toReviewNote)
                .toList();
        double topDuplicateScore = detail.duplicateMatches().stream()
                .map(ProposedDuplicateMatch::matchScore)
                .filter(score -> score != null)
                .mapToDouble(score -> score.doubleValue())
                .max()
                .orElse(0.0);
        DuplicateConfidence duplicateConfidence = topDuplicateScore >= 0.9
                ? DuplicateConfidence.HIGH
                : topDuplicateScore >= 0.75 ? DuplicateConfidence.MEDIUM : DuplicateConfidence.NONE;
        return new AdminImportCandidateDetailResponse()
                .candidateId(detail.candidate().id())
                .importBatchId(detail.candidate().importBatchId())
                .connectorKey("import")
                .rawTitle(detail.candidate().rawTitle())
                .normalizedTitle(detail.candidate().normalizedTitle())
                .sourceArtistName(detail.candidate().sourceArtistName())
                .sourcePayloadRedacted(true)
                .sourcePayloadJson(null)
                .rawSourceReference(detail.rawSourceReference())
                .parserName(detail.parserName())
                .parserVersion(detail.parserVersion())
                .parserConfidence(detail.parserConfidence())
                .parserWarnings(detail.parserWarnings())
                .status(ImportCandidateStatus.fromValue(detail.candidate().status().name()))
                .allowedActions(allowedActions(detail))
                .version(versionFor(detail))
                .etag(etagFor(detail))
                .eligibilityBlockers(eligibilityBlockers(detail))
                .duplicateSummary(new AdminDuplicateSummary()
                        .confidence(duplicateConfidence)
                        .matchCount(detail.duplicateMatches().size())
                        .topScore(topDuplicateScore == 0.0 ? null : topDuplicateScore)
                        .summary(detail.duplicateMatches().isEmpty() ? "No duplicate detected" : "Backend duplicate signals require review"))
                .provenanceReferences(List.of(new AdminProvenanceReference()
                        .label("import")
                        .sourceReference(sourceReferenceFor(detail))
                        .fingerprint(detail.candidate().lyricsHash())
                        .status(ProvenanceStatus.NEEDS_REVIEW)))
                .parserEvidence(new AdminParserEvidence()
                        .parserName(detail.parserName())
                        .parserVersion(detail.parserVersion())
                        .confidence(parseDouble(detail.parserConfidence()))
                        .severity(detail.parserWarnings().isEmpty() ? ParserSeverity.NONE : ParserSeverity.WARNING)
                        .warnings(detail.parserWarnings())
                        .evidenceReferences(List.of()))
                .reviewNotes(reviewNotes)
                .relatedAuditReferences(List.of())
                .duplicateMatches(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList())
                .reviewHistory(detail.reviewHistory().stream().map(AdminImportCandidateController::toHistory).toList())
                .approvalState(approvalState(detail))
                .moderationFlags(reviewService.listModerationFlagsForCandidate(detail.candidate().id()).stream()
                        .map(AdminImportCandidateController::toModerationFlagResponse)
                        .toList());
    }

    private AdminImportCandidateDetail requireFreshCandidate(UUID candidateId, String ifMatch) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        String expectedEtag = etagFor(detail);
        if (!expectedEtag.equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate version is stale");
        }
        return detail;
    }

    private static List<AllowedImportCandidateAction> allowedActions(AdminImportCandidateDetail detail) {
        List<AllowedImportCandidateAction> actions = new java.util.ArrayList<>();
        actions.add(AllowedImportCandidateAction.VIEW_DETAIL);
        actions.add(AllowedImportCandidateAction.ADD_REVIEW_NOTE);
        actions.add(AllowedImportCandidateAction.OPEN_MODERATION_FLAG);
        actions.add(AllowedImportCandidateAction.MERGE_DECISION_DEFER);
        actions.add(AllowedImportCandidateAction.MERGE_DECISION_CREATE_NEW);
        if (!detail.duplicateMatches().isEmpty()) {
            actions.add(AllowedImportCandidateAction.MERGE_DECISION_MERGE_EXISTING);
            actions.add(AllowedImportCandidateAction.MERGE_DECISION_REJECT_DUPLICATE);
        }
        actions.add(AllowedImportCandidateAction.MERGE_DECISION_REJECT_NOT_PERMITTED);
        if (detail.candidate().mergedSongId() != null) {
            actions.add(AllowedImportCandidateAction.SUBMIT_APPROVAL_ACTION);
            actions.add(AllowedImportCandidateAction.REVERSE_APPROVAL);
        }
        return actions;
    }

    private AdminApprovalState approvalState(AdminImportCandidateDetail detail) {
        List<ApprovalRecord> approvals = detail.candidate().mergedSongId() == null
                ? List.of()
                : reviewService.findApprovalRecordsForSong(detail.candidate().mergedSongId());
        List<AdminApprovalStateStatusesInner> statuses = approvals.stream()
                .map(record -> new AdminApprovalStateStatusesInner()
                        .type(record.approvalType().name())
                        .status(record.status().name())
                        .actor(record.reviewer()))
                .toList();
        List<String> blockers = detail.candidate().mergedSongId() == null
                ? List.of("Candidate must be merged before approval actions")
                : approvals.stream().anyMatch(record -> record.status() == com.cadentia.catalog.model.ApprovalStatus.APPROVED)
                        ? List.of()
                        : List.of("At least one approved approval record is required for recommendation eligibility");
        return new AdminApprovalState()
                .requiredTypes(List.of(ApprovalType.EDITORIAL.name(), ApprovalType.DOCTRINAL.name()))
                .statuses(statuses)
                .blockers(blockers)
                .allowedTransitions(List.of("APPROVE", "REJECT_APPROVAL", "REVERSE_APPROVAL"))
                .eligibilityImpact(blockers.isEmpty()
                        ? "Backend approval gates may permit recommendation eligibility when provenance and moderation also pass."
                        : "Backend approval gates currently block recommendation eligibility.");
    }

    private static ApprovalReviewAction approvalActionFor(ApprovalActionRequest.ActionEnum action) {
        return switch (action) {
            case APPROVE -> ApprovalReviewAction.APPROVE;
            case REJECT_APPROVAL -> ApprovalReviewAction.REJECT;
            case REVERSE_APPROVAL -> ApprovalReviewAction.REVOKE;
        };
    }

    private static String sourceReferenceFor(AdminImportCandidateDetail detail) {
        if (detail.rawSourceReference() != null && !detail.rawSourceReference().isBlank()) {
            return detail.rawSourceReference();
        }
        if (detail.candidate().externalCandidateId() != null && !detail.candidate().externalCandidateId().isBlank()) {
            return detail.candidate().externalCandidateId();
        }
        return detail.candidate().id().toString();
    }

    private List<String> eligibilityBlockers(AdminImportCandidateDetail detail) {
        if (detail.candidate().status() == com.cadentia.catalog.model.ImportCandidateStatus.REJECTED
                || detail.candidate().status() == com.cadentia.catalog.model.ImportCandidateStatus.FAILED) {
            return List.of("Candidate status is " + detail.candidate().status().name());
        }
        if (!detail.parserWarnings().isEmpty()) {
            return List.of("Parser warnings require reviewer acknowledgement");
        }
        return List.of();
    }

    private static long versionFor(AdminImportCandidateDetail detail) {
        return detail.candidate().updatedAt().toEpochMilli();
    }

    private static String etagFor(AdminImportCandidateDetail detail) {
        return "\"candidate-" + detail.candidate().id() + "-v" + versionFor(detail) + "\"";
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static AdminDuplicateMatch toDuplicate(ProposedDuplicateMatch match) {
        return new AdminDuplicateMatch()
                .id(match.id())
                .candidateSongId(match.candidateSongId())
                .matchScore(match.matchScore() == null ? null : match.matchScore().doubleValue())
                .matchSignalsJson(match.matchSignalsJson())
                .status(match.status().name());
    }

    private static AdminReviewHistoryItem toHistory(ImportCandidateReview review) {
        return new AdminReviewHistoryItem()
                .id(review.id())
                .proposedDuplicateMatchId(review.proposedDuplicateMatchId())
                .decision(review.decision().name())
                .reviewer(review.reviewer())
                .reviewNotes(review.reviewNotes())
                .reviewedAt(OffsetDateTime.ofInstant(review.reviewedAt(), ZoneOffset.UTC));
    }

    private AdminReviewNote toReviewNote(ImportCandidateReview review) {
        Map<String, Object> note = parseNote(review.reviewNotes());
        return new AdminReviewNote()
                .noteId(review.id())
                .authorId(review.reviewer())
                .authorDisplayName(review.reviewer())
                .category(String.valueOf(note.getOrDefault("category", review.decision().name())))
                .body(String.valueOf(note.getOrDefault("body", review.reviewNotes())))
                .createdAt(OffsetDateTime.ofInstant(review.reviewedAt(), ZoneOffset.UTC));
    }

    private Map<String, Object> parseNote(String reviewNotes) {
        try {
            return objectMapper.readValue(reviewNotes == null ? "{}" : reviewNotes, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static AdminAuditHistoryItem toAuditHistoryItem(AdminAuditEvent event) {
        return new AdminAuditHistoryItem()
                .id(event.id())
                .entityId(event.entityId())
                .entityType(event.entityType())
                .action(event.action())
                .actor(event.actor())
                .occurredAt(OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .reason(event.reason())
                .beforeState(event.beforeState())
                .afterState(event.afterState());
    }

    private static ModerationFlagResponse toModerationFlagResponse(ModerationFlag flag) {
        return new ModerationFlagResponse()
                .id(flag.id())
                .importCandidateId(flag.importCandidateId())
                .type(com.cadentia.generated.model.ModerationFlagType.fromValue(flag.type().name()))
                .status(ModerationFlagStatus.fromValue(flag.status().name()))
                .openedBy(flag.openedBy())
                .assignedTo(flag.assignedTo())
                .resolutionNotes(flag.resolutionNotes())
                .scope("IMPORT_CANDIDATE")
                .reason(flag.resolutionNotes())
                .eligibilityImpactPolicy(flag.excludeFromRecommendation()
                        ? "BLOCK_UNTIL_RESOLVED"
                        : "REVIEW_ONLY")
                .excludeFromRecommendation(flag.excludeFromRecommendation())
                .openedAt(OffsetDateTime.ofInstant(flag.openedAt(), ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.ofInstant(flag.updatedAt(), ZoneOffset.UTC));
    }
}
