package com.cadentia.catalog.review;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.DoctrinalReviewCommand;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
import com.cadentia.catalog.repository.SongRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DoctrinalReviewService {

    private final SongRepository songRepository;

    public DoctrinalReviewService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    public ApprovalRecord assignSongReview(UUID songId, ApprovalStatus status, String reviewer, String reviewNotes) {
        return saveReview(new DoctrinalReviewCommand(songId, null, status, reviewer, reviewNotes));
    }

    public ApprovalRecord assignLyricsReview(
            UUID lyricsDocumentId, ApprovalStatus status, String reviewer, String reviewNotes) {
        return saveReview(new DoctrinalReviewCommand(null, lyricsDocumentId, status, reviewer, reviewNotes));
    }

    public List<ApprovalRecord> findPendingQueue() {
        return findQueue(List.of(ApprovalStatus.PENDING));
    }

    public List<ApprovalRecord> findNeedsReviewQueue() {
        return findQueue(List.of(ApprovalStatus.NEEDS_REVIEW));
    }

    public boolean isDoctrinallyEligibleForRecommendation(UUID arrangementId) {
        return songRepository.isArrangementDoctrinallyApprovedForRecommendation(arrangementId);
    }

    private ApprovalRecord saveReview(DoctrinalReviewCommand command) {
        return songRepository.findApprovalRecord(
                        command.songId(), null, command.lyricsDocumentId(), ApprovalType.DOCTRINAL)
                .map(existingRecord -> songRepository.updateApprovalRecord(
                        existingRecord.id(),
                        new UpdateApprovalRecordCommand(command.status(), command.reviewer(), command.reviewNotes()))
                        .orElseThrow(() -> new IllegalStateException(
                                "Doctrinal review record disappeared during update: " + existingRecord.id())))
                .orElseGet(() -> songRepository.createApprovalRecord(new CreateApprovalRecordCommand(
                        command.songId(),
                        null,
                        command.lyricsDocumentId(),
                        ApprovalType.DOCTRINAL,
                        command.status(),
                        command.reviewer(),
                        command.reviewNotes())));
    }

    private List<ApprovalRecord> findQueue(Collection<ApprovalStatus> statuses) {
        return songRepository.findApprovalRecordsByTypeAndStatuses(ApprovalType.DOCTRINAL, statuses);
    }
}
