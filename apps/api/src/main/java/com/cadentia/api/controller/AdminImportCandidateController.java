package com.cadentia.api.controller;

import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.generated.api.AdminReviewApi;
import com.cadentia.generated.model.AdminDuplicateMatch;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminImportCandidateQueueItem;
import com.cadentia.generated.model.AdminReviewHistoryItem;
import com.cadentia.generated.model.ImportCandidateStatus;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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
}
