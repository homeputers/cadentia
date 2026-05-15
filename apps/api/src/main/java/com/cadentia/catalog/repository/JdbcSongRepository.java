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
import com.cadentia.catalog.model.ArrangementSourceType;
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
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportCandidateReviewDecision;
import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.model.UpdateApprovalRecordCommand;
import com.cadentia.catalog.model.UpdateArrangementCommand;
import com.cadentia.catalog.model.UpdateImportBatchCommand;
import com.cadentia.catalog.model.UpdateLyricsDocumentCommand;
import com.cadentia.catalog.model.UpdateSongCommand;
import com.cadentia.catalog.model.UpdateTagCommand;
import com.cadentia.scraperadmin.CatalogSongCandidate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSongRepository implements SongRepository {

    private static final String SONG_COLUMNS = "id, canonical_title, normalized_title, primary_language, "
            + "original_artist_display, composer_credits, ccli_number, year_written, song_status, "
            + "doctrinal_notes, created_at, updated_at";
    private static final String ARRANGEMENT_COLUMNS = "id, song_id, name, normalized_name, source_type, language, "
            + "musical_key, key_mode, tempo_bpm, time_signature, duration_seconds, energy_level, difficulty_level, "
            + "default_for_song, is_active, created_at, updated_at";
    private static final String LYRICS_COLUMNS = "id, arrangement_id, format, content, content_hash, version_number, "
            + "is_current, contains_chords, contains_sections, source_reference, created_by, created_at";
    private static final String TAG_COLUMNS = "id, tag_type, name, slug, description, is_active, created_at, updated_at";
    private static final String TAG_COLUMNS_QUALIFIED = "tags.id AS id, tags.tag_type AS tag_type, "
            + "tags.name AS name, tags.slug AS slug, tags.description AS description, tags.is_active AS is_active, "
            + "tags.created_at AS created_at, tags.updated_at AS updated_at";
    private static final String IMPORT_COLUMNS = "id, source_system, initiated_by, status, summary_json::text AS summary_json, "
            + "started_at, completed_at";
    private static final String PROVENANCE_COLUMNS = "id, song_id, arrangement_id, lyrics_document_id, import_batch_id, "
            + "source_system, source_uri, source_label, license_type, license_notes, import_method, confidence_score, captured_at";
    private static final String APPROVAL_COLUMNS = "id, song_id, arrangement_id, lyrics_document_id, approval_type, status, "
            + "reviewer, review_notes, reviewed_at, created_at";

    private static final String IMPORT_CANDIDATE_COLUMNS = "id, import_batch_id, external_candidate_id, raw_title, "
            + "normalized_title, source_artist_name, source_artist_metadata::text AS source_artist_metadata_json, "
            + "ccli_number, lyrics_hash, source_payload::text AS source_payload_json, status, merged_song_id, "
            + "created_at, updated_at";
    private static final String PROPOSED_DUPLICATE_MATCH_COLUMNS = "id, import_candidate_id, candidate_song_id, "
            + "match_score, match_signals::text AS match_signals_json, status, suggested_by, created_at, updated_at";
    private static final String IMPORT_CANDIDATE_REVIEW_COLUMNS = "id, import_candidate_id, "
            + "proposed_duplicate_match_id, decision, reviewer, review_notes, reviewed_at";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSongRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Song createSong(CreateSongCommand command) {
        String sql = """
                INSERT INTO songs (
                    canonical_title, normalized_title, primary_language, original_artist_display,
                    composer_credits, ccli_number, year_written, song_status, doctrinal_notes
                ) VALUES (
                    :canonicalTitle, :normalizedTitle, :primaryLanguage, :originalArtistDisplay,
                    :composerCredits, :ccliNumber, :yearWritten, :songStatus, :doctrinalNotes
                )
                RETURNING %s
                """.formatted(SONG_COLUMNS);
        return jdbcTemplate.queryForObject(sql, songParams(command), songMapper());
    }

    @Override
    public Optional<Song> findById(UUID id) {
        return queryOptional("SELECT " + SONG_COLUMNS + " FROM songs WHERE id = :id", Map.of("id", id), songMapper());
    }

    @Override
    public Optional<Song> findByNormalizedTitleAndLanguage(String normalizedTitle, String primaryLanguage) {
        return queryOptional(
                "SELECT " + SONG_COLUMNS
                        + " FROM songs WHERE normalized_title = :normalizedTitle AND primary_language = :primaryLanguage",
                Map.of("normalizedTitle", normalizedTitle, "primaryLanguage", primaryLanguage),
                songMapper());
    }

    @Override
    public Optional<Song> updateSong(UUID id, UpdateSongCommand command) {
        String sql = """
                UPDATE songs
                SET canonical_title = :canonicalTitle,
                    normalized_title = :normalizedTitle,
                    primary_language = :primaryLanguage,
                    original_artist_display = :originalArtistDisplay,
                    composer_credits = :composerCredits,
                    ccli_number = :ccliNumber,
                    year_written = :yearWritten,
                    song_status = :songStatus,
                    doctrinal_notes = :doctrinalNotes,
                    updated_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(SONG_COLUMNS);
        MapSqlParameterSource params = songParams(command).addValue("id", id);
        return queryOptional(sql, params, songMapper());
    }

    @Override
    public Arrangement createArrangement(CreateArrangementCommand command) {
        String sql = """
                INSERT INTO arrangements (
                    song_id, name, normalized_name, source_type, language, musical_key, key_mode, tempo_bpm,
                    time_signature, duration_seconds, energy_level, difficulty_level, default_for_song, is_active
                ) VALUES (
                    :songId, :name, :normalizedName, :sourceType, :language, :musicalKey, :keyMode, :tempoBpm,
                    :timeSignature, :durationSeconds, :energyLevel, :difficultyLevel, :defaultForSong, :active
                )
                RETURNING %s
                """.formatted(ARRANGEMENT_COLUMNS);
        return jdbcTemplate.queryForObject(sql, arrangementParams(command), arrangementMapper());
    }

    @Override
    public Optional<Arrangement> findArrangementById(UUID id) {
        return queryOptional("SELECT " + ARRANGEMENT_COLUMNS + " FROM arrangements WHERE id = :id",
                Map.of("id", id), arrangementMapper());
    }

    @Override
    public List<Arrangement> findArrangementsBySongId(UUID songId) {
        return jdbcTemplate.query("SELECT " + ARRANGEMENT_COLUMNS + " FROM arrangements WHERE song_id = :songId",
                Map.of("songId", songId), arrangementMapper());
    }

    @Override
    public Optional<Arrangement> updateArrangement(UUID id, UpdateArrangementCommand command) {
        String sql = """
                UPDATE arrangements
                SET name = :name,
                    normalized_name = :normalizedName,
                    source_type = :sourceType,
                    language = :language,
                    musical_key = :musicalKey,
                    key_mode = :keyMode,
                    tempo_bpm = :tempoBpm,
                    time_signature = :timeSignature,
                    duration_seconds = :durationSeconds,
                    energy_level = :energyLevel,
                    difficulty_level = :difficultyLevel,
                    default_for_song = :defaultForSong,
                    is_active = :active,
                    updated_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(ARRANGEMENT_COLUMNS);
        return queryOptional(sql, arrangementParams(command).addValue("id", id), arrangementMapper());
    }

    @Override
    @Transactional
    public LyricsDocument createLyricsDocument(CreateLyricsDocumentCommand command) {
        if (command.current()) {
            demoteCurrentLyricsDocument(command.arrangementId());
        }
        String sql = """
                INSERT INTO lyrics_documents (
                    arrangement_id, format, content, content_hash, version_number, is_current,
                    contains_chords, contains_sections, source_reference, created_by
                ) VALUES (
                    :arrangementId, :format, :content, :contentHash, :versionNumber, :current,
                    :containsChords, :containsSections, :sourceReference, :createdBy
                )
                RETURNING %s
                """.formatted(LYRICS_COLUMNS);
        return jdbcTemplate.queryForObject(sql, lyricsParams(command), lyricsMapper());
    }

    @Override
    public Optional<LyricsDocument> findLyricsDocumentById(UUID id) {
        return queryOptional("SELECT " + LYRICS_COLUMNS + " FROM lyrics_documents WHERE id = :id",
                Map.of("id", id), lyricsMapper());
    }

    @Override
    @Transactional
    public Optional<LyricsDocument> updateLyricsDocument(UUID id, UpdateLyricsDocumentCommand command) {
        Optional<UUID> arrangementId = queryOptional(
                "SELECT arrangement_id FROM lyrics_documents WHERE id = :id",
                Map.of("id", id),
                (rs, rowNum) -> uuid(rs, "arrangement_id"));
        if (arrangementId.isEmpty()) {
            return Optional.empty();
        }
        demoteCurrentLyricsDocument(arrangementId.get());
        String sql = """
                INSERT INTO lyrics_documents (
                    arrangement_id, format, content, content_hash, version_number, is_current,
                    contains_chords, contains_sections, source_reference, created_by
                )
                SELECT
                    :arrangementId, :format, :content, :contentHash, COALESCE(MAX(version_number), 0) + 1, true,
                    :containsChords, :containsSections, :sourceReference, :createdBy
                FROM lyrics_documents
                WHERE arrangement_id = :arrangementId
                RETURNING %s
                """.formatted(LYRICS_COLUMNS);
        return Optional.ofNullable(jdbcTemplate.queryForObject(
                sql, lyricsUpdateParams(arrangementId.get(), command), lyricsMapper()));
    }

    @Override
    public List<LyricsDocument> findLyricsDocumentsByArrangementId(UUID arrangementId) {
        return jdbcTemplate.query("SELECT " + LYRICS_COLUMNS
                        + " FROM lyrics_documents WHERE arrangement_id = :arrangementId ORDER BY version_number",
                Map.of("arrangementId", arrangementId), lyricsMapper());
    }

    @Override
    public Tag createTag(CreateTagCommand command) {
        String sql = """
                INSERT INTO tags (tag_type, name, slug, description, is_active)
                VALUES (:tagType, :name, :slug, :description, :active)
                RETURNING %s
                """.formatted(TAG_COLUMNS);
        return jdbcTemplate.queryForObject(sql, tagParams(command), tagMapper());
    }

    @Override
    public Optional<Tag> findTagById(UUID id) {
        return queryOptional("SELECT " + TAG_COLUMNS + " FROM tags WHERE id = :id", Map.of("id", id), tagMapper());
    }

    @Override
    public Optional<Tag> findTagByTypeAndSlug(TagType tagType, String slug) {
        return queryOptional("SELECT " + TAG_COLUMNS + " FROM tags WHERE tag_type = :tagType AND slug = :slug",
                Map.of("tagType", tagType.name(), "slug", slug), tagMapper());
    }

    @Override
    public Optional<Tag> updateTag(UUID id, UpdateTagCommand command) {
        String sql = """
                UPDATE tags
                SET name = :name,
                    slug = :slug,
                    description = :description,
                    is_active = :active,
                    updated_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(TAG_COLUMNS);
        return queryOptional(sql, tagParams(command).addValue("id", id), tagMapper());
    }

    @Override
    public void addTagToSong(UUID songId, UUID tagId) {
        jdbcTemplate.update("INSERT INTO song_tags (song_id, tag_id) VALUES (:songId, :tagId) ON CONFLICT DO NOTHING",
                Map.of("songId", songId, "tagId", tagId));
    }

    @Override
    public void addTagToArrangement(UUID arrangementId, UUID tagId) {
        jdbcTemplate.update("INSERT INTO arrangement_tags (arrangement_id, tag_id) VALUES (:arrangementId, :tagId) "
                        + "ON CONFLICT DO NOTHING",
                Map.of("arrangementId", arrangementId, "tagId", tagId));
    }

    @Override
    public List<Tag> findTagsBySongId(UUID songId) {
        return jdbcTemplate.query("SELECT " + TAG_COLUMNS_QUALIFIED
                        + " FROM tags INNER JOIN song_tags ON tags.id = song_tags.tag_id "
                        + "WHERE song_tags.song_id = :songId ORDER BY tags.tag_type, tags.slug",
                Map.of("songId", songId), tagMapper());
    }

    @Override
    public List<Tag> findTagsByArrangementId(UUID arrangementId) {
        return jdbcTemplate.query("SELECT " + TAG_COLUMNS_QUALIFIED
                        + " FROM tags INNER JOIN arrangement_tags ON tags.id = arrangement_tags.tag_id "
                        + "WHERE arrangement_tags.arrangement_id = :arrangementId ORDER BY tags.tag_type, tags.slug",
                Map.of("arrangementId", arrangementId), tagMapper());
    }

    @Override
    public ImportBatch createImportBatch(CreateImportBatchCommand command) {
        String sql = """
                INSERT INTO import_batches (source_system, initiated_by, status, summary_json)
                VALUES (:sourceSystem, :initiatedBy, :status, CAST(:summaryJson AS jsonb))
                RETURNING %s
                """.formatted(IMPORT_COLUMNS);
        return jdbcTemplate.queryForObject(sql, importBatchParams(command), importBatchMapper());
    }

    @Override
    public Optional<ImportBatch> findImportBatchById(UUID id) {
        return queryOptional("SELECT " + IMPORT_COLUMNS + " FROM import_batches WHERE id = :id",
                Map.of("id", id), importBatchMapper());
    }

    @Override
    public Optional<ImportBatch> updateImportBatch(UUID id, UpdateImportBatchCommand command) {
        String sql = """
                UPDATE import_batches
                SET status = :status,
                    summary_json = CAST(:summaryJson AS jsonb),
                    completed_at = CASE WHEN :completed THEN now() ELSE completed_at END
                WHERE id = :id
                RETURNING %s
                """.formatted(IMPORT_COLUMNS);
        return queryOptional(sql, importBatchParams(command).addValue("id", id), importBatchMapper());
    }

    @Override
    public ImportCandidate createImportCandidate(CreateImportCandidateCommand command) {
        String sql = """
                INSERT INTO import_candidates (
                    import_batch_id, external_candidate_id, raw_title, normalized_title, source_artist_name,
                    source_artist_metadata, ccli_number, lyrics_hash, source_payload, status
                ) VALUES (
                    :importBatchId, :externalCandidateId, :rawTitle, :normalizedTitle, :sourceArtistName,
                    CAST(:sourceArtistMetadataJson AS jsonb), :ccliNumber, :lyricsHash,
                    CAST(:sourcePayloadJson AS jsonb), :status
                )
                RETURNING %s
                """.formatted(IMPORT_CANDIDATE_COLUMNS);
        return jdbcTemplate.queryForObject(sql, importCandidateParams(command), importCandidateMapper());
    }

    @Override
    public List<ImportCandidate> findImportCandidatesByBatchId(UUID importBatchId) {
        return jdbcTemplate.query("SELECT " + IMPORT_CANDIDATE_COLUMNS
                        + " FROM import_candidates WHERE import_batch_id = :importBatchId ORDER BY created_at, id",
                Map.of("importBatchId", importBatchId), importCandidateMapper());
    }

    @Override
    public Optional<ImportCandidate> findImportCandidateById(UUID id) {
        return queryOptional("SELECT " + IMPORT_CANDIDATE_COLUMNS + " FROM import_candidates WHERE id = :id",
                Map.of("id", id), importCandidateMapper());
    }

    @Override
    public Optional<ImportCandidate> updateImportCandidateStatus(UUID id, ImportCandidateStatus status) {
        String sql = """
                UPDATE import_candidates
                SET status = :status,
                    updated_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(IMPORT_CANDIDATE_COLUMNS);
        return queryOptional(sql, Map.of("id", id, "status", status.name()), importCandidateMapper());
    }

    @Override
    public Optional<ImportCandidate> markImportCandidateMerged(UUID id, UUID mergedSongId) {
        String sql = """
                UPDATE import_candidates
                SET status = :status,
                    merged_song_id = :mergedSongId,
                    updated_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(IMPORT_CANDIDATE_COLUMNS);
        return queryOptional(sql, Map.of(
                "id", id,
                "status", ImportCandidateStatus.MERGED.name(),
                "mergedSongId", mergedSongId), importCandidateMapper());
    }

    @Override
    public ProposedDuplicateMatch createProposedDuplicateMatch(CreateProposedDuplicateMatchCommand command) {
        String sql = """
                INSERT INTO proposed_duplicate_matches (
                    import_candidate_id, candidate_song_id, match_score, match_signals, status, suggested_by
                ) VALUES (
                    :importCandidateId, :candidateSongId, :matchScore, CAST(:matchSignalsJson AS jsonb),
                    :status, :suggestedBy
                )
                RETURNING %s
                """.formatted(PROPOSED_DUPLICATE_MATCH_COLUMNS);
        return jdbcTemplate.queryForObject(sql, proposedDuplicateMatchParams(command), proposedDuplicateMatchMapper());
    }

    @Override
    public List<ProposedDuplicateMatch> findProposedDuplicateMatchesByImportCandidateId(UUID importCandidateId) {
        return jdbcTemplate.query("SELECT " + PROPOSED_DUPLICATE_MATCH_COLUMNS
                        + " FROM proposed_duplicate_matches WHERE import_candidate_id = :importCandidateId "
                        + "ORDER BY match_score DESC, created_at, id",
                Map.of("importCandidateId", importCandidateId), proposedDuplicateMatchMapper());
    }

    @Override
    public Optional<ProposedDuplicateMatch> findProposedDuplicateMatchById(UUID id) {
        return queryOptional("SELECT " + PROPOSED_DUPLICATE_MATCH_COLUMNS
                        + " FROM proposed_duplicate_matches WHERE id = :id",
                Map.of("id", id), proposedDuplicateMatchMapper());
    }

    @Override
    public Optional<ProposedDuplicateMatch> updateProposedDuplicateMatchStatus(UUID id, DuplicateMatchStatus status) {
        String sql = """
                UPDATE proposed_duplicate_matches
                SET status = :status,
                    updated_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(PROPOSED_DUPLICATE_MATCH_COLUMNS);
        return queryOptional(sql, Map.of("id", id, "status", status.name()), proposedDuplicateMatchMapper());
    }

    @Override
    public ImportCandidateReview createImportCandidateReview(CreateImportCandidateReviewCommand command) {
        String sql = """
                INSERT INTO import_candidate_reviews (
                    import_candidate_id, proposed_duplicate_match_id, decision, reviewer, review_notes
                ) VALUES (
                    :importCandidateId, :proposedDuplicateMatchId, :decision, :reviewer, :reviewNotes
                )
                RETURNING %s
                """.formatted(IMPORT_CANDIDATE_REVIEW_COLUMNS);
        return jdbcTemplate.queryForObject(sql, importCandidateReviewParams(command), importCandidateReviewMapper());
    }

    @Override
    public List<ImportCandidateReview> findImportCandidateReviewsByImportCandidateId(UUID importCandidateId) {
        return jdbcTemplate.query("SELECT " + IMPORT_CANDIDATE_REVIEW_COLUMNS
                        + " FROM import_candidate_reviews WHERE import_candidate_id = :importCandidateId "
                        + "ORDER BY reviewed_at DESC, id DESC",
                Map.of("importCandidateId", importCandidateId), importCandidateReviewMapper());
    }

    @Override
    public List<CatalogSongCandidate> findCatalogSongCandidatesForDeduplication() {
        String sql = """
                SELECT DISTINCT ON (songs.id)
                    songs.id,
                    songs.canonical_title,
                    songs.normalized_title,
                    songs.original_artist_display,
                    songs.ccli_number,
                    lyrics_documents.content_hash AS lyrics_hash
                FROM songs
                LEFT JOIN arrangements
                    ON arrangements.song_id = songs.id
                    AND arrangements.is_active = true
                    AND arrangements.default_for_song = true
                LEFT JOIN lyrics_documents
                    ON lyrics_documents.arrangement_id = arrangements.id
                    AND lyrics_documents.is_current = true
                ORDER BY songs.id, lyrics_documents.created_at DESC NULLS LAST
                """;
        return jdbcTemplate.query(sql, Map.of(), catalogSongCandidateMapper());
    }

    @Override
    public ProvenanceRecord createProvenanceRecord(CreateProvenanceRecordCommand command) {
        String sql = """
                INSERT INTO provenance_records (
                    song_id, arrangement_id, lyrics_document_id, import_batch_id, source_system, source_uri,
                    source_label, license_type, license_notes, import_method, confidence_score
                ) VALUES (
                    :songId, :arrangementId, :lyricsDocumentId, :importBatchId, :sourceSystem, :sourceUri,
                    :sourceLabel, :licenseType, :licenseNotes, :importMethod, :confidenceScore
                )
                RETURNING %s
                """.formatted(PROVENANCE_COLUMNS);
        return jdbcTemplate.queryForObject(sql, provenanceParams(command), provenanceMapper());
    }

    @Override
    public Optional<ProvenanceRecord> findProvenanceRecordById(UUID id) {
        return queryOptional("SELECT " + PROVENANCE_COLUMNS + " FROM provenance_records WHERE id = :id",
                Map.of("id", id), provenanceMapper());
    }

    @Override
    public List<ProvenanceRecord> findProvenanceRecordsForSong(UUID songId) {
        return jdbcTemplate.query("SELECT " + PROVENANCE_COLUMNS + " FROM provenance_records WHERE song_id = :songId",
                Map.of("songId", songId), provenanceMapper());
    }

    @Override
    public ApprovalRecord createApprovalRecord(CreateApprovalRecordCommand command) {
        String sql = """
                INSERT INTO approval_records (
                    song_id, arrangement_id, lyrics_document_id, approval_type, status, reviewer, review_notes
                ) VALUES (
                    :songId, :arrangementId, :lyricsDocumentId, :approvalType, :status, :reviewer, :reviewNotes
                )
                RETURNING %s
                """.formatted(APPROVAL_COLUMNS);
        return jdbcTemplate.queryForObject(sql, approvalParams(command), approvalMapper());
    }

    @Override
    public Optional<ApprovalRecord> findApprovalRecordById(UUID id) {
        return queryOptional("SELECT " + APPROVAL_COLUMNS + " FROM approval_records WHERE id = :id",
                Map.of("id", id), approvalMapper());
    }

    @Override
    public List<ApprovalRecord> findApprovalRecordsForSong(UUID songId) {
        return jdbcTemplate.query("SELECT " + APPROVAL_COLUMNS + " FROM approval_records WHERE song_id = :songId",
                Map.of("songId", songId), approvalMapper());
    }

    @Override
    public Optional<ApprovalRecord> updateApprovalRecord(UUID id, UpdateApprovalRecordCommand command) {
        String sql = """
                UPDATE approval_records
                SET status = :status,
                    reviewer = :reviewer,
                    review_notes = :reviewNotes,
                    reviewed_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(APPROVAL_COLUMNS);
        return queryOptional(sql, approvalParams(command).addValue("id", id), approvalMapper());
    }

    private <T> Optional<T> queryOptional(String sql, Map<String, ?> params, RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private <T> Optional<T> queryOptional(String sql, MapSqlParameterSource params, RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static MapSqlParameterSource songParams(CreateSongCommand command) {
        return new MapSqlParameterSource()
                .addValue("canonicalTitle", command.canonicalTitle())
                .addValue("normalizedTitle", command.normalizedTitle())
                .addValue("primaryLanguage", command.primaryLanguage())
                .addValue("originalArtistDisplay", command.originalArtistDisplay())
                .addValue("composerCredits", command.composerCredits())
                .addValue("ccliNumber", command.ccliNumber())
                .addValue("yearWritten", command.yearWritten())
                .addValue("songStatus", command.songStatus().name())
                .addValue("doctrinalNotes", command.doctrinalNotes());
    }

    private static MapSqlParameterSource songParams(UpdateSongCommand command) {
        return new MapSqlParameterSource()
                .addValue("canonicalTitle", command.canonicalTitle())
                .addValue("normalizedTitle", command.normalizedTitle())
                .addValue("primaryLanguage", command.primaryLanguage())
                .addValue("originalArtistDisplay", command.originalArtistDisplay())
                .addValue("composerCredits", command.composerCredits())
                .addValue("ccliNumber", command.ccliNumber())
                .addValue("yearWritten", command.yearWritten())
                .addValue("songStatus", command.songStatus().name())
                .addValue("doctrinalNotes", command.doctrinalNotes());
    }

    private static MapSqlParameterSource arrangementParams(CreateArrangementCommand command) {
        return arrangementParams(command.name(), command.normalizedName(), command.sourceType(), command.language(),
                command.musicalKey(), command.keyMode(), command.tempoBpm(), command.timeSignature(),
                command.durationSeconds(), command.energyLevel(), command.difficultyLevel(), command.defaultForSong(),
                command.active()).addValue("songId", command.songId());
    }

    private static MapSqlParameterSource arrangementParams(UpdateArrangementCommand command) {
        return arrangementParams(command.name(), command.normalizedName(), command.sourceType(), command.language(),
                command.musicalKey(), command.keyMode(), command.tempoBpm(), command.timeSignature(),
                command.durationSeconds(), command.energyLevel(), command.difficultyLevel(), command.defaultForSong(),
                command.active());
    }

    private static MapSqlParameterSource arrangementParams(
            String name,
            String normalizedName,
            ArrangementSourceType sourceType,
            String language,
            String musicalKey,
            KeyMode keyMode,
            Integer tempoBpm,
            String timeSignature,
            Integer durationSeconds,
            Integer energyLevel,
            Integer difficultyLevel,
            boolean defaultForSong,
            boolean active) {
        return new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("normalizedName", normalizedName)
                .addValue("sourceType", sourceType.name())
                .addValue("language", language)
                .addValue("musicalKey", musicalKey)
                .addValue("keyMode", keyMode == null ? null : keyMode.name())
                .addValue("tempoBpm", tempoBpm)
                .addValue("timeSignature", timeSignature)
                .addValue("durationSeconds", durationSeconds)
                .addValue("energyLevel", energyLevel)
                .addValue("difficultyLevel", difficultyLevel)
                .addValue("defaultForSong", defaultForSong)
                .addValue("active", active);
    }

    private void demoteCurrentLyricsDocument(UUID arrangementId) {
        jdbcTemplate.update(
                "UPDATE lyrics_documents SET is_current = false WHERE arrangement_id = :arrangementId AND is_current",
                Map.of("arrangementId", arrangementId));
    }

    private static MapSqlParameterSource lyricsParams(CreateLyricsDocumentCommand command) {
        return new MapSqlParameterSource()
                .addValue("arrangementId", command.arrangementId())
                .addValue("format", command.format().storageValue())
                .addValue("content", command.content())
                .addValue("contentHash", command.contentHash())
                .addValue("versionNumber", command.versionNumber())
                .addValue("current", command.current())
                .addValue("containsChords", command.containsChords())
                .addValue("containsSections", command.containsSections())
                .addValue("sourceReference", command.sourceReference())
                .addValue("createdBy", command.createdBy());
    }

    private static MapSqlParameterSource lyricsUpdateParams(UUID arrangementId, UpdateLyricsDocumentCommand command) {
        return new MapSqlParameterSource()
                .addValue("arrangementId", arrangementId)
                .addValue("format", command.format().storageValue())
                .addValue("content", command.content())
                .addValue("contentHash", command.contentHash())
                .addValue("containsChords", command.containsChords())
                .addValue("containsSections", command.containsSections())
                .addValue("sourceReference", command.sourceReference())
                .addValue("createdBy", command.editedBy());
    }

    private static MapSqlParameterSource tagParams(CreateTagCommand command) {
        return new MapSqlParameterSource()
                .addValue("tagType", command.tagType().name())
                .addValue("name", command.name())
                .addValue("slug", command.slug())
                .addValue("description", command.description())
                .addValue("active", command.active());
    }

    private static MapSqlParameterSource tagParams(UpdateTagCommand command) {
        return new MapSqlParameterSource()
                .addValue("name", command.name())
                .addValue("slug", command.slug())
                .addValue("description", command.description())
                .addValue("active", command.active());
    }

    private static MapSqlParameterSource importBatchParams(CreateImportBatchCommand command) {
        return new MapSqlParameterSource()
                .addValue("sourceSystem", command.sourceSystem())
                .addValue("initiatedBy", command.initiatedBy())
                .addValue("status", command.status().name())
                .addValue("summaryJson", command.summaryJson());
    }

    private static MapSqlParameterSource importBatchParams(UpdateImportBatchCommand command) {
        return new MapSqlParameterSource()
                .addValue("status", command.status().name())
                .addValue("summaryJson", command.summaryJson())
                .addValue("completed", command.completed());
    }

    private static MapSqlParameterSource importCandidateParams(CreateImportCandidateCommand command) {
        return new MapSqlParameterSource()
                .addValue("importBatchId", command.importBatchId())
                .addValue("externalCandidateId", command.externalCandidateId())
                .addValue("rawTitle", command.rawTitle())
                .addValue("normalizedTitle", command.normalizedTitle())
                .addValue("sourceArtistName", command.sourceArtistName())
                .addValue("sourceArtistMetadataJson", command.sourceArtistMetadataJson())
                .addValue("ccliNumber", command.ccliNumber())
                .addValue("lyricsHash", command.lyricsHash())
                .addValue("sourcePayloadJson", command.sourcePayloadJson())
                .addValue("status", command.status().name());
    }

    private static MapSqlParameterSource proposedDuplicateMatchParams(CreateProposedDuplicateMatchCommand command) {
        return new MapSqlParameterSource()
                .addValue("importCandidateId", command.importCandidateId())
                .addValue("candidateSongId", command.candidateSongId())
                .addValue("matchScore", command.matchScore())
                .addValue("matchSignalsJson", command.matchSignalsJson())
                .addValue("status", command.status().name())
                .addValue("suggestedBy", command.suggestedBy());
    }

    private static MapSqlParameterSource importCandidateReviewParams(CreateImportCandidateReviewCommand command) {
        return new MapSqlParameterSource()
                .addValue("importCandidateId", command.importCandidateId())
                .addValue("proposedDuplicateMatchId", command.proposedDuplicateMatchId())
                .addValue("decision", command.decision().name())
                .addValue("reviewer", command.reviewer())
                .addValue("reviewNotes", command.reviewNotes());
    }

    private static MapSqlParameterSource provenanceParams(CreateProvenanceRecordCommand command) {
        return new MapSqlParameterSource()
                .addValue("songId", command.songId())
                .addValue("arrangementId", command.arrangementId())
                .addValue("lyricsDocumentId", command.lyricsDocumentId())
                .addValue("importBatchId", command.importBatchId())
                .addValue("sourceSystem", command.sourceSystem())
                .addValue("sourceUri", command.sourceUri())
                .addValue("sourceLabel", command.sourceLabel())
                .addValue("licenseType", command.licenseType().name())
                .addValue("licenseNotes", command.licenseNotes())
                .addValue("importMethod", command.importMethod().name())
                .addValue("confidenceScore", command.confidenceScore());
    }

    private static MapSqlParameterSource approvalParams(CreateApprovalRecordCommand command) {
        return new MapSqlParameterSource()
                .addValue("songId", command.songId())
                .addValue("arrangementId", command.arrangementId())
                .addValue("lyricsDocumentId", command.lyricsDocumentId())
                .addValue("approvalType", command.approvalType().name())
                .addValue("status", command.status().name())
                .addValue("reviewer", command.reviewer())
                .addValue("reviewNotes", command.reviewNotes());
    }

    private static MapSqlParameterSource approvalParams(UpdateApprovalRecordCommand command) {
        return new MapSqlParameterSource()
                .addValue("status", command.status().name())
                .addValue("reviewer", command.reviewer())
                .addValue("reviewNotes", command.reviewNotes());
    }

    private static RowMapper<Song> songMapper() {
        return (rs, rowNum) -> new Song(
                uuid(rs, "id"),
                rs.getString("canonical_title"),
                rs.getString("normalized_title"),
                rs.getString("primary_language"),
                rs.getString("original_artist_display"),
                rs.getString("composer_credits"),
                rs.getString("ccli_number"),
                integer(rs, "year_written"),
                SongStatus.valueOf(rs.getString("song_status")),
                rs.getString("doctrinal_notes"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static RowMapper<Arrangement> arrangementMapper() {
        return (rs, rowNum) -> new Arrangement(
                uuid(rs, "id"),
                uuid(rs, "song_id"),
                rs.getString("name"),
                rs.getString("normalized_name"),
                ArrangementSourceType.valueOf(rs.getString("source_type")),
                rs.getString("language"),
                rs.getString("musical_key"),
                nullableEnum(rs.getString("key_mode"), KeyMode.class),
                integer(rs, "tempo_bpm"),
                rs.getString("time_signature"),
                integer(rs, "duration_seconds"),
                integer(rs, "energy_level"),
                integer(rs, "difficulty_level"),
                rs.getBoolean("default_for_song"),
                rs.getBoolean("is_active"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static RowMapper<LyricsDocument> lyricsMapper() {
        return (rs, rowNum) -> new LyricsDocument(
                uuid(rs, "id"),
                uuid(rs, "arrangement_id"),
                LyricsFormat.fromDeclaredValue(rs.getString("format")),
                rs.getString("content"),
                rs.getString("content_hash"),
                rs.getInt("version_number"),
                rs.getBoolean("is_current"),
                rs.getBoolean("contains_chords"),
                rs.getBoolean("contains_sections"),
                rs.getString("source_reference"),
                rs.getString("created_by"),
                instant(rs, "created_at"));
    }

    private static RowMapper<Tag> tagMapper() {
        return (rs, rowNum) -> new Tag(
                uuid(rs, "id"),
                TagType.valueOf(rs.getString("tag_type")),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                rs.getBoolean("is_active"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static RowMapper<ImportBatch> importBatchMapper() {
        return (rs, rowNum) -> new ImportBatch(
                uuid(rs, "id"),
                rs.getString("source_system"),
                rs.getString("initiated_by"),
                ImportBatchStatus.valueOf(rs.getString("status")),
                rs.getString("summary_json"),
                instant(rs, "started_at"),
                instant(rs, "completed_at"));
    }

    private static RowMapper<ImportCandidate> importCandidateMapper() {
        return (rs, rowNum) -> new ImportCandidate(
                uuid(rs, "id"),
                uuid(rs, "import_batch_id"),
                rs.getString("external_candidate_id"),
                rs.getString("raw_title"),
                rs.getString("normalized_title"),
                rs.getString("source_artist_name"),
                rs.getString("source_artist_metadata_json"),
                rs.getString("ccli_number"),
                rs.getString("lyrics_hash"),
                rs.getString("source_payload_json"),
                ImportCandidateStatus.valueOf(rs.getString("status")),
                uuid(rs, "merged_song_id"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static RowMapper<ProposedDuplicateMatch> proposedDuplicateMatchMapper() {
        return (rs, rowNum) -> new ProposedDuplicateMatch(
                uuid(rs, "id"),
                uuid(rs, "import_candidate_id"),
                uuid(rs, "candidate_song_id"),
                rs.getBigDecimal("match_score"),
                rs.getString("match_signals_json"),
                DuplicateMatchStatus.valueOf(rs.getString("status")),
                rs.getString("suggested_by"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static RowMapper<ImportCandidateReview> importCandidateReviewMapper() {
        return (rs, rowNum) -> new ImportCandidateReview(
                uuid(rs, "id"),
                uuid(rs, "import_candidate_id"),
                uuid(rs, "proposed_duplicate_match_id"),
                ImportCandidateReviewDecision.valueOf(rs.getString("decision")),
                rs.getString("reviewer"),
                rs.getString("review_notes"),
                instant(rs, "reviewed_at"));
    }

    private static RowMapper<CatalogSongCandidate> catalogSongCandidateMapper() {
        return (rs, rowNum) -> new CatalogSongCandidate(
                uuid(rs, "id"),
                rs.getString("canonical_title"),
                rs.getString("normalized_title"),
                rs.getString("original_artist_display"),
                rs.getString("ccli_number"),
                rs.getString("lyrics_hash"));
    }

    private static RowMapper<ProvenanceRecord> provenanceMapper() {
        return (rs, rowNum) -> new ProvenanceRecord(
                uuid(rs, "id"),
                uuid(rs, "song_id"),
                uuid(rs, "arrangement_id"),
                uuid(rs, "lyrics_document_id"),
                uuid(rs, "import_batch_id"),
                rs.getString("source_system"),
                rs.getString("source_uri"),
                rs.getString("source_label"),
                LicenseType.valueOf(rs.getString("license_type")),
                rs.getString("license_notes"),
                ImportMethod.valueOf(rs.getString("import_method")),
                rs.getBigDecimal("confidence_score"),
                instant(rs, "captured_at"));
    }

    private static RowMapper<ApprovalRecord> approvalMapper() {
        return (rs, rowNum) -> new ApprovalRecord(
                uuid(rs, "id"),
                uuid(rs, "song_id"),
                uuid(rs, "arrangement_id"),
                uuid(rs, "lyrics_document_id"),
                ApprovalType.valueOf(rs.getString("approval_type")),
                ApprovalStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer"),
                rs.getString("review_notes"),
                instant(rs, "reviewed_at"),
                instant(rs, "created_at"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static <T extends Enum<T>> T nullableEnum(String value, Class<T> enumClass) {
        return value == null ? null : Enum.valueOf(enumClass, value);
    }
}
