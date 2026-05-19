package com.cadentia.scraperadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateImportCandidateCommand;
import com.cadentia.catalog.model.CreateProposedDuplicateMatchCommand;
import com.cadentia.catalog.model.DuplicateMatchStatus;
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.repository.SongRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportBatchIngestionServiceTest {

    @Mock
    private SongRepository songRepository;

    @Captor
    private ArgumentCaptor<CreateImportBatchCommand> createBatchCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateImportCandidateCommand> createCandidateCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateProposedDuplicateMatchCommand> createMatchCommandCaptor;

    @Captor
    private ArgumentCaptor<UpdateImportBatchCommand> updateBatchCommandCaptor;

    @Test
    void ingestCreatesBatchStoresCandidatesAndRecordsDeterministicDuplicateSuggestions() {
        // Arrange
        UUID batchId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID songId = UUID.randomUUID();
        ImportBatch runningBatch = batch(batchId, ImportBatchStatus.RUNNING, "{}");
        ImportBatch completedBatch = batch(batchId, ImportBatchStatus.COMPLETED, "{\"acceptedCandidates\":1}");
        when(songRepository.createImportBatch(any(CreateImportBatchCommand.class))).thenReturn(runningBatch);
        when(songRepository.findCatalogSongCandidatesForDeduplication()).thenReturn(List.of(new CatalogSongCandidate(
                songId,
                "Great Is Thy Faithfulness",
                "great-is-thy-faithfulness",
                "Fixture Artist",
                "18723",
                null)));
        when(songRepository.createImportCandidate(any(CreateImportCandidateCommand.class)))
                .thenAnswer(invocation -> candidate(candidateId, invocation.getArgument(0, CreateImportCandidateCommand.class)));
        when(songRepository.updateImportCandidateStatus(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW))
                .thenAnswer(invocation -> Optional.of(candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW)));
        when(songRepository.createProposedDuplicateMatch(any(CreateProposedDuplicateMatchCommand.class)))
                .thenAnswer(invocation -> proposedMatch(UUID.randomUUID(), invocation.getArgument(0, CreateProposedDuplicateMatchCommand.class)));
        when(songRepository.updateImportBatch(any(UUID.class), any(UpdateImportBatchCommand.class)))
                .thenReturn(Optional.of(completedBatch));
        ImportBatchIngestionService service = new ImportBatchIngestionService(songRepository, new ObjectMapper());

        // Act
        ImportBatchIngestionResult result = service.ingest(new ImportBatchIngestionCommand(
                "fixture-csv",
                "admin@example.test",
                List.of(new ImportCandidateRecord(
                        "row-1",
                        "source-1",
                        "Great Is Thy Faithfulness (Live)",
                        "Fixture Artist",
                        "{\"sourceArtistId\":\"artist-1\"}",
                        "18723",
                        null,
                        "{\"title\":\"Great Is Thy Faithfulness (Live)\"}",
                        "CSV_UPLOAD",
                        "file://imports/setlist.csv#row-1",
                        "2026-05-18T10:15:30Z",
                        "admin@example.test",
                        "CCLI",
                        "Church CCLI license export"))));

        // Assert
        assertThat(result.importBatch()).isEqualTo(completedBatch);
        assertThat(result.acceptedCandidates())
                .hasSize(1)
                .first()
                .extracting(ImportCandidate::status)
                .isEqualTo(ImportCandidateStatus.DEDUPLICATION_REVIEW);
        assertThat(result.proposedMatches()).hasSize(1);
        assertThat(result.validationErrors()).isEmpty();

        verify(songRepository).createImportBatch(createBatchCommandCaptor.capture());
        assertThat(createBatchCommandCaptor.getValue())
                .extracting(
                        CreateImportBatchCommand::sourceSystem,
                        CreateImportBatchCommand::initiatedBy,
                        CreateImportBatchCommand::status)
                .containsExactly("fixture-csv", "admin@example.test", ImportBatchStatus.RUNNING);

        verify(songRepository).createImportCandidate(createCandidateCommandCaptor.capture());
        assertThat(createCandidateCommandCaptor.getValue())
                .extracting(
                        CreateImportCandidateCommand::importBatchId,
                        CreateImportCandidateCommand::normalizedTitle,
                        CreateImportCandidateCommand::status,
                        CreateImportCandidateCommand::sourcePayloadJson)
                .containsExactly(
                        batchId,
                        "great-is-thy-faithfulness",
                        ImportCandidateStatus.STAGED,
                        "{\"title\":\"Great Is Thy Faithfulness (Live)\"}");

        verify(songRepository).createProposedDuplicateMatch(createMatchCommandCaptor.capture());
        assertThat(createMatchCommandCaptor.getValue())
                .extracting(
                        CreateProposedDuplicateMatchCommand::importCandidateId,
                        CreateProposedDuplicateMatchCommand::candidateSongId,
                        CreateProposedDuplicateMatchCommand::status,
                        CreateProposedDuplicateMatchCommand::suggestedBy)
                .containsExactly(candidateId, songId, DuplicateMatchStatus.PROPOSED,
                        DeterministicSongDeduper.RULESET_NAME);
        assertThat(createMatchCommandCaptor.getValue().matchScore()).isGreaterThanOrEqualTo(new BigDecimal("0.4000"));
        assertThat(createMatchCommandCaptor.getValue().matchSignalsJson()).contains("ccliNumber");

        verify(songRepository).updateImportBatch(any(UUID.class), updateBatchCommandCaptor.capture());
        assertThat(updateBatchCommandCaptor.getValue().status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(updateBatchCommandCaptor.getValue().summaryJson())
                .contains("\"totalCandidates\":1")
                .contains("\"acceptedCandidates\":1")
                .contains("\"validationErrors\":0")
                .contains("\"proposedMatches\":1");
    }

    @Test
    void ingestReportsInvalidRowsWithoutPersistingInvalidCandidates() {
        // Arrange
        UUID batchId = UUID.randomUUID();
        ImportBatch runningBatch = batch(batchId, ImportBatchStatus.RUNNING, "{}");
        ImportBatch failedBatch = batch(batchId, ImportBatchStatus.FAILED, "{\"validationErrors\":1}");
        when(songRepository.createImportBatch(any(CreateImportBatchCommand.class))).thenReturn(runningBatch);
        when(songRepository.findCatalogSongCandidatesForDeduplication()).thenReturn(List.of());
        when(songRepository.updateImportBatch(any(UUID.class), any(UpdateImportBatchCommand.class)))
                .thenReturn(Optional.of(failedBatch));
        ImportBatchIngestionService service = new ImportBatchIngestionService(songRepository, new ObjectMapper());

        // Act
        ImportBatchIngestionResult result = service.ingest(new ImportBatchIngestionCommand(
                "fixture-csv",
                "admin@example.test",
                List.of(new ImportCandidateRecord(
                        "row-2",
                        "source-2",
                        " ",
                        "Fixture Artist",
                        "{}",
                        null,
                        null,
                        "{\"title\":\"Missing Raw Title\"}",
                        "CSV_UPLOAD",
                        "file://imports/setlist.csv#row-2",
                        "2026-05-18T10:15:30Z",
                        "admin@example.test",
                        "CCLI",
                        "Church CCLI license export"))));

        // Assert
        assertThat(result.importBatch()).isEqualTo(failedBatch);
        assertThat(result.acceptedCandidates()).isEmpty();
        assertThat(result.proposedMatches()).isEmpty();
        assertThat(result.validationErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.candidateIdentifier()).isEqualTo("row-2");
                    assertThat(error.field()).isEqualTo("rawTitle");
                    assertThat(error.message()).isEqualTo("rawTitle is required");
                });
        verify(songRepository, never()).createImportCandidate(any(CreateImportCandidateCommand.class));
        verify(songRepository, never()).createProposedDuplicateMatch(any(CreateProposedDuplicateMatchCommand.class));
    }

    @Test
    void ingestRejectsCandidateWhenLicenseIsMissing() {
        UUID batchId = UUID.randomUUID();
        ImportBatch runningBatch = batch(batchId, ImportBatchStatus.RUNNING, "{}");
        ImportBatch failedBatch = batch(batchId, ImportBatchStatus.FAILED, "{\"validationErrors\":1}");
        when(songRepository.createImportBatch(any(CreateImportBatchCommand.class))).thenReturn(runningBatch);
        when(songRepository.findCatalogSongCandidatesForDeduplication()).thenReturn(List.of());
        when(songRepository.updateImportBatch(any(UUID.class), any(UpdateImportBatchCommand.class)))
                .thenReturn(Optional.of(failedBatch));
        ImportBatchIngestionService service = new ImportBatchIngestionService(songRepository, new ObjectMapper());

        ImportBatchIngestionResult result = service.ingest(new ImportBatchIngestionCommand(
                "fixture-csv",
                "admin@example.test",
                List.of(new ImportCandidateRecord(
                        "row-3",
                        "source-3",
                        "Build My Life",
                        "Fixture Artist",
                        "{}",
                        null,
                        null,
                        "{\"title\":\"Build My Life\"}",
                        "CSV_UPLOAD",
                        "file://imports/setlist.csv#row-3",
                        "2026-05-18T10:15:30Z",
                        "admin@example.test",
                        null,
                        null))));

        assertThat(result.acceptedCandidates()).isEmpty();
        assertThat(result.validationErrors()).anySatisfy(error -> {
            assertThat(error.field()).isEqualTo("licenseType");
            assertThat(error.message()).isEqualTo("licenseType is required");
        });
        verify(songRepository, never()).createImportCandidate(any(CreateImportCandidateCommand.class));
    }

    @Test
    void ingestRejectsCandidateWhenLicenseIsProhibitedOrSourceIsMissing() {
        UUID batchId = UUID.randomUUID();
        ImportBatch runningBatch = batch(batchId, ImportBatchStatus.RUNNING, "{}");
        ImportBatch failedBatch = batch(batchId, ImportBatchStatus.FAILED, "{\"validationErrors\":1}");
        when(songRepository.createImportBatch(any(CreateImportBatchCommand.class))).thenReturn(runningBatch);
        when(songRepository.findCatalogSongCandidatesForDeduplication()).thenReturn(List.of());
        when(songRepository.updateImportBatch(any(UUID.class), any(UpdateImportBatchCommand.class)))
                .thenReturn(Optional.of(failedBatch));
        ImportBatchIngestionService service = new ImportBatchIngestionService(songRepository, new ObjectMapper());

        ImportBatchIngestionResult result = service.ingest(new ImportBatchIngestionCommand(
                "fixture-csv",
                "admin@example.test",
                List.of(new ImportCandidateRecord(
                        "row-4",
                        "source-4",
                        "House Of The Lord",
                        "Fixture Artist",
                        "{}",
                        null,
                        null,
                        "{\"title\":\"House Of The Lord\"}",
                        "CSV_UPLOAD",
                        null,
                        "2026-05-18T10:15:30Z",
                        "admin@example.test",
                        "PROHIBITED",
                        "Provider terms deny storage"))));

        assertThat(result.acceptedCandidates()).isEmpty();
        assertThat(result.validationErrors()).anySatisfy(error -> assertThat(error.field()).isEqualTo("sourceReference"));
        assertThat(result.validationErrors()).anySatisfy(error -> {
            assertThat(error.field()).isEqualTo("licenseType");
            assertThat(error.message()).isEqualTo("licenseType is prohibited");
        });
        verify(songRepository, never()).createImportCandidate(any(CreateImportCandidateCommand.class));
    }

    private static ImportBatch batch(UUID id, ImportBatchStatus status, String summaryJson) {
        return new ImportBatch(id, "fixture-csv", "admin@example.test", status, summaryJson, Instant.EPOCH, null);
    }

    private static ImportCandidate candidate(UUID id, CreateImportCandidateCommand command) {
        return new ImportCandidate(
                id,
                command.importBatchId(),
                command.externalCandidateId(),
                command.rawTitle(),
                command.normalizedTitle(),
                command.sourceArtistName(),
                command.sourceArtistMetadataJson(),
                command.ccliNumber(),
                command.lyricsHash(),
                command.sourcePayloadJson(),
                command.status(),
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ImportCandidate candidate(UUID id, ImportCandidateStatus status) {
        return new ImportCandidate(
                id,
                UUID.randomUUID(),
                "source-1",
                "Great Is Thy Faithfulness (Live)",
                "great-is-thy-faithfulness",
                "Fixture Artist",
                "{\"sourceArtistId\":\"artist-1\"}",
                "18723",
                null,
                "{\"title\":\"Great Is Thy Faithfulness (Live)\"}",
                status,
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ProposedDuplicateMatch proposedMatch(UUID id, CreateProposedDuplicateMatchCommand command) {
        return new ProposedDuplicateMatch(
                id,
                command.importCandidateId(),
                command.candidateSongId(),
                command.matchScore(),
                command.matchSignalsJson(),
                command.status(),
                command.suggestedBy(),
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
