package com.cadentia.scraperadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateImportCandidateCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.repository.SongRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StarterCatalogPackageImportServiceTest {

    @Mock
    private SongRepository songRepository;

    @Captor
    private ArgumentCaptor<CreateImportBatchCommand> batchCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateImportCandidateCommand> candidateCommandCaptor;

    @Test
    void importPackageStagesSeededCatalogContentWithPackageMetadataForLocalReview() {
        // Arrange
        UUID batchId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(songRepository.createImportBatch(any(CreateImportBatchCommand.class)))
                .thenReturn(batch(batchId, ImportBatchStatus.RUNNING));
        when(songRepository.findCatalogSongCandidatesForDeduplication()).thenReturn(List.of());
        when(songRepository.createImportCandidate(any(CreateImportCandidateCommand.class)))
                .thenAnswer(invocation -> candidate(candidateId, invocation.getArgument(0, CreateImportCandidateCommand.class)));
        when(songRepository.updateImportCandidateStatus(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW))
                .thenAnswer(invocation -> Optional.of(candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW)));
        when(songRepository.updateImportBatch(any(UUID.class), any(UpdateImportBatchCommand.class)))
                .thenReturn(Optional.of(batch(batchId, ImportBatchStatus.COMPLETED)));
        StarterCatalogPackageImportService service = new StarterCatalogPackageImportService(
                new ImportBatchIngestionService(songRepository, new ObjectMapper()), new ObjectMapper());

        // Act
        ImportBatchIngestionResult result = service.importPackage(new StarterCatalogPackageImportCommand(
                StarterCatalogPackageScope.DENOMINATIONAL,
                "wesleyan-baseline",
                "2026.05",
                "package://denom/wesleyan-baseline/2026.05",
                "Wesleyan",
                "admin@example.test",
                Instant.parse("2026-05-20T12:00:00Z"),
                List.of(new StarterCatalogSeedSong(
                        "song-001",
                        "And Can It Be",
                        "Fixture Hymnal",
                        Map.of("publisher", "Fixture Hymnal Board"),
                        "12345",
                        "lyrics-sha-001",
                        "package://denom/wesleyan-baseline/2026.05#song-001",
                        "PUBLIC_DOMAIN",
                        "Public-domain hymnal evidence",
                        List.of("grace", "assurance"),
                        List.of(new StarterCatalogSeedArrangement(
                                "arr-001",
                                "Hymnal Key",
                                "en",
                                "G",
                                "MAJOR",
                                88,
                                "4/4",
                                210,
                                3,
                                2,
                                List.of("classic-hymn"),
                                "package://denom/wesleyan-baseline/2026.05#arr-001")),
                        Map.of("hymnalNumber", "455")))));

        // Assert
        assertThat(result.acceptedCandidates())
                .singleElement()
                .extracting(ImportCandidate::status)
                .isEqualTo(ImportCandidateStatus.DEDUPLICATION_REVIEW);
        verify(songRepository).createImportBatch(batchCommandCaptor.capture());
        assertThat(batchCommandCaptor.getValue())
                .extracting(CreateImportBatchCommand::sourceSystem, CreateImportBatchCommand::initiatedBy)
                .containsExactly("starter-package:denominational:wesleyan-baseline:2026.05", "admin@example.test");
        verify(songRepository).createImportCandidate(candidateCommandCaptor.capture());
        assertThat(candidateCommandCaptor.getValue())
                .extracting(
                        CreateImportCandidateCommand::importBatchId,
                        CreateImportCandidateCommand::externalCandidateId,
                        CreateImportCandidateCommand::rawTitle,
                        CreateImportCandidateCommand::status)
                .containsExactly(batchId, "song-001", "And Can It Be", ImportCandidateStatus.STAGED);
        assertThat(candidateCommandCaptor.getValue().sourcePayloadJson())
                .contains("\"seedOrigin\":\"STARTER_CATALOG_PACKAGE\"")
                .contains("\"scope\":\"DENOMINATIONAL\"")
                .contains("\"version\":\"2026.05\"")
                .contains("\"requiresLocalApproval\":true")
                .contains("\"recommendableBeforeLocalApproval\":false")
                .contains("\"arrangements\"")
                .contains("classic-hymn")
                .contains("hymnalNumber");
        verify(songRepository, never()).createSong(any(CreateSongCommand.class));
        verify(songRepository, never()).createArrangement(any(CreateArrangementCommand.class));
    }

    @Test
    void refreshingStarterPackageStagesNewReviewCandidatesWithoutOverwritingLocalCatalogRecords() {
        // Arrange
        UUID batchId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(songRepository.createImportBatch(any(CreateImportBatchCommand.class)))
                .thenReturn(batch(batchId, ImportBatchStatus.RUNNING));
        when(songRepository.findCatalogSongCandidatesForDeduplication()).thenReturn(List.of());
        when(songRepository.createImportCandidate(any(CreateImportCandidateCommand.class)))
                .thenAnswer(invocation -> candidate(candidateId, invocation.getArgument(0, CreateImportCandidateCommand.class)));
        when(songRepository.updateImportCandidateStatus(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW))
                .thenReturn(Optional.of(candidate(candidateId, ImportCandidateStatus.DEDUPLICATION_REVIEW)));
        when(songRepository.updateImportBatch(any(UUID.class), any(UpdateImportBatchCommand.class)))
                .thenReturn(Optional.of(batch(batchId, ImportBatchStatus.COMPLETED)));
        StarterCatalogPackageImportService service = new StarterCatalogPackageImportService(
                new ImportBatchIngestionService(songRepository, new ObjectMapper()), new ObjectMapper());

        // Act
        service.importPackage(new StarterCatalogPackageImportCommand(
                StarterCatalogPackageScope.GLOBAL,
                "global-baseline",
                "2026.06",
                "package://global/baseline/2026.06",
                null,
                "admin@example.test",
                Instant.parse("2026-06-01T00:00:00Z"),
                List.of(new StarterCatalogSeedSong(
                        "song-100",
                        "Local Review Candidate",
                        "Fixture Artist",
                        Map.of(),
                        null,
                        null,
                        null,
                        "CCLI",
                        "covered by local CCLI reporting",
                        List.of(),
                        List.of(),
                        Map.of()))));

        // Assert
        verify(songRepository).createImportCandidate(any(CreateImportCandidateCommand.class));
        verify(songRepository, never()).createSong(any(CreateSongCommand.class));
        verify(songRepository, never()).createArrangement(any(CreateArrangementCommand.class));
    }

    private static ImportBatch batch(UUID id, ImportBatchStatus status) {
        return new ImportBatch(id, "starter-package", "admin@example.test", status, "{}", Instant.EPOCH, null);
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
                "song-001",
                "And Can It Be",
                "and-can-it-be",
                "Fixture Hymnal",
                "{}",
                null,
                null,
                "{}",
                status,
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
