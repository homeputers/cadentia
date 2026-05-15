package com.cadentia.catalog.model;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportCandidateCommandValidationTest {

    @Test
    void createImportCandidateRequiresBatchAndNormalizedTitle() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateImportCandidateCommand(
                        null,
                        "source-row-1",
                        "Great Is Thy Faithfulness",
                        "great-is-thy-faithfulness",
                        "Fixture Artist",
                        "{}",
                        null,
                        null,
                        "{\"title\":\"Great Is Thy Faithfulness\"}",
                        ImportCandidateStatus.STAGED))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("importBatchId");

        assertThatThrownBy(() -> new CreateImportCandidateCommand(
                        UUID.randomUUID(),
                        "source-row-1",
                        "Great Is Thy Faithfulness",
                        " ",
                        "Fixture Artist",
                        "{}",
                        null,
                        null,
                        "{\"title\":\"Great Is Thy Faithfulness\"}",
                        ImportCandidateStatus.STAGED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("normalizedTitle");
    }

    @Test
    void createProposedDuplicateMatchRequiresScoreInUnitRange() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateProposedDuplicateMatchCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("1.01"),
                        "{\"ccli\":\"exact\"}",
                        DuplicateMatchStatus.PROPOSED,
                        "deterministic-deduper"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matchScore");
    }

    @Test
    void matchReviewDecisionRequiresProposedMatchReference() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> new CreateImportCandidateReviewCommand(
                        UUID.randomUUID(),
                        null,
                        ImportCandidateReviewDecision.CONFIRM_MATCH,
                        "reviewer@example.test",
                        "Same CCLI number."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proposedDuplicateMatchId");
    }

    @Test
    void validCandidateDeduplicationReviewCommandsCanBeConstructed() {
        // Arrange
        UUID importBatchId = UUID.randomUUID();
        UUID importCandidateId = UUID.randomUUID();
        UUID candidateSongId = UUID.randomUUID();
        UUID proposedMatchId = UUID.randomUUID();

        // Act / Assert
        assertThatNoException().isThrownBy(() -> {
            new CreateImportCandidateCommand(
                    importBatchId,
                    "source-row-1",
                    "Great Is Thy Faithfulness",
                    "great-is-thy-faithfulness",
                    "Fixture Artist",
                    "{\"sourceArtistId\":\"artist-1\"}",
                    "18723",
                    "sha256:fixture",
                    "{\"title\":\"Great Is Thy Faithfulness\"}",
                    ImportCandidateStatus.DEDUPLICATION_REVIEW);
            new CreateProposedDuplicateMatchCommand(
                    importCandidateId,
                    candidateSongId,
                    new BigDecimal("0.9500"),
                    "{\"ccli\":\"exact\",\"normalizedTitle\":\"exact\"}",
                    DuplicateMatchStatus.PROPOSED,
                    "deterministic-deduper-v1");
            new CreateImportCandidateReviewCommand(
                    importCandidateId,
                    proposedMatchId,
                    ImportCandidateReviewDecision.CONFIRM_MATCH,
                    "reviewer@example.test",
                    "Confirmed deterministic match signals.");
            new CreateImportCandidateReviewCommand(
                    importCandidateId,
                    null,
                    ImportCandidateReviewDecision.CREATE_NEW_SONG,
                    "reviewer@example.test",
                    "No proposed match accepted.");
        });
    }
}
