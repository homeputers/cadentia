package com.cadentia.api.controller;

import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.generated.api.AdminReviewApi;
import com.cadentia.generated.model.AdminDuplicateMatch;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminImportCandidateQueueItem;
import com.cadentia.generated.model.AdminReviewHistoryItem;
import com.cadentia.generated.model.ImportCandidateStatus;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.ModerationFlag;
import com.cadentia.scraperadmin.ModerationFlagType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ResponseEntity<AdminImportCandidateDetailResponse> getAdminImportCandidateDetail(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(toDetail(detail));
    }

    @Override
    public ResponseEntity<List<AdminDuplicateMatch>> getAdminImportCandidateDuplicates(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(detail.duplicateMatches().stream().map(AdminImportCandidateController::toDuplicate).toList());
    }

    @GetMapping("/api/admin/import-candidates/{candidateId}/audit-history")
    public ResponseEntity<List<AuditHistoryItemResponse>> getAuditHistory(@PathVariable UUID candidateId) {
        return ResponseEntity.ok(reviewService.getAuditHistory(candidateId).stream()
                .map(AdminImportCandidateController::toAuditHistoryItem)
                .toList());
    }

    @PostMapping("/api/admin/import-candidates/{candidateId}/moderation-flags")
    public ResponseEntity<ModerationFlagResponse> openModerationFlag(
            @PathVariable UUID candidateId,
            @RequestBody OpenModerationFlagRequest request) {
        ModerationFlag flag = reviewService.openModerationFlag(
                candidateId,
                ModerationFlagType.valueOf(request.type()),
                request.openedBy(),
                request.reason(),
                request.excludeFromRecommendation());
        return ResponseEntity.ok(toModerationFlagResponse(flag));
    }

    @PostMapping("/api/admin/moderation-flags/{flagId}/assign")
    public ResponseEntity<ModerationFlagResponse> assignModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody AssignModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.assignModerationFlag(flagId, request.assignedTo(), request.actor(), request.reason())));
    }

    @PostMapping("/api/admin/moderation-flags/{flagId}/resolve")
    public ResponseEntity<ModerationFlagResponse> resolveModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody ResolveModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.resolveModerationFlag(flagId, request.actor(), request.resolutionNotes())));
    }

    @PostMapping("/api/admin/moderation-flags/{flagId}/escalate")
    public ResponseEntity<ModerationFlagResponse> escalateModerationFlag(
            @PathVariable UUID flagId,
            @RequestBody EscalateModerationFlagRequest request) {
        return ResponseEntity.ok(toModerationFlagResponse(
                reviewService.escalateModerationFlag(flagId, request.actor(), request.reason())));
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

    private static AuditHistoryItemResponse toAuditHistoryItem(AdminAuditEvent event) {
        return new AuditHistoryItemResponse(
                event.id(),
                event.entityId(),
                event.entityType(),
                event.action(),
                event.actor(),
                OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                event.reason(),
                event.beforeState(),
                event.afterState());
    }

    private static ModerationFlagResponse toModerationFlagResponse(ModerationFlag flag) {
        return new ModerationFlagResponse(
                flag.id(),
                flag.importCandidateId(),
                flag.type().name(),
                flag.status().name(),
                flag.openedBy(),
                flag.assignedTo(),
                flag.resolutionNotes(),
                flag.excludeFromRecommendation(),
                OffsetDateTime.ofInstant(flag.openedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(flag.updatedAt(), ZoneOffset.UTC));
    }

    public record OpenModerationFlagRequest(String type, String openedBy, String reason, boolean excludeFromRecommendation) {
    }

    public record AssignModerationFlagRequest(String assignedTo, String actor, String reason) {
    }

    public record ResolveModerationFlagRequest(String actor, String resolutionNotes) {
    }

    public record EscalateModerationFlagRequest(String actor, String reason) {
    }

    public record AuditHistoryItemResponse(
            UUID id,
            UUID entityId,
            String entityType,
            String action,
            String actor,
            OffsetDateTime occurredAt,
            String reason,
            Object beforeState,
            Object afterState) {
    }

    public record ModerationFlagResponse(
            UUID id,
            UUID importCandidateId,
            String type,
            String status,
            String openedBy,
            String assignedTo,
            String resolutionNotes,
            boolean excludeFromRecommendation,
            OffsetDateTime openedAt,
            OffsetDateTime updatedAt) {
    }
}
