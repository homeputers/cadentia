package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.generated.model.AdminAuditEventSearchResponse;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminReviewNote;
import com.cadentia.generated.model.ApprovalActionRequest;
import com.cadentia.generated.model.CreateAdminReviewNoteRequest;
import com.cadentia.generated.model.MergeDecisionRequest;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.ApplyApprovalActionCommand;
import com.cadentia.scraperadmin.ModerationFlag;
import com.cadentia.scraperadmin.StructuredReviewNote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminImportCandidateControllerTest {

    @Test
    void detailResponseRedactsRawPayloadAndPopulatesBackendFacts() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.READY_TO_MERGE);
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate,
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.72",
                List.of("low-confidence"),
                List.of(),
                List.of()),
                null);
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);

        // Act
        AdminImportCandidateDetailResponse response = controller.getAdminImportCandidateDetail(candidateId).getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSourcePayloadRedacted()).isTrue();
        assertThat(response.getSourcePayloadJson()).isNull();
        assertThat(response.getConnectorKey()).isEqualTo("import");
        assertThat(response.getEtag()).isEqualTo("\"candidate-" + candidateId + "-v0\"");
        assertThat(response.getParserEvidence().getWarnings()).containsExactly("low-confidence");
        assertThat(response.getEligibilityBlockers()).containsExactly("Parser warnings require reviewer acknowledgement");
    }

    @Test
    void createReviewNoteRequiresMatchingCandidateEtagAndPersistsStructuredNote() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW);
        ImportCandidateReview savedReview = review(candidateId);
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate,
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                savedReview);
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);
        CreateAdminReviewNoteRequest request = new CreateAdminReviewNoteRequest()
                .actor("reviewer-1")
                .category("PROVENANCE")
                .body("Source reference looks safe.");

        // Act
        AdminReviewNote response = controller.createAdminImportCandidateReviewNote(
                candidateId,
                "\"candidate-" + candidateId + "-v0\"",
                request).getBody();

        // Assert
        assertThat(reviewService.addedCandidateId).isEqualTo(candidateId);
        assertThat(reviewService.addedActor).isEqualTo("reviewer-1");
        assertThat(reviewService.addedNote.category()).isEqualTo("PROVENANCE");
        assertThat(reviewService.addedNote.body()).isEqualTo("Source reference looks safe.");
        assertThat(response).isNotNull();
        assertThat(response.getNoteId()).isEqualTo(savedReview.id());
        assertThat(response.getBody()).isEqualTo("Source reference looks safe.");
    }

    @Test
    void createReviewNoteRejectsStaleCandidateEtag() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW),
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                null);
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);
        CreateAdminReviewNoteRequest request = new CreateAdminReviewNoteRequest()
                .actor("reviewer-1")
                .category("GENERAL")
                .body("Needs a reload.");

        // Act / Assert
        assertThatThrownBy(() -> controller.createAdminImportCandidateReviewNote(candidateId, "\"stale\"", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Candidate version is stale");
    }

    @Test
    void submitMergeDecisionRequiresFreshEtagAndPersistsBackendDecision() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        UUID duplicateMatchId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW);
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate,
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                review(candidateId));
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);
        MergeDecisionRequest request = new MergeDecisionRequest()
                .actor("reviewer-1")
                .decision(MergeDecisionRequest.DecisionEnum.MERGE_EXISTING)
                .duplicateMatchId(duplicateMatchId)
                .rationale("Same title and source fingerprint.");

        // Act
        AdminImportCandidateDetailResponse response = controller.submitAdminImportCandidateMergeDecision(
                candidateId,
                "\"candidate-" + candidateId + "-v0\"",
                request).getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(reviewService.mergeCandidateId).isEqualTo(candidateId);
        assertThat(reviewService.mergeActor).isEqualTo("reviewer-1");
        assertThat(reviewService.mergeDecision).isEqualTo("MERGE_EXISTING");
        assertThat(reviewService.mergeDuplicateMatchId).isEqualTo(duplicateMatchId);
        assertThat(reviewService.mergeRationale).isEqualTo("Same title and source fingerprint.");
    }

    @Test
    void submitMergeDecisionRejectsStaleCandidateEtagBeforeMutation() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW),
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                null);
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);
        MergeDecisionRequest request = new MergeDecisionRequest()
                .actor("reviewer-1")
                .decision(MergeDecisionRequest.DecisionEnum.DEFER)
                .rationale("Needs another reviewer.");

        // Act / Assert
        assertThatThrownBy(() -> controller.submitAdminImportCandidateMergeDecision(candidateId, "\"stale\"", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Candidate version is stale");
        assertThat(reviewService.mergeCandidateId).isNull();
    }

    @Test
    void submitApprovalActionRequiresMergedCandidateAndPassesActorVersionContext() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        UUID mergedSongId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.MERGED, mergedSongId);
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate,
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.98",
                List.of(),
                List.of(),
                List.of()),
                null);
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);
        ApprovalActionRequest request = new ApprovalActionRequest()
                .actor("doctrine-reviewer")
                .approvalType("DOCTRINAL")
                .action(ApprovalActionRequest.ActionEnum.REVERSE_APPROVAL)
                .rationale("Approval was entered for the wrong candidate.");

        // Act
        AdminImportCandidateDetailResponse response = controller.submitAdminImportCandidateApprovalAction(
                candidateId,
                "\"candidate-" + candidateId + "-v0\"",
                request).getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(reviewService.approvalCommand.songId()).isEqualTo(mergedSongId);
        assertThat(reviewService.approvalCommand.approvalType()).isEqualTo(ApprovalType.DOCTRINAL);
        assertThat(reviewService.approvalCommand.reviewer()).isEqualTo("doctrine-reviewer");
        assertThat(reviewService.approvalCommand.reviewNotes()).isEqualTo("Approval was entered for the wrong candidate.");
    }

    @Test
    void searchAuditEventsUsesBackendFiltersAndRedactsPayloadSummaries() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID rollbackRequestId = UUID.randomUUID();
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW),
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                null);
        reviewService.auditEvents = List.of(new AdminAuditEvent(
                eventId,
                candidateId,
                "IMPORT_CANDIDATE",
                "ROLLBACK_IMPORT_CANDIDATE_STATUS",
                "rollback-admin",
                Instant.EPOCH,
                "contains token=secret",
                Map.of("rawPayload", "secret"),
                Map.of("rollbackRequestId", rollbackRequestId.toString(), "connectorPayload", "secret")));
        AdminImportCandidateController controller = new AdminImportCandidateController(reviewService);

        // Act
        AdminAuditEventSearchResponse response = controller.searchAdminAuditEvents(
                eventId.toString(),
                "IMPORT_CANDIDATE",
                candidateId.toString(),
                "rollback-admin",
                "ROLLBACK_IMPORT_CANDIDATE_STATUS",
                null,
                null,
                null,
                candidateId.toString(),
                null,
                null,
                null,
                rollbackRequestId.toString()).getBody();

        // Assert
        assertThat(reviewService.auditRollbackRequestId).isEqualTo(rollbackRequestId);
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(eventId);
        assertThat(response.getItems().get(0).getBeforeState()).isNull();
        assertThat(response.getItems().get(0).getAfterState()).containsEntry("summary", "Redacted audit payload available to authorized backend processes only");
        assertThat(response.getItems().get(0).getReason()).doesNotContain("secret");
    }

    private static final class FakeReviewService extends AdminImportReviewService {

        private final AdminImportCandidateDetail detail;
        private final ImportCandidateReview savedReview;
        private UUID addedCandidateId;
        private String addedActor;
        private StructuredReviewNote addedNote;
        private UUID mergeCandidateId;
        private String mergeActor;
        private String mergeDecision;
        private UUID mergeDuplicateMatchId;
        private String mergeRationale;
        private ApplyApprovalActionCommand approvalCommand;
        private List<AdminAuditEvent> auditEvents = List.of();
        private UUID auditRollbackRequestId;

        private FakeReviewService(AdminImportCandidateDetail detail, ImportCandidateReview savedReview) {
            super(null);
            this.detail = detail;
            this.savedReview = savedReview;
        }

        @Override
        public List<AdminAuditEvent> searchAuditEvents(
                UUID eventId,
                String entityType,
                UUID entityId,
                String actor,
                String action,
                Instant from,
                Instant to,
                UUID importBatchId,
                UUID candidateId,
                UUID songId,
                UUID arrangementId,
                UUID moderationFlagId,
                UUID rollbackRequestId) {
            auditRollbackRequestId = rollbackRequestId;
            return auditEvents;
        }

        @Override
        public AdminImportCandidateDetail getCandidateDetail(UUID importCandidateId) {
            return detail;
        }

        @Override
        public ImportCandidateReview addStructuredNote(UUID importCandidateId, String actor, StructuredReviewNote note) {
            addedCandidateId = importCandidateId;
            addedActor = actor;
            addedNote = note;
            return savedReview;
        }

        @Override
        public ImportCandidateReview submitMergeDecision(
                UUID importCandidateId,
                String actor,
                String decision,
                UUID duplicateMatchId,
                String rationale) {
            mergeCandidateId = importCandidateId;
            mergeActor = actor;
            mergeDecision = decision;
            mergeDuplicateMatchId = duplicateMatchId;
            mergeRationale = rationale;
            return savedReview;
        }

        @Override
        public ApprovalRecord applyApprovalAction(ApplyApprovalActionCommand command) {
            approvalCommand = command;
            return new ApprovalRecord(
                    UUID.randomUUID(),
                    command.songId(),
                    command.arrangementId(),
                    command.lyricsDocumentId(),
                    command.approvalType(),
                    ApprovalStatus.APPROVED,
                    command.reviewer(),
                    command.reviewNotes(),
                    Instant.EPOCH,
                    Instant.EPOCH);
        }

        @Override
        public List<ModerationFlag> listModerationFlagsForCandidate(UUID importCandidateId) {
            return List.of();
        }

        @Override
        public List<ApprovalRecord> findApprovalRecordsForSong(UUID songId) {
            return List.of();
        }
    }

    private static ImportCandidate candidate(UUID id, ImportCandidateStatus status) {
        return candidate(id, status, null);
    }

    private static ImportCandidate candidate(UUID id, ImportCandidateStatus status, UUID mergedSongId) {
        return new ImportCandidate(
                id,
                UUID.randomUUID(),
                "external-1",
                "Raw Title",
                "raw-title",
                "Artist",
                "{}",
                "12345",
                "sha256:lyrics",
                "{\"rawPayload\":\"must-not-render\"}",
                status,
                mergedSongId,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ImportCandidateReview review(UUID candidateId) {
        return new ImportCandidateReview(
                UUID.randomUUID(),
                candidateId,
                null,
                ImportCandidateReviewDecision.NEEDS_MORE_INFO,
                "reviewer-1",
                "{\"category\":\"PROVENANCE\",\"body\":\"Source reference looks safe.\"}",
                Instant.EPOCH);
    }
}
