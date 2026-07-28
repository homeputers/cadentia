package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.generated.model.AdminAuditEventSearchResponse;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminManualSongImportRequest;
import com.cadentia.generated.model.AdminReviewNote;
import com.cadentia.generated.model.AdminSongImportResponse;
import com.cadentia.generated.model.AdminSongLicenseType;
import com.cadentia.generated.model.ApprovalActionRequest;
import com.cadentia.generated.model.CommitMergeRequest;
import com.cadentia.generated.model.CreateAdminReviewNoteRequest;
import com.cadentia.generated.model.MergeDecisionRequest;
import com.cadentia.scraperadmin.AdminAuditEvent;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.AdminMergeResult;
import com.cadentia.scraperadmin.AdminSongImportService;
import com.cadentia.scraperadmin.AdminSongImportService.AdminSongImportResult;
import com.cadentia.scraperadmin.AdminSongImportService.CandidateSummary;
import com.cadentia.scraperadmin.AdminSongImportService.ManualSongImportCommand;
import com.cadentia.scraperadmin.ApplyApprovalActionCommand;
import com.cadentia.scraperadmin.MergeIntoExistingSongCommand;
import com.cadentia.scraperadmin.ModerationFlag;
import com.cadentia.scraperadmin.StructuredReviewNote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        AdminImportCandidateController controller = controller(reviewService);

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
        AdminImportCandidateController controller = controller(reviewService);
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
        AdminImportCandidateController controller = controller(reviewService);
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
        AdminImportCandidateController controller = controller(reviewService);
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
        AdminImportCandidateController controller = controller(reviewService);
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
    void commitMergeRequiresFreshEtagAndPassesTargetFieldSelection() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        UUID targetSongId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.READY_TO_MERGE);
        FakeReviewService reviewService = new FakeReviewService(new AdminImportCandidateDetail(
                candidate,
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                null);
        AdminImportCandidateController controller = controller(reviewService);
        CommitMergeRequest request = new CommitMergeRequest()
                .actor("reviewer-1")
                .action(CommitMergeRequest.ActionEnum.MERGE_EXISTING)
                .targetSongId(targetSongId)
                .selectedFields(List.of(CommitMergeRequest.SelectedFieldsEnum.CANONICAL_TITLE))
                .rationale("Reviewed duplicate and selected title merge.");

        // Act
        AdminImportCandidateDetailResponse response = controller.commitAdminImportCandidateMerge(
                candidateId,
                "\"candidate-" + candidateId + "-v0\"",
                request).getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(reviewService.commitCandidateId).isEqualTo(candidateId);
        assertThat(reviewService.commitActor).isEqualTo("reviewer-1");
        assertThat(reviewService.commitAction).isEqualTo("MERGE_EXISTING");
        assertThat(reviewService.commitTargetSongId).isEqualTo(targetSongId);
        assertThat(reviewService.commitSelectedFields)
                .containsExactly(MergeIntoExistingSongCommand.MergeField.CANONICAL_TITLE);
        assertThat(reviewService.commitRationale).isEqualTo("Reviewed duplicate and selected title merge.");
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
        AdminImportCandidateController controller = controller(reviewService);
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
        AdminImportCandidateController controller = controller(reviewService);

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

    @Test
    void createManualSongImportStagesCandidateForReviewWithoutApprovingCatalog() {
        // Arrange
        FakeSongImportService songImportService = new FakeSongImportService();
        AdminImportCandidateController controller = controller(new FakeReviewService(new AdminImportCandidateDetail(
                candidate(UUID.randomUUID(), ImportCandidateStatus.DEDUPLICATION_REVIEW),
                "fixture://source/1",
                "fixture-parser",
                "1.2.3",
                "0.91",
                List.of(),
                List.of(),
                List.of()),
                null),
                songImportService);
        AdminManualSongImportRequest request = new AdminManualSongImportRequest()
                .actor("catalog-editor")
                .title("Manually Entered Song")
                .author("Writer")
                .key("G")
                .bpm(84)
                .lyrics("Safe fixture lyrics")
                .licenseType(AdminSongLicenseType.CCLI)
                .licenseEvidence("Church CCLI license");

        // Act
        AdminSongImportResponse response = controller.createAdminManualSongImport(request).getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AdminSongImportResponse.StatusEnum.COMPLETED);
        assertThat(response.getAcceptedCount()).isEqualTo(1);
        assertThat(response.getCandidateIds()).hasSize(1);
        assertThat(response.getCandidates()).hasSize(1);
        assertThat(response.getCandidates().get(0).getRawTitle()).isEqualTo("Manually Entered Song");
        assertThat(response.getCandidates().get(0).getSourceArtistName()).isEqualTo("Writer");
        assertThat(response.getValidationErrors()).isEmpty();
        assertThat(songImportService.manualCommand.title()).isEqualTo("Manually Entered Song");
        assertThat(songImportService.manualCommand.author()).isEqualTo("Writer");
        assertThat(songImportService.manualCommand.bpm()).isEqualTo(84);
    }

    @Test
    void listImportCandidatesWithoutBatchIdReturnsRecentPersistedCandidates() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW);
        FakeReviewService reviewService = new FakeReviewService(null, null);
        reviewService.candidates = List.of(candidate);
        reviewService.candidateCount = 232;
        AdminImportCandidateController controller = controller(reviewService);

        // Act
        var response = controller.listAdminImportCandidates(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                25).getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getCandidateId()).isEqualTo(candidateId);
        assertThat(response.getItems().get(0).getRawTitle()).isEqualTo("Raw Title");
        assertThat(response.getTotalItems()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    void listImportCandidatesDefaultQueueExcludesCompletedMergedCandidates() {
        // Arrange
        UUID activeCandidateId = UUID.randomUUID();
        UUID completedCandidateId = UUID.randomUUID();
        UUID completedSongId = UUID.randomUUID();
        ImportCandidate activeCandidate = candidate(activeCandidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW);
        ImportCandidate completedCandidate = candidate(completedCandidateId, ImportCandidateStatus.MERGED, completedSongId);
        FakeReviewService reviewService = new FakeReviewService(null, null);
        reviewService.candidates = List.of(activeCandidate, completedCandidate);
        reviewService.approvalRecords = List.of(
                approval(completedSongId, ApprovalType.EDITORIAL),
                approval(completedSongId, ApprovalType.LICENSING));
        reviewService.provenanceRecords = List.of(provenance(completedSongId));
        AdminImportCandidateController controller = controller(reviewService);

        // Act
        var defaultResponse = controller.listAdminImportCandidates(
                null, null, null, null, null, null, null, null, null, null, null, null, 1, 25).getBody();
        var mergedResponse = controller.listAdminImportCandidates(
                com.cadentia.generated.model.ImportCandidateStatus.MERGED,
                null, null, null, null, null, null, null, null, null, null, null, 1, 25)
                .getBody();

        // Assert
        assertThat(defaultResponse).isNotNull();
        assertThat(defaultResponse.getItems()).extracting(item -> item.getCandidateId())
                .containsExactly(activeCandidateId);
        assertThat(defaultResponse.getTotalItems()).isEqualTo(1);
        assertThat(mergedResponse).isNotNull();
        assertThat(mergedResponse.getItems()).extracting(item -> item.getCandidateId())
                .containsExactly(completedCandidateId);
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
        private UUID commitCandidateId;
        private String commitActor;
        private String commitAction;
        private UUID commitTargetSongId;
        private Set<MergeIntoExistingSongCommand.MergeField> commitSelectedFields = Set.of();
        private String commitRationale;
        private ApplyApprovalActionCommand approvalCommand;
        private List<AdminAuditEvent> auditEvents = List.of();
        private List<ImportCandidate> candidates = List.of();
        private List<ApprovalRecord> approvalRecords = List.of();
        private List<ProvenanceRecord> provenanceRecords = List.of();
        private int candidateCount;
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
        public int countCandidates(com.cadentia.catalog.model.ImportCandidateStatus status) {
            return candidateCount;
        }

        @Override
        public List<ImportCandidate> findCandidates(
                com.cadentia.catalog.model.ImportCandidateStatus status,
                int limit,
                int offset) {
            if (status == null) {
                return candidates;
            }
            return candidates.stream().filter(candidate -> candidate.status() == status).toList();
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
        public AdminMergeResult commitCandidateMerge(
                UUID importCandidateId,
                String actor,
                String action,
                UUID targetSongId,
                Set<MergeIntoExistingSongCommand.MergeField> selectedFields,
                String rationale) {
            commitCandidateId = importCandidateId;
            commitActor = actor;
            commitAction = action;
            commitTargetSongId = targetSongId;
            commitSelectedFields = selectedFields;
            commitRationale = rationale;
            return null;
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
            return approvalRecords;
        }

        @Override
        public List<ProvenanceRecord> findProvenanceRecordsForSong(UUID songId) {
            return provenanceRecords;
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

    private static ApprovalRecord approval(UUID songId, ApprovalType approvalType) {
        return new ApprovalRecord(
                UUID.randomUUID(),
                songId,
                null,
                null,
                approvalType,
                ApprovalStatus.APPROVED,
                "reviewer-1",
                "Approved fixture",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ProvenanceRecord provenance(UUID songId) {
        return new ProvenanceRecord(
                UUID.randomUUID(),
                songId,
                null,
                null,
                UUID.randomUUID(),
                "admin-import",
                "fixture://source",
                "Reviewed import",
                LicenseType.CCLI,
                "Fixture license",
                ImportMethod.MANUAL_ENTRY,
                BigDecimal.ONE,
                Instant.EPOCH);
    }

    private static AdminImportCandidateController controller(FakeReviewService reviewService) {
        return controller(reviewService, new FakeSongImportService());
    }

    private static AdminImportCandidateController controller(
            FakeReviewService reviewService,
            AdminSongImportService songImportService) {
        return new AdminImportCandidateController(reviewService, songImportService);
    }

    private static final class FakeSongImportService extends AdminSongImportService {

        private ManualSongImportCommand manualCommand;

        private FakeSongImportService() {
            super(null, null);
        }

        @Override
        public AdminSongImportResult importManualSong(ManualSongImportCommand command) {
            manualCommand = command;
            UUID candidateId = UUID.randomUUID();
            return new AdminSongImportResult(
                    UUID.randomUUID(),
                    "COMPLETED",
                    ImportMethod.MANUAL_ENTRY,
                    1,
                    List.of(candidateId),
                    List.of(new CandidateSummary(
                            candidateId,
                            command.title(),
                            command.title(),
                            command.author(),
                            ImportCandidateStatus.DEDUPLICATION_REVIEW)),
                    List.of());
        }
    }
}
