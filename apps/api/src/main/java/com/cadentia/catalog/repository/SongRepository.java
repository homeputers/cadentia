package com.cadentia.catalog.repository;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.CreateApprovalRecordRequest;
import com.cadentia.catalog.model.CreateArrangementRequest;
import com.cadentia.catalog.model.CreateImportBatchRequest;
import com.cadentia.catalog.model.CreateLyricsDocumentRequest;
import com.cadentia.catalog.model.CreateProvenanceRecordRequest;
import com.cadentia.catalog.model.CreateSongRequest;
import com.cadentia.catalog.model.CreateTagRequest;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.model.UpdateApprovalRecordRequest;
import com.cadentia.catalog.model.UpdateArrangementRequest;
import com.cadentia.catalog.model.UpdateImportBatchRequest;
import com.cadentia.catalog.model.UpdateSongRequest;
import com.cadentia.catalog.model.UpdateTagRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SongRepository {

    Song createSong(CreateSongRequest request);

    Optional<Song> findById(UUID id);

    Optional<Song> findByNormalizedTitleAndLanguage(String normalizedTitle, String primaryLanguage);

    Optional<Song> updateSong(UUID id, UpdateSongRequest request);

    Arrangement createArrangement(CreateArrangementRequest request);

    Optional<Arrangement> findArrangementById(UUID id);

    List<Arrangement> findArrangementsBySongId(UUID songId);

    Optional<Arrangement> updateArrangement(UUID id, UpdateArrangementRequest request);

    LyricsDocument createLyricsDocument(CreateLyricsDocumentRequest request);

    Optional<LyricsDocument> findLyricsDocumentById(UUID id);

    List<LyricsDocument> findLyricsDocumentsByArrangementId(UUID arrangementId);

    Tag createTag(CreateTagRequest request);

    Optional<Tag> findTagById(UUID id);

    Optional<Tag> findTagByTypeAndSlug(TagType tagType, String slug);

    Optional<Tag> updateTag(UUID id, UpdateTagRequest request);

    void addTagToSong(UUID songId, UUID tagId);

    void addTagToArrangement(UUID arrangementId, UUID tagId);

    List<Tag> findTagsBySongId(UUID songId);

    List<Tag> findTagsByArrangementId(UUID arrangementId);

    ImportBatch createImportBatch(CreateImportBatchRequest request);

    Optional<ImportBatch> findImportBatchById(UUID id);

    Optional<ImportBatch> updateImportBatch(UUID id, UpdateImportBatchRequest request);

    ProvenanceRecord createProvenanceRecord(CreateProvenanceRecordRequest request);

    Optional<ProvenanceRecord> findProvenanceRecordById(UUID id);

    List<ProvenanceRecord> findProvenanceRecordsForSong(UUID songId);

    ApprovalRecord createApprovalRecord(CreateApprovalRecordRequest request);

    Optional<ApprovalRecord> findApprovalRecordById(UUID id);

    List<ApprovalRecord> findApprovalRecordsForSong(UUID songId);

    Optional<ApprovalRecord> updateApprovalRecord(UUID id, UpdateApprovalRecordRequest request);
}
