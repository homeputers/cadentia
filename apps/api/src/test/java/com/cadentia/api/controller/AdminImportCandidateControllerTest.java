package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.generated.model.AdminImportCandidateDetailResponse;
import com.cadentia.generated.model.AdminReviewNote;
import com.cadentia.generated.model.CreateAdminReviewNoteRequest;
import com.cadentia.scraperadmin.AdminImportCandidateDetail;
import com.cadentia.scraperadmin.AdminImportReviewService;
import com.cadentia.scraperadmin.StructuredReviewNote;
import java.time.Instant;
import java.util.List;
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

    private static final class FakeReviewService extends AdminImportReviewService {

        private final AdminImportCandidateDetail detail;
        private final ImportCandidateReview savedReview;
        private UUID addedCandidateId;
        private String addedActor;
        private StructuredReviewNote addedNote;

        private FakeReviewService(AdminImportCandidateDetail detail, ImportCandidateReview savedReview) {
            super(null);
            this.detail = detail;
            this.savedReview = savedReview;
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
    }

    private static ImportCandidate candidate(UUID id, ImportCandidateStatus status) {
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
                null,
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
