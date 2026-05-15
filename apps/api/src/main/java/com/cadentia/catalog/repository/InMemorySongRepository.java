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

public class InMemorySongRepository implements SongRepository {

    @Override
    public Song createSong(CreateSongRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<Song> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<Song> findByNormalizedTitleAndLanguage(String normalizedTitle, String primaryLanguage) {
        return Optional.empty();
    }

    @Override
    public Optional<Song> updateSong(UUID id, UpdateSongRequest request) {
        return Optional.empty();
    }

    @Override
    public Arrangement createArrangement(CreateArrangementRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<Arrangement> findArrangementById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<Arrangement> findArrangementsBySongId(UUID songId) {
        return List.of();
    }

    @Override
    public Optional<Arrangement> updateArrangement(UUID id, UpdateArrangementRequest request) {
        return Optional.empty();
    }

    @Override
    public LyricsDocument createLyricsDocument(CreateLyricsDocumentRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<LyricsDocument> findLyricsDocumentById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<LyricsDocument> findLyricsDocumentsByArrangementId(UUID arrangementId) {
        return List.of();
    }

    @Override
    public Tag createTag(CreateTagRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<Tag> findTagById(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<Tag> findTagByTypeAndSlug(TagType tagType, String slug) {
        return Optional.empty();
    }

    @Override
    public Optional<Tag> updateTag(UUID id, UpdateTagRequest request) {
        return Optional.empty();
    }

    @Override
    public void addTagToSong(UUID songId, UUID tagId) {
    }

    @Override
    public void addTagToArrangement(UUID arrangementId, UUID tagId) {
    }

    @Override
    public List<Tag> findTagsBySongId(UUID songId) {
        return List.of();
    }

    @Override
    public List<Tag> findTagsByArrangementId(UUID arrangementId) {
        return List.of();
    }

    @Override
    public ImportBatch createImportBatch(CreateImportBatchRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<ImportBatch> findImportBatchById(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<ImportBatch> updateImportBatch(UUID id, UpdateImportBatchRequest request) {
        return Optional.empty();
    }

    @Override
    public ProvenanceRecord createProvenanceRecord(CreateProvenanceRecordRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<ProvenanceRecord> findProvenanceRecordById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<ProvenanceRecord> findProvenanceRecordsForSong(UUID songId) {
        return List.of();
    }

    @Override
    public ApprovalRecord createApprovalRecord(CreateApprovalRecordRequest request) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<ApprovalRecord> findApprovalRecordById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<ApprovalRecord> findApprovalRecordsForSong(UUID songId) {
        return List.of();
    }

    @Override
    public Optional<ApprovalRecord> updateApprovalRecord(UUID id, UpdateApprovalRecordRequest request) {
        return Optional.empty();
    }
}
