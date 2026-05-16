package com.cadentia.catalog.repository;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.ImportCandidate;
import com.cadentia.catalog.entity.ImportCandidateReview;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ProposedDuplicateMatch;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateImportCandidateCommand;
import com.cadentia.catalog.model.CreateImportCandidateReviewCommand;
import com.cadentia.catalog.model.CreateLyricsDocumentCommand;
import com.cadentia.catalog.model.CreateProposedDuplicateMatchCommand;
import com.cadentia.catalog.model.CreateProvenanceRecordCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.CreateTagCommand;
import com.cadentia.catalog.model.DuplicateMatchStatus;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
import com.cadentia.catalog.model.UpdateArrangementCommand;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.model.UpdateLyricsDocumentCommand;
import com.cadentia.catalog.model.UpdateLyricsParseResultCommand;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.model.UpdateTagCommand;
import com.cadentia.scraperadmin.CatalogSongCandidate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SongRepository {

    Song createSong(CreateSongCommand command);

    Optional<Song> findById(UUID id);

    Optional<Song> findByNormalizedTitleAndLanguage(String normalizedTitle, String primaryLanguage);

    Optional<Song> updateSong(UUID id, UpdateSongCommand command);

    Arrangement createArrangement(CreateArrangementCommand command);

    Optional<Arrangement> findArrangementById(UUID id);

    List<Arrangement> findArrangementsBySongId(UUID songId);

    Optional<Arrangement> updateArrangement(UUID id, UpdateArrangementCommand command);

    LyricsDocument createLyricsDocument(CreateLyricsDocumentCommand command);

    Optional<LyricsDocument> findLyricsDocumentById(UUID id);

    Optional<LyricsDocument> updateLyricsDocument(UUID id, UpdateLyricsDocumentCommand command);

    Optional<LyricsDocument> updateLyricsParseResult(UUID id, UpdateLyricsParseResultCommand command);

    List<LyricsDocument> findLyricsDocumentsByArrangementId(UUID arrangementId);

    Tag createTag(CreateTagCommand command);

    Optional<Tag> findTagById(UUID id);

    Optional<Tag> findTagByTypeAndSlug(TagType tagType, String slug);

    Optional<Tag> updateTag(UUID id, UpdateTagCommand command);

    boolean addTagToSong(UUID songId, UUID tagId);

    boolean addTagToArrangement(UUID arrangementId, UUID tagId);

    boolean addTagToLyricsDocument(UUID lyricsDocumentId, UUID tagId);

    List<Tag> findTagsBySongId(UUID songId);

    List<Tag> findTagsByArrangementId(UUID arrangementId);

    List<Tag> findTagsByLyricsDocumentId(UUID lyricsDocumentId);

    ImportBatch createImportBatch(CreateImportBatchCommand command);

    Optional<ImportBatch> findImportBatchById(UUID id);

    Optional<ImportBatch> updateImportBatch(UUID id, UpdateImportBatchCommand command);

    ImportCandidate createImportCandidate(CreateImportCandidateCommand command);

    List<ImportCandidate> findImportCandidatesByBatchId(UUID importBatchId);

    Optional<ImportCandidate> findImportCandidateById(UUID id);

    Optional<ImportCandidate> updateImportCandidateStatus(UUID id, ImportCandidateStatus status);

    Optional<ImportCandidate> markImportCandidateMerged(UUID id, UUID mergedSongId);

    ProposedDuplicateMatch createProposedDuplicateMatch(CreateProposedDuplicateMatchCommand command);

    List<ProposedDuplicateMatch> findProposedDuplicateMatchesByImportCandidateId(UUID importCandidateId);

    Optional<ProposedDuplicateMatch> findProposedDuplicateMatchById(UUID id);

    Optional<ProposedDuplicateMatch> updateProposedDuplicateMatchStatus(UUID id, DuplicateMatchStatus status);

    ImportCandidateReview createImportCandidateReview(CreateImportCandidateReviewCommand command);

    List<ImportCandidateReview> findImportCandidateReviewsByImportCandidateId(UUID importCandidateId);

    List<CatalogSongCandidate> findCatalogSongCandidatesForDeduplication();

    ProvenanceRecord createProvenanceRecord(CreateProvenanceRecordCommand command);

    Optional<ProvenanceRecord> findProvenanceRecordById(UUID id);

    List<ProvenanceRecord> findProvenanceRecordsForSong(UUID songId);

    ApprovalRecord createApprovalRecord(CreateApprovalRecordCommand command);

    Optional<ApprovalRecord> findApprovalRecordById(UUID id);

    List<ApprovalRecord> findApprovalRecordsForSong(UUID songId);

    Optional<ApprovalRecord> findApprovalRecord(
            UUID songId, UUID arrangementId, UUID lyricsDocumentId, ApprovalType approvalType);

    List<ApprovalRecord> findApprovalRecordsByTypeAndStatuses(
            ApprovalType approvalType, Collection<ApprovalStatus> statuses);

    boolean isArrangementDoctrinallyApprovedForRecommendation(UUID arrangementId);

    Optional<ApprovalRecord> updateApprovalRecord(UUID id, UpdateApprovalRecordCommand command);
}
