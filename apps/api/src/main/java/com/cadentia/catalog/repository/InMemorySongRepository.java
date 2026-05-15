package com.cadentia.catalog.repository;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.CreateApprovalRecordCommand;
import com.cadentia.catalog.model.CreateArrangementCommand;
import com.cadentia.catalog.model.CreateImportBatchCommand;
import com.cadentia.catalog.model.CreateLyricsDocumentCommand;
import com.cadentia.catalog.model.CreateProvenanceRecordCommand;
import com.cadentia.catalog.model.CreateSongCommand;
import com.cadentia.catalog.model.CreateTagCommand;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
import com.cadentia.catalog.model.UpdateArrangementCommand;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.model.UpdateTagCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemorySongRepository implements SongRepository {

    @Override
    public Song createSong(CreateSongCommand command) {
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
    public Optional<Song> updateSong(UUID id, UpdateSongCommand command) {
        return Optional.empty();
    }

    @Override
    public Arrangement createArrangement(CreateArrangementCommand command) {
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
    public Optional<Arrangement> updateArrangement(UUID id, UpdateArrangementCommand command) {
        return Optional.empty();
    }

    @Override
    public LyricsDocument createLyricsDocument(CreateLyricsDocumentCommand command) {
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
    public Tag createTag(CreateTagCommand command) {
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
    public Optional<Tag> updateTag(UUID id, UpdateTagCommand command) {
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
    public ImportBatch createImportBatch(CreateImportBatchCommand command) {
        throw new UnsupportedOperationException("In-memory catalog writes are not supported");
    }

    @Override
    public Optional<ImportBatch> findImportBatchById(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<ImportBatch> updateImportBatch(UUID id, UpdateImportBatchCommand command) {
        return Optional.empty();
    }

    @Override
    public ProvenanceRecord createProvenanceRecord(CreateProvenanceRecordCommand command) {
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
    public ApprovalRecord createApprovalRecord(CreateApprovalRecordCommand command) {
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
    public Optional<ApprovalRecord> updateApprovalRecord(UUID id, UpdateApprovalRecordCommand command) {
        return Optional.empty();
    }
}
