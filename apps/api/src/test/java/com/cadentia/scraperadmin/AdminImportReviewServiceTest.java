package com.cadentia.scraperadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateImportCandidateReviewCommand;
import com.cadentia.catalog.model.CreateProvenanceRecordCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.DuplicateMatchStatus;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.SongStatus;
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
class AdminImportReviewServiceTest {

    @Mock
    private SongRepository songRepository;

    @Captor
    private ArgumentCaptor<CreateImportCandidateReviewCommand> reviewCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateProvenanceRecordCommand> provenanceCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateSongCommand> songCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateArrangementCommand> arrangementCommandCaptor;

    @Captor
    private ArgumentCaptor<CreateApprovalRecordCommand> approvalCommandCaptor;

    @Test
    void recordReviewConfirmsMatchAndMarksCandidateReadyToMerge() {
        // Arrange
        ImportCandidate candidate = candidate(UUID.randomUUID(), ImportCandidateStatus.DEDUPLICATION_REVIEW, null);
        ProposedDuplicateMatch match = proposedMatch(UUID.randomUUID(), candidate.id(), UUID.randomUUID());
        ImportCandidateReview review = review(candidate.id(), match.id(), ImportCandidateReviewDecision.CONFIRM_MATCH);
        when(songRepository.findImportCandidateById(candidate.id())).thenReturn(Optional.of(candidate));
        when(songRepository.findProposedDuplicateMatchById(match.id())).thenReturn(Optional.of(match));
        when(songRepository.createImportCandidateReview(any(CreateImportCandidateReviewCommand.class))).thenReturn(review);
        AdminImportReviewService service = new AdminImportReviewService(songRepository);

        // Act
        ImportCandidateReview result = service.recordReview(new CreateImportCandidateReviewCommand(
                candidate.id(),
                match.id(),
                ImportCandidateReviewDecision.CONFIRM_MATCH,
                "reviewer@example.test",
                "CCLI and title match canonical record"));

        // Assert
        assertThat(result).isEqualTo(review);
        verify(songRepository).createImportCandidateReview(reviewCommandCaptor.capture());
        assertThat(reviewCommandCaptor.getValue())
                .extracting(
                        CreateImportCandidateReviewCommand::importCandidateId,
                        CreateImportCandidateReviewCommand::proposedDuplicateMatchId,
                        CreateImportCandidateReviewCommand::decision,
                        CreateImportCandidateReviewCommand::reviewer)
                .containsExactly(candidate.id(), match.id(), ImportCandidateReviewDecision.CONFIRM_MATCH,
                        "reviewer@example.test");
        verify(songRepository).updateProposedDuplicateMatchStatus(match.id(), DuplicateMatchStatus.REVIEWED);
        verify(songRepository).updateImportCandidateStatus(candidate.id(), ImportCandidateStatus.READY_TO_MERGE);
    }

    @Test
    void recordReviewRejectsCandidateWithoutCreatingCanonicalRecords() {
        // Arrange
        ImportCandidate candidate = candidate(UUID.randomUUID(), ImportCandidateStatus.DEDUPLICATION_REVIEW, null);
        ImportCandidateReview review = review(candidate.id(), null, ImportCandidateReviewDecision.REJECT_CANDIDATE);
        when(songRepository.findImportCandidateById(candidate.id())).thenReturn(Optional.of(candidate));
        when(songRepository.createImportCandidateReview(any(CreateImportCandidateReviewCommand.class))).thenReturn(review);
        AdminImportReviewService service = new AdminImportReviewService(songRepository);

        // Act
        ImportCandidateReview result = service.recordReview(new CreateImportCandidateReviewCommand(
                candidate.id(),
                null,
                ImportCandidateReviewDecision.REJECT_CANDIDATE,
                "reviewer@example.test",
                "Outside catalog scope"));

        // Assert
        assertThat(result.decision()).isEqualTo(ImportCandidateReviewDecision.REJECT_CANDIDATE);
        verify(songRepository).updateImportCandidateStatus(candidate.id(), ImportCandidateStatus.REJECTED);
        verify(songRepository, never()).createSong(any(CreateSongCommand.class));
        verify(songRepository, never()).createArrangement(any(CreateArrangementCommand.class));
        verify(songRepository, never()).createProvenanceRecord(any(CreateProvenanceRecordCommand.class));
    }

    @Test
    void mergeIntoExistingSongRequiresConfirmedReviewAndWritesProvenance() {
        // Arrange
        UUID songId = UUID.randomUUID();
        ImportCandidate candidate = candidate(UUID.randomUUID(), ImportCandidateStatus.READY_TO_MERGE, null);
        Song song = song(songId, "great-is-thy-faithfulness", SongStatus.APPROVED);
        ProposedDuplicateMatch match = proposedMatch(UUID.randomUUID(), candidate.id(), songId);
        ImportCandidateReview review = review(candidate.id(), match.id(), ImportCandidateReviewDecision.CONFIRM_MATCH);
        ProvenanceRecord provenanceRecord = provenanceRecord(UUID.randomUUID(), songId, null, candidate.importBatchId());
        when(songRepository.findImportCandidateById(candidate.id())).thenReturn(Optional.of(candidate));
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));
        when(songRepository.findImportCandidateReviewsByImportCandidateId(candidate.id())).thenReturn(List.of(review));
        when(songRepository.findProposedDuplicateMatchById(match.id())).thenReturn(Optional.of(match));
        when(songRepository.createProvenanceRecord(any(CreateProvenanceRecordCommand.class))).thenReturn(provenanceRecord);
        when(songRepository.markImportCandidateMerged(candidate.id(), songId))
                .thenReturn(Optional.of(candidate(candidate.id(), ImportCandidateStatus.MERGED, songId)));
        AdminImportReviewService service = new AdminImportReviewService(songRepository);

        // Act
        AdminMergeResult result = service.mergeIntoExistingSong(new MergeIntoExistingSongCommand(
                candidate.id(),
                songId,
                "reviewer@example.test",
                "fixture-csv",
                "https://example.test/imports/1",
                "Fixture CSV row 1",
                LicenseType.UNKNOWN,
                "reviewed import metadata only",
                ImportMethod.CSV_IMPORT));

        // Assert
        assertThat(result.song()).isEqualTo(song);
        assertThat(result.provenanceRecords()).containsExactly(provenanceRecord);
        assertThat(result.approvalRecords()).isEmpty();
        assertThat(result.idempotentReplay()).isFalse();
        verify(songRepository).createProvenanceRecord(provenanceCommandCaptor.capture());
        assertThat(provenanceCommandCaptor.getValue())
                .extracting(
                        CreateProvenanceRecordCommand::songId,
                        CreateProvenanceRecordCommand::arrangementId,
                        CreateProvenanceRecordCommand::importBatchId,
                        CreateProvenanceRecordCommand::sourceSystem,
                        CreateProvenanceRecordCommand::licenseType,
                        CreateProvenanceRecordCommand::importMethod)
                .containsExactly(songId, null, candidate.importBatchId(), "fixture-csv", LicenseType.UNKNOWN,
                        ImportMethod.CSV_IMPORT);
        assertThat(provenanceCommandCaptor.getValue().sourceLabel()).contains(candidate.id().toString());
        verify(songRepository).markImportCandidateMerged(candidate.id(), songId);
        verify(songRepository, never()).createApprovalRecord(any(CreateApprovalRecordCommand.class));
    }

    @Test
    void createNewCanonicalSongCreatesDraftReviewRecordsWithoutImplicitApproval() {
        // Arrange
        ImportCandidate candidate = candidate(UUID.randomUUID(), ImportCandidateStatus.READY_TO_MERGE, null);
        Song song = song(UUID.randomUUID(), "new-song", SongStatus.IN_REVIEW);
        Arrangement arrangement = arrangement(UUID.randomUUID(), song.id());
        ImportCandidateReview review = review(candidate.id(), null, ImportCandidateReviewDecision.CREATE_NEW_SONG);
        ProvenanceRecord songProvenance = provenanceRecord(UUID.randomUUID(), song.id(), null, candidate.importBatchId());
        ProvenanceRecord arrangementProvenance = provenanceRecord(UUID.randomUUID(), null, arrangement.id(), candidate.importBatchId());
        ApprovalRecord approvalRecord = approvalRecord(UUID.randomUUID(), song.id(), ApprovalStatus.PENDING);
        when(songRepository.findImportCandidateById(candidate.id())).thenReturn(Optional.of(candidate));
        when(songRepository.findImportCandidateReviewsByImportCandidateId(candidate.id())).thenReturn(List.of(review));
        when(songRepository.findByNormalizedTitleAndLanguage("new-song", "en")).thenReturn(Optional.empty());
        when(songRepository.createSong(any(CreateSongCommand.class))).thenReturn(song);
        when(songRepository.findArrangementsBySongId(song.id())).thenReturn(List.of());
        when(songRepository.createArrangement(any(CreateArrangementCommand.class))).thenReturn(arrangement);
        when(songRepository.createProvenanceRecord(any(CreateProvenanceRecordCommand.class)))
                .thenReturn(songProvenance, arrangementProvenance);
        when(songRepository.createApprovalRecord(any(CreateApprovalRecordCommand.class))).thenReturn(approvalRecord);
        when(songRepository.markImportCandidateMerged(candidate.id(), song.id()))
                .thenReturn(Optional.of(candidate(candidate.id(), ImportCandidateStatus.MERGED, song.id())));
        AdminImportReviewService service = new AdminImportReviewService(songRepository);

        // Act
        AdminMergeResult result = service.createNewCanonicalSong(new CreateCanonicalSongFromImportCandidateCommand(
                candidate.id(),
                "reviewer@example.test",
                "New Song",
                "en",
                "Fixture Artist",
                "Fixture Writer",
                null,
                2024,
                "Needs doctrinal review after import.",
                "New Song",
                "fixture-csv",
                null,
                "Fixture CSV row 2",
                LicenseType.UNKNOWN,
                "metadata-only import",
                ImportMethod.CSV_IMPORT));

        // Assert
        assertThat(result.song()).isEqualTo(song);
        assertThat(result.arrangement()).isEqualTo(arrangement);
        assertThat(result.provenanceRecords()).containsExactly(songProvenance, arrangementProvenance);
        assertThat(result.approvalRecords()).containsExactly(approvalRecord);
        verify(songRepository).createSong(songCommandCaptor.capture());
        assertThat(songCommandCaptor.getValue())
                .extracting(CreateSongCommand::normalizedTitle, CreateSongCommand::songStatus)
                .containsExactly("new-song", SongStatus.IN_REVIEW);
        verify(songRepository).createArrangement(arrangementCommandCaptor.capture());
        assertThat(arrangementCommandCaptor.getValue())
                .extracting(CreateArrangementCommand::songId, CreateArrangementCommand::normalizedName,
                        CreateArrangementCommand::defaultForSong)
                .containsExactly(song.id(), "new-song", true);
        verify(songRepository).createApprovalRecord(approvalCommandCaptor.capture());
        assertThat(approvalCommandCaptor.getValue())
                .extracting(CreateApprovalRecordCommand::approvalType, CreateApprovalRecordCommand::status)
                .containsExactly(ApprovalType.EDITORIAL, ApprovalStatus.PENDING);
        verify(songRepository).markImportCandidateMerged(candidate.id(), song.id());
    }

    @Test
    void repeatedMergeRequestReturnsExistingMergedSongWithoutDuplicatingRecords() {
        // Arrange
        UUID songId = UUID.randomUUID();
        ImportCandidate candidate = candidate(UUID.randomUUID(), ImportCandidateStatus.MERGED, songId);
        Song song = song(songId, "already-merged", SongStatus.IN_REVIEW);
        when(songRepository.findImportCandidateById(candidate.id())).thenReturn(Optional.of(candidate));
        when(songRepository.findById(songId)).thenReturn(Optional.of(song));
        AdminImportReviewService service = new AdminImportReviewService(songRepository);

        // Act
        AdminMergeResult result = service.mergeIntoExistingSong(new MergeIntoExistingSongCommand(
                candidate.id(),
                songId,
                "reviewer@example.test",
                "fixture-csv",
                null,
                "Fixture CSV row 3",
                LicenseType.UNKNOWN,
                null,
                ImportMethod.CSV_IMPORT));

        // Assert
        assertThat(result.song()).isEqualTo(song);
        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.provenanceRecords()).isEmpty();
        verify(songRepository, never()).createSong(any(CreateSongCommand.class));
        verify(songRepository, never()).createArrangement(any(CreateArrangementCommand.class));
        verify(songRepository, never()).createProvenanceRecord(any(CreateProvenanceRecordCommand.class));
        verify(songRepository, never()).markImportCandidateMerged(any(UUID.class), any(UUID.class));
    }

    @Test
    void getCandidateDetailIncludesParserEvidenceWarningsDuplicatesAndHistory() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.READY_TO_MERGE, null);
        ProposedDuplicateMatch duplicateMatch = proposedMatch(UUID.randomUUID(), candidateId, UUID.randomUUID());
        ImportCandidateReview review = review(candidateId, null, ImportCandidateReviewDecision.NEEDS_MORE_INFO);
        when(songRepository.findImportCandidateById(candidateId)).thenReturn(Optional.of(candidate));
        when(songRepository.findProposedDuplicateMatchesByImportCandidateId(candidateId)).thenReturn(List.of(duplicateMatch));
        when(songRepository.findImportCandidateReviewsByImportCandidateId(candidateId)).thenReturn(List.of(review));
        AdminImportReviewService service = new AdminImportReviewService(songRepository, new TitleNormalizer(), new ObjectMapper());

        // Act
        AdminImportCandidateDetail detail = service.getCandidateDetail(candidateId);

        // Assert
        assertThat(detail.candidate().id()).isEqualTo(candidateId);
        assertThat(detail.rawSourceReference()).isEqualTo("fixture://sources/1");
        assertThat(detail.parserName()).isEqualTo("fixture-parser");
        assertThat(detail.parserVersion()).isEqualTo("1.2.3");
        assertThat(detail.parserConfidence()).isEqualTo("0.72");
        assertThat(detail.parserWarnings()).containsExactly("low-ccli-confidence", "unresolved-bridge-boundary");
        assertThat(detail.duplicateMatches()).containsExactly(duplicateMatch);
        assertThat(detail.reviewHistory()).containsExactly(review);
    }

    @Test
    void addStructuredNotePersistsNeedsMoreInfoReviewWithoutMutatingCandidate() {
        // Arrange
        UUID candidateId = UUID.randomUUID();
        ImportCandidate candidate = candidate(candidateId, ImportCandidateStatus.FAILED, null);
        ImportCandidateReview saved = review(candidateId, null, ImportCandidateReviewDecision.NEEDS_MORE_INFO);
        when(songRepository.findImportCandidateById(candidateId)).thenReturn(Optional.of(candidate));
        when(songRepository.createImportCandidateReview(any(CreateImportCandidateReviewCommand.class))).thenReturn(saved);
        AdminImportReviewService service = new AdminImportReviewService(songRepository, new TitleNormalizer(), new ObjectMapper());

        // Act
        ImportCandidateReview result = service.addStructuredNote(
                candidateId,
                "reviewer@example.test",
                new StructuredReviewNote("parser-warning", "Parser confidence below threshold", "request-reparse"));

        // Assert
        assertThat(result).isEqualTo(saved);
        verify(songRepository).createImportCandidateReview(reviewCommandCaptor.capture());
        assertThat(reviewCommandCaptor.getValue().decision()).isEqualTo(ImportCandidateReviewDecision.NEEDS_MORE_INFO);
        assertThat(reviewCommandCaptor.getValue().reviewNotes())
                .contains("\"category\":\"parser-warning\"")
                .contains("\"body\":\"Parser confidence below threshold\"");
        verify(songRepository, never()).updateImportCandidateStatus(any(UUID.class), any(ImportCandidateStatus.class));
    }

    private static ImportCandidate candidate(UUID id, ImportCandidateStatus status, UUID mergedSongId) {
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
                "{\"title\":\"Great Is Thy Faithfulness (Live)\","
                        + "\"sourceReference\":\"fixture://sources/1\","
                        + "\"parserEvidence\":{\"parserName\":\"fixture-parser\",\"parserVersion\":\"1.2.3\","
                        + "\"confidence\":0.72,\"warnings\":[\"low-ccli-confidence\"]},"
                        + "\"parserWarnings\":[\"unresolved-bridge-boundary\"]}",
                status,
                mergedSongId,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ProposedDuplicateMatch proposedMatch(UUID id, UUID candidateId, UUID songId) {
        return new ProposedDuplicateMatch(
                id,
                candidateId,
                songId,
                new BigDecimal("0.9500"),
                "{\"ccliNumber\":\"exact\"}",
                DuplicateMatchStatus.PROPOSED,
                DeterministicSongDeduper.RULESET_NAME,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ImportCandidateReview review(
            UUID candidateId,
            UUID proposedDuplicateMatchId,
            ImportCandidateReviewDecision decision) {
        return new ImportCandidateReview(
                UUID.randomUUID(),
                candidateId,
                proposedDuplicateMatchId,
                decision,
                "reviewer@example.test",
                "Reviewed by admin",
                Instant.EPOCH);
    }

    private static Song song(UUID id, String normalizedTitle, SongStatus songStatus) {
        return new Song(
                id,
                "Great Is Thy Faithfulness",
                normalizedTitle,
                "en",
                "Fixture Artist",
                "Fixture Writer",
                "18723",
                1923,
                songStatus,
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static Arrangement arrangement(UUID id, UUID songId) {
        return new Arrangement(
                id,
                songId,
                "New Song",
                "new-song",
                ArrangementSourceType.UNKNOWN,
                "en",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                true,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static ProvenanceRecord provenanceRecord(UUID id, UUID songId, UUID arrangementId, UUID importBatchId) {
        return new ProvenanceRecord(
                id,
                songId,
                arrangementId,
                null,
                importBatchId,
                "fixture-csv",
                null,
                "Fixture CSV row",
                LicenseType.UNKNOWN,
                null,
                ImportMethod.CSV_IMPORT,
                BigDecimal.ONE,
                Instant.EPOCH);
    }

    private static ApprovalRecord approvalRecord(UUID id, UUID songId, ApprovalStatus status) {
        return new ApprovalRecord(
                id,
                songId,
                null,
                null,
                ApprovalType.EDITORIAL,
                status,
                "reviewer@example.test",
                "Created from reviewed import candidate; approval remains pending.",
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
