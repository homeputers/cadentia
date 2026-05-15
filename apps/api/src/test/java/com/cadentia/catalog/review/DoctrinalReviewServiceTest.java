package com.cadentia.catalog.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
import com.cadentia.catalog.repository.SongRepository;
import java.time.Instant;
import java.util.Collection;
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
class DoctrinalReviewServiceTest {

    @Mock
    private SongRepository songRepository;

    @Captor
    private ArgumentCaptor<CreateApprovalRecordCommand> createApprovalRecordCommandCaptor;

    @Captor
    private ArgumentCaptor<UpdateApprovalRecordCommand> updateApprovalRecordCommandCaptor;

    @Captor
    private ArgumentCaptor<Collection<ApprovalStatus>> statusesCaptor;

    @Test
    void assignSongReviewCreatesDoctrinalApprovalRecordWithHumanReviewerNotes() {
        // Arrange
        UUID songId = UUID.randomUUID();
        ApprovalRecord approvalRecord = approvalRecord(songId, null, ApprovalStatus.PENDING);
        when(songRepository.findApprovalRecord(songId, null, null, ApprovalType.DOCTRINAL))
                .thenReturn(Optional.empty());
        when(songRepository.createApprovalRecord(any(CreateApprovalRecordCommand.class))).thenReturn(approvalRecord);
        DoctrinalReviewService service = new DoctrinalReviewService(songRepository);

        // Act
        ApprovalRecord result = service.assignSongReview(
                songId, ApprovalStatus.PENDING, "pastor@example.test", "Review against local statement of faith.");

        // Assert
        assertThat(result).isEqualTo(approvalRecord);
        verify(songRepository).createApprovalRecord(createApprovalRecordCommandCaptor.capture());
        assertThat(createApprovalRecordCommandCaptor.getValue())
                .extracting(
                        CreateApprovalRecordCommand::songId,
                        CreateApprovalRecordCommand::approvalType,
                        CreateApprovalRecordCommand::status,
                        CreateApprovalRecordCommand::reviewer,
                        CreateApprovalRecordCommand::reviewNotes)
                .containsExactly(
                        songId,
                        ApprovalType.DOCTRINAL,
                        ApprovalStatus.PENDING,
                        "pastor@example.test",
                        "Review against local statement of faith.");
    }

    @Test
    void assignLyricsReviewUpdatesExistingDoctrinalRecordWithoutTouchingOtherApprovalTypes() {
        // Arrange
        UUID lyricsDocumentId = UUID.randomUUID();
        ApprovalRecord existingRecord = approvalRecord(null, lyricsDocumentId, ApprovalStatus.NEEDS_REVIEW);
        ApprovalRecord approvedRecord = approvalRecord(null, lyricsDocumentId, ApprovalStatus.APPROVED);
        when(songRepository.findApprovalRecord(null, null, lyricsDocumentId, ApprovalType.DOCTRINAL))
                .thenReturn(Optional.of(existingRecord));
        when(songRepository.updateApprovalRecord(any(UUID.class), any(UpdateApprovalRecordCommand.class)))
                .thenReturn(Optional.of(approvedRecord));
        DoctrinalReviewService service = new DoctrinalReviewService(songRepository);

        // Act
        ApprovalRecord result = service.assignLyricsReview(
                lyricsDocumentId, ApprovalStatus.APPROVED, "elder@example.test", "Lyrics align doctrinally.");

        // Assert
        assertThat(result).isEqualTo(approvedRecord);
        verify(songRepository).findApprovalRecord(null, null, lyricsDocumentId, ApprovalType.DOCTRINAL);
        verify(songRepository)
                .updateApprovalRecord(eq(existingRecord.id()), updateApprovalRecordCommandCaptor.capture());
        assertThat(updateApprovalRecordCommandCaptor.getValue())
                .extracting(
                        UpdateApprovalRecordCommand::status,
                        UpdateApprovalRecordCommand::reviewer,
                        UpdateApprovalRecordCommand::reviewNotes)
                .containsExactly(ApprovalStatus.APPROVED, "elder@example.test", "Lyrics align doctrinally.");
    }

    @Test
    void queueQueriesAreScopedToDoctrinalPendingAndNeedsReviewStatuses() {
        // Arrange
        ApprovalRecord pendingRecord = approvalRecord(UUID.randomUUID(), null, ApprovalStatus.PENDING);
        ApprovalRecord needsReviewRecord = approvalRecord(UUID.randomUUID(), null, ApprovalStatus.NEEDS_REVIEW);
        when(songRepository.findApprovalRecordsByTypeAndStatuses(
                any(ApprovalType.class), org.mockito.ArgumentMatchers.<Collection<ApprovalStatus>>any()))
                .thenReturn(List.of(pendingRecord), List.of(needsReviewRecord));
        DoctrinalReviewService service = new DoctrinalReviewService(songRepository);

        // Act
        List<ApprovalRecord> pendingQueue = service.findPendingQueue();
        List<ApprovalRecord> needsReviewQueue = service.findNeedsReviewQueue();

        // Assert
        assertThat(pendingQueue).containsExactly(pendingRecord);
        assertThat(needsReviewQueue).containsExactly(needsReviewRecord);
        verify(songRepository, times(2))
                .findApprovalRecordsByTypeAndStatuses(eq(ApprovalType.DOCTRINAL), statusesCaptor.capture());
        assertThat(statusesCaptor.getAllValues())
                .hasSize(2)
                .satisfiesExactly(
                        statuses -> assertThat(statuses).containsExactly(ApprovalStatus.PENDING),
                        statuses -> assertThat(statuses).containsExactly(ApprovalStatus.NEEDS_REVIEW));
    }

    @Test
    void doctrinalReviewRejectsLlmReviewerIdentities() {
        // Arrange
        DoctrinalReviewService service = new DoctrinalReviewService(songRepository);

        // Act / Assert
        assertThatThrownBy(() -> service.assignSongReview(
                        UUID.randomUUID(), ApprovalStatus.APPROVED, "openai-llm", "Automated judgment."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("human doctrinal reviewer");
    }

    private static ApprovalRecord approvalRecord(UUID songId, UUID lyricsDocumentId, ApprovalStatus status) {
        return new ApprovalRecord(
                UUID.randomUUID(),
                songId,
                null,
                lyricsDocumentId,
                ApprovalType.DOCTRINAL,
                status,
                "reviewer@example.test",
                "Review notes",
                Instant.parse("2026-05-15T00:00:00Z"),
                Instant.parse("2026-05-15T00:00:00Z"));
    }
}
