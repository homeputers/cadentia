package com.cadentia.api.controller;

import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/import-candidates")
public class AdminImportCandidateController {

    private final AdminImportReviewService reviewService;

    public AdminImportCandidateController(AdminImportReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<List<AdminImportCandidateQueueItemResponse>> list(
            @RequestParam UUID batchId,
            @RequestParam(required = false) ImportCandidateStatus status) {
        List<AdminImportCandidateQueueItemResponse> response = reviewService.findCandidatesForBatch(batchId, status).stream()
                .map(AdminImportCandidateQueueItemResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{candidateId}")
    public ResponseEntity<AdminImportCandidateDetailResponse> detail(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(AdminImportCandidateDetailResponse.from(detail));
    }

    @GetMapping("/{candidateId}/duplicates")
    public ResponseEntity<List<AdminDuplicateMatchResponse>> duplicates(@PathVariable UUID candidateId) {
        AdminImportCandidateDetail detail = reviewService.getCandidateDetail(candidateId);
        return ResponseEntity.ok(detail.duplicateMatches().stream().map(AdminDuplicateMatchResponse::from).toList());
    }

    public static record AdminImportCandidateQueueItemResponse(
            UUID candidateId,
            UUID importBatchId,
            String rawTitle,
            String normalizedTitle,
            String sourceArtistName,
            ImportCandidateStatus status) {

        private static AdminImportCandidateQueueItemResponse from(ImportCandidate candidate) {
            return new AdminImportCandidateQueueItemResponse(
                    candidate.id(),
                    candidate.importBatchId(),
                    candidate.rawTitle(),
                    candidate.normalizedTitle(),
                    candidate.sourceArtistName(),
                    candidate.status());
        }
    }

    public static record AdminImportCandidateDetailResponse(
            UUID candidateId,
            UUID importBatchId,
            String rawTitle,
            String normalizedTitle,
            String sourceArtistName,
            String sourcePayloadJson,
            String rawSourceReference,
            String parserName,
            String parserVersion,
            String parserConfidence,
            List<String> parserWarnings,
            ImportCandidateStatus status,
            List<AdminDuplicateMatchResponse> duplicateMatches,
            List<AdminReviewHistoryItemResponse> reviewHistory) {

        private static AdminImportCandidateDetailResponse from(AdminImportCandidateDetail detail) {
            ImportCandidate candidate = detail.candidate();
            return new AdminImportCandidateDetailResponse(
                    candidate.id(),
                    candidate.importBatchId(),
                    candidate.rawTitle(),
                    candidate.normalizedTitle(),
                    candidate.sourceArtistName(),
                    candidate.sourcePayloadJson(),
                    detail.rawSourceReference(),
                    detail.parserName(),
                    detail.parserVersion(),
                    detail.parserConfidence(),
                    detail.parserWarnings(),
                    candidate.status(),
                    detail.duplicateMatches().stream().map(AdminDuplicateMatchResponse::from).toList(),
                    detail.reviewHistory().stream().map(AdminReviewHistoryItemResponse::from).toList());
        }
    }

    public static record AdminDuplicateMatchResponse(UUID id, UUID candidateSongId, String matchScore, String matchSignalsJson, String status) {
        private static AdminDuplicateMatchResponse from(ProposedDuplicateMatch match) {
            return new AdminDuplicateMatchResponse(
                    match.id(),
                    match.candidateSongId(),
                    match.matchScore().toPlainString(),
                    match.matchSignalsJson(),
                    match.status().name());
        }
    }

    public static record AdminReviewHistoryItemResponse(
            UUID id,
            UUID proposedDuplicateMatchId,
            String decision,
            String reviewer,
            String reviewNotes,
            String reviewedAt) {
        private static AdminReviewHistoryItemResponse from(ImportCandidateReview review) {
            return new AdminReviewHistoryItemResponse(
                    review.id(),
                    review.proposedDuplicateMatchId(),
                    review.decision().name(),
                    review.reviewer(),
                    review.reviewNotes(),
                    review.reviewedAt().toString());
        }
    }
}
