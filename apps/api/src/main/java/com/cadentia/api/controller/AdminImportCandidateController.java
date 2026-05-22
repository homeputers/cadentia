package com.cadentia.api.controller;

import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.generated.api.AdminReviewApi;
import com.cadentia.generated.model.AdminAuditHistoryItem;
import com.cadentia.generated.model.AdminDuplicateMatch;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminImportCandidateQueueItem;
import com.cadentia.generated.model.AdminReviewHistoryItem;
import com.cadentia.generated.model.AssignModerationFlagRequest;
import com.cadentia.generated.model.CreateRollbackPreviewRequest;
import com.cadentia.generated.model.ExecuteRollbackRequest;
import com.cadentia.generated.model.RollbackExecutionResponse;
import com.cadentia.generated.model.RollbackImpactedRecord;
import com.cadentia.generated.model.RollbackPreviewResponse;
import com.cadentia.generated.model.EscalateModerationFlagRequest;
import com.cadentia.generated.model.ImportCandidateStatus;
import com.cadentia.generated.model.ModerationFlagResponse;
import com.cadentia.generated.model.ModerationFlagStatus;
import com.cadentia.generated.model.OpenModerationFlagRequest;
import com.cadentia.generated.model.ResolveModerationFlagRequest;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.ModerationFlag;
import com.cadentia.scraperadmin.ModerationFlagType;
import com.cadentia.scraperadmin.RollbackExecutionResult;
import com.cadentia.scraperadmin.RollbackPreview;
import com.cadentia.scraperadmin.RollbackTargetType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminImportCandidateController implements AdminReviewApi {

    private final AdminImportReviewService reviewService;

    public AdminImportCandidateController(AdminImportReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.view','catalog.admin.review','catalog.admin.approve','catalog.admin.rollback')")
    public ResponseEntity<List<AdminImportCandidateQueueItem>> listAdminImportCandidates(
            @RequestParam UUID batchId,
            @RequestParam(required = false) ImportCandidateStatus status) {
        List<AdminImportCandidateQueueItem> response = reviewService.findCandidatesForBatch(
                        batchId,
                        status == null ? null : com.cadentia.catalog.model.ImportCandidateStatus.valueOf(status.getValue()))
                .stream()
                .map(candidate -> new AdminImportCandidateQueueItem()
                        .candidateId(candidate.id())
                        .importBatchId(candidate.importBatchId())
                        .rawTitle(candidate.rawTitle())
                        .normalizedTitle(candidate.normalizedTitle())
                        .sourceArtistName(candidate.sourceArtistName())
                        .status(ImportCandidateStatus.fromValue(candidate.status().name()))
                        .updatedAt(OffsetDateTime.ofInstant(candidate.updatedAt(), ZoneOffset.UTC)))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.view','catalog.admin.review','catalog.admin.approve','catalog.admin.rollback')")
    public ResponseEntity<AdminImportCandidateDetailResponse> getAdminImportCandidateDetail(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(toDetail(detail));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.view','catalog.admin.review','catalog.admin.approve','catalog.admin.rollback')")
    public ResponseEntity<List<AdminDuplicateMatch>> getAdminImportCandidateDuplicates(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.view','catalog.admin.review','catalog.admin.approve','catalog.admin.rollback')")
    public ResponseEntity<List<AdminAuditHistoryItem>> getAdminImportCandidateAuditHistory(@PathVariable UUID candidateId) {
        return ResponseEntity.ok(reviewService.getAuditHistory(candidateId).stream()
                .map(AdminImportCandidateController::toAuditHistoryItem)
                .toList());
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.review','catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> openAdminModerationFlag(
            @PathVariable UUID candidateId,
            @RequestBody OpenModerationFlagRequest request) {
        ModerationFlag flag = reviewService.openModerationFlag(
                candidateId,
                ModerationFlagType.valueOf(request.getType().getValue()),
                request.getOpenedBy(),
                request.getReason(),
                request.getExcludeFromRecommendation());
        return ResponseEntity.ok(toModerationFlagResponse(flag));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.review','catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> assignAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody AssignModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.assignModerationFlag(flagId, request.getAssignedTo(), request.getActor(), request.getReason())));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('catalog.admin.review','catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> resolveAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody ResolveModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.resolveModerationFlag(flagId, request.getActor(), request.getResolutionNotes())));
    }

    @Override
    @PreAuthorize("hasAuthority('catalog.admin.approve')")
    public ResponseEntity<ModerationFlagResponse> escalateAdminModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody EscalateModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.escalateModerationFlag(flagId, request.getActor(), request.getReason())));
    }


    @Override
    @PreAuthorize("hasAuthority('catalog.admin.rollback')")
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
                .blockers(preview.blockers())
                .impactedRecords(preview.impactedRecords().stream().map(record -> new RollbackImpactedRecord()
                        .entityType(String.valueOf(record.get("entityType")))
                        .entityId(String.valueOf(record.get("entityId")))
                        .status(String.valueOf(record.get("status"))))
                        .toList());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAuthority('catalog.admin.rollback')")
    public ResponseEntity<RollbackExecutionResponse> executeAdminRollback(@RequestBody ExecuteRollbackRequest request) {
        RollbackExecutionResult result =
                reviewService.executeRollback(request.getRollbackRequestId(), request.getActor(), request.getReason());
        return ResponseEntity.ok(new RollbackExecutionResponse()
                .rollbackRequestId(result.rollbackRequestId())
                .action(result.action())
                .auditEventId(result.auditEventId()));
    }

    private static AdminImportCandidateDetailResponse toDetail(AdminImportCandidateDetail detail) {
        return new AdminImportCandidateDetailResponse()
                .candidateId(detail.candidate().id())
                .importBatchId(detail.candidate().importBatchId())
                .rawTitle(detail.candidate().rawTitle())
                .normalizedTitle(detail.candidate().normalizedTitle())
                .sourceArtistName(detail.candidate().sourceArtistName())
                .sourcePayloadJson(detail.candidate().sourcePayloadJson())
                .rawSourceReference(detail.rawSourceReference())
                .parserName(detail.parserName())
                .parserVersion(detail.parserVersion())
                .parserConfidence(detail.parserConfidence())
                .parserWarnings(detail.parserWarnings())
                .status(ImportCandidateStatus.fromValue(detail.candidate().status().name()))
                .duplicateMatches(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList())
                .reviewHistory(detail.reviewHistory().stream().map(AdminImportCandidateController::toHistory).toList());
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
                .excludeFromRecommendation(flag.excludeFromRecommendation())
                .openedAt(OffsetDateTime.ofInstant(flag.openedAt(), ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.ofInstant(flag.updatedAt(), ZoneOffset.UTC));
    }

}
