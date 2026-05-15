package com.cadentia.catalog.repository;

import com.cadentia.catalog.entity.ApprovalRecord;
import com.cadentia.catalog.entity.Arrangement;
import com.cadentia.catalog.entity.ImportBatch;
import com.cadentia.catalog.entity.LyricsDocument;
import com.cadentia.catalog.entity.ProvenanceRecord;
import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.entity.Tag;
import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.ApprovalType;
import com.cadentia.catalog.model.ArrangementSourceType;
import com.cadentia.catalog.model.CreateApprovalRecordRequest;
import com.cadentia.catalog.model.CreateArrangementRequest;
import com.cadentia.catalog.model.CreateImportBatchRequest;
import com.cadentia.catalog.model.CreateLyricsDocumentRequest;
import com.cadentia.catalog.model.CreateProvenanceRecordRequest;
import com.cadentia.catalog.model.CreateSongRequest;
import com.cadentia.catalog.model.CreateTagRequest;
import com.cadentia.catalog.model.ImportBatchStatus;
import com.cadentia.catalog.model.ImportMethod;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.catalog.model.LyricsFormat;
import com.cadentia.catalog.model.SongStatus;
import com.cadentia.catalog.model.TagType;
import com.cadentia.catalog.model.UpdateApprovalRecordRequest;
import com.cadentia.catalog.model.UpdateArrangementRequest;
import com.cadentia.catalog.model.UpdateImportBatchRequest;
import com.cadentia.catalog.model.UpdateSongRequest;
import com.cadentia.catalog.model.UpdateTagRequest;
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
    private static final String IMPORT_COLUMNS = "id, source_system, initiated_by, status, summary_json::text AS summary_json, "
            + "started_at, completed_at";
    private static final String PROVENANCE_COLUMNS = "id, song_id, arrangement_id, lyrics_document_id, import_batch_id, "
            + "source_system, source_uri, source_label, license_type, license_notes, import_method, confidence_score, captured_at";
    private static final String APPROVAL_COLUMNS = "id, song_id, arrangement_id, lyrics_document_id, approval_type, status, "
            + "reviewer, review_notes, reviewed_at, created_at";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSongRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Song createSong(CreateSongRequest request) {
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
        return jdbcTemplate.queryForObject(sql, songParams(request), songMapper());
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
    public Optional<Song> updateSong(UUID id, UpdateSongRequest request) {
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
        MapSqlParameterSource params = songParams(request).addValue("id", id);
        return queryOptional(sql, params, songMapper());
    }

    @Override
    public Arrangement createArrangement(CreateArrangementRequest request) {
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
        return jdbcTemplate.queryForObject(sql, arrangementParams(request), arrangementMapper());
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
    public Optional<Arrangement> updateArrangement(UUID id, UpdateArrangementRequest request) {
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
        return queryOptional(sql, arrangementParams(request).addValue("id", id), arrangementMapper());
    }

    @Override
    public LyricsDocument createLyricsDocument(CreateLyricsDocumentRequest request) {
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
        return jdbcTemplate.queryForObject(sql, lyricsParams(request), lyricsMapper());
    }

    @Override
    public Optional<LyricsDocument> findLyricsDocumentById(UUID id) {
        return queryOptional("SELECT " + LYRICS_COLUMNS + " FROM lyrics_documents WHERE id = :id",
                Map.of("id", id), lyricsMapper());
    }

    @Override
    public List<LyricsDocument> findLyricsDocumentsByArrangementId(UUID arrangementId) {
        return jdbcTemplate.query("SELECT " + LYRICS_COLUMNS
                        + " FROM lyrics_documents WHERE arrangement_id = :arrangementId ORDER BY version_number",
                Map.of("arrangementId", arrangementId), lyricsMapper());
    }

    @Override
    public Tag createTag(CreateTagRequest request) {
        String sql = """
                INSERT INTO tags (tag_type, name, slug, description, is_active)
                VALUES (:tagType, :name, :slug, :description, :active)
                RETURNING %s
                """.formatted(TAG_COLUMNS);
        return jdbcTemplate.queryForObject(sql, tagParams(request), tagMapper());
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
    public Optional<Tag> updateTag(UUID id, UpdateTagRequest request) {
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
        return queryOptional(sql, tagParams(request).addValue("id", id), tagMapper());
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
        return jdbcTemplate.query("SELECT " + TAG_COLUMNS + " FROM tags INNER JOIN song_tags ON tags.id = song_tags.tag_id "
                        + "WHERE song_tags.song_id = :songId ORDER BY tags.tag_type, tags.slug",
                Map.of("songId", songId), tagMapper());
    }

    @Override
    public List<Tag> findTagsByArrangementId(UUID arrangementId) {
        return jdbcTemplate.query("SELECT " + TAG_COLUMNS
                        + " FROM tags INNER JOIN arrangement_tags ON tags.id = arrangement_tags.tag_id "
                        + "WHERE arrangement_tags.arrangement_id = :arrangementId ORDER BY tags.tag_type, tags.slug",
                Map.of("arrangementId", arrangementId), tagMapper());
    }

    @Override
    public ImportBatch createImportBatch(CreateImportBatchRequest request) {
        String sql = """
                INSERT INTO import_batches (source_system, initiated_by, status, summary_json)
                VALUES (:sourceSystem, :initiatedBy, :status, CAST(:summaryJson AS jsonb))
                RETURNING %s
                """.formatted(IMPORT_COLUMNS);
        return jdbcTemplate.queryForObject(sql, importBatchParams(request), importBatchMapper());
    }

    @Override
    public Optional<ImportBatch> findImportBatchById(UUID id) {
        return queryOptional("SELECT " + IMPORT_COLUMNS + " FROM import_batches WHERE id = :id",
                Map.of("id", id), importBatchMapper());
    }

    @Override
    public Optional<ImportBatch> updateImportBatch(UUID id, UpdateImportBatchRequest request) {
        String sql = """
                UPDATE import_batches
                SET status = :status,
                    summary_json = CAST(:summaryJson AS jsonb),
                    completed_at = CASE WHEN :completed THEN now() ELSE completed_at END
                WHERE id = :id
                RETURNING %s
                """.formatted(IMPORT_COLUMNS);
        return queryOptional(sql, importBatchParams(request).addValue("id", id), importBatchMapper());
    }

    @Override
    public ProvenanceRecord createProvenanceRecord(CreateProvenanceRecordRequest request) {
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
        return jdbcTemplate.queryForObject(sql, provenanceParams(request), provenanceMapper());
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
    public ApprovalRecord createApprovalRecord(CreateApprovalRecordRequest request) {
        String sql = """
                INSERT INTO approval_records (
                    song_id, arrangement_id, lyrics_document_id, approval_type, status, reviewer, review_notes
                ) VALUES (
                    :songId, :arrangementId, :lyricsDocumentId, :approvalType, :status, :reviewer, :reviewNotes
                )
                RETURNING %s
                """.formatted(APPROVAL_COLUMNS);
        return jdbcTemplate.queryForObject(sql, approvalParams(request), approvalMapper());
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
    public Optional<ApprovalRecord> updateApprovalRecord(UUID id, UpdateApprovalRecordRequest request) {
        String sql = """
                UPDATE approval_records
                SET status = :status,
                    reviewer = :reviewer,
                    review_notes = :reviewNotes,
                    reviewed_at = now()
                WHERE id = :id
                RETURNING %s
                """.formatted(APPROVAL_COLUMNS);
        return queryOptional(sql, approvalParams(request).addValue("id", id), approvalMapper());
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

    private static MapSqlParameterSource songParams(CreateSongRequest request) {
        return new MapSqlParameterSource()
                .addValue("canonicalTitle", request.canonicalTitle())
                .addValue("normalizedTitle", request.normalizedTitle())
                .addValue("primaryLanguage", request.primaryLanguage())
                .addValue("originalArtistDisplay", request.originalArtistDisplay())
                .addValue("composerCredits", request.composerCredits())
                .addValue("ccliNumber", request.ccliNumber())
                .addValue("yearWritten", request.yearWritten())
                .addValue("songStatus", request.songStatus().name())
                .addValue("doctrinalNotes", request.doctrinalNotes());
    }

    private static MapSqlParameterSource songParams(UpdateSongRequest request) {
        return new MapSqlParameterSource()
                .addValue("canonicalTitle", request.canonicalTitle())
                .addValue("normalizedTitle", request.normalizedTitle())
                .addValue("primaryLanguage", request.primaryLanguage())
                .addValue("originalArtistDisplay", request.originalArtistDisplay())
                .addValue("composerCredits", request.composerCredits())
                .addValue("ccliNumber", request.ccliNumber())
                .addValue("yearWritten", request.yearWritten())
                .addValue("songStatus", request.songStatus().name())
                .addValue("doctrinalNotes", request.doctrinalNotes());
    }

    private static MapSqlParameterSource arrangementParams(CreateArrangementRequest request) {
        return arrangementParams(request.name(), request.normalizedName(), request.sourceType(), request.language(),
                request.musicalKey(), request.keyMode(), request.tempoBpm(), request.timeSignature(),
                request.durationSeconds(), request.energyLevel(), request.difficultyLevel(), request.defaultForSong(),
                request.active()).addValue("songId", request.songId());
    }

    private static MapSqlParameterSource arrangementParams(UpdateArrangementRequest request) {
        return arrangementParams(request.name(), request.normalizedName(), request.sourceType(), request.language(),
                request.musicalKey(), request.keyMode(), request.tempoBpm(), request.timeSignature(),
                request.durationSeconds(), request.energyLevel(), request.difficultyLevel(), request.defaultForSong(),
                request.active());
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

    private static MapSqlParameterSource lyricsParams(CreateLyricsDocumentRequest request) {
        return new MapSqlParameterSource()
                .addValue("arrangementId", request.arrangementId())
                .addValue("format", request.format().name())
                .addValue("content", request.content())
                .addValue("contentHash", request.contentHash())
                .addValue("versionNumber", request.versionNumber())
                .addValue("current", request.current())
                .addValue("containsChords", request.containsChords())
                .addValue("containsSections", request.containsSections())
                .addValue("sourceReference", request.sourceReference())
                .addValue("createdBy", request.createdBy());
    }

    private static MapSqlParameterSource tagParams(CreateTagRequest request) {
        return new MapSqlParameterSource()
                .addValue("tagType", request.tagType().name())
                .addValue("name", request.name())
                .addValue("slug", request.slug())
                .addValue("description", request.description())
                .addValue("active", request.active());
    }

    private static MapSqlParameterSource tagParams(UpdateTagRequest request) {
        return new MapSqlParameterSource()
                .addValue("name", request.name())
                .addValue("slug", request.slug())
                .addValue("description", request.description())
                .addValue("active", request.active());
    }

    private static MapSqlParameterSource importBatchParams(CreateImportBatchRequest request) {
        return new MapSqlParameterSource()
                .addValue("sourceSystem", request.sourceSystem())
                .addValue("initiatedBy", request.initiatedBy())
                .addValue("status", request.status().name())
                .addValue("summaryJson", request.summaryJson());
    }

    private static MapSqlParameterSource importBatchParams(UpdateImportBatchRequest request) {
        return new MapSqlParameterSource()
                .addValue("status", request.status().name())
                .addValue("summaryJson", request.summaryJson())
                .addValue("completed", request.completed());
    }

    private static MapSqlParameterSource provenanceParams(CreateProvenanceRecordRequest request) {
        return new MapSqlParameterSource()
                .addValue("songId", request.songId())
                .addValue("arrangementId", request.arrangementId())
                .addValue("lyricsDocumentId", request.lyricsDocumentId())
                .addValue("importBatchId", request.importBatchId())
                .addValue("sourceSystem", request.sourceSystem())
                .addValue("sourceUri", request.sourceUri())
                .addValue("sourceLabel", request.sourceLabel())
                .addValue("licenseType", request.licenseType().name())
                .addValue("licenseNotes", request.licenseNotes())
                .addValue("importMethod", request.importMethod().name())
                .addValue("confidenceScore", request.confidenceScore());
    }

    private static MapSqlParameterSource approvalParams(CreateApprovalRecordRequest request) {
        return new MapSqlParameterSource()
                .addValue("songId", request.songId())
                .addValue("arrangementId", request.arrangementId())
                .addValue("lyricsDocumentId", request.lyricsDocumentId())
                .addValue("approvalType", request.approvalType().name())
                .addValue("status", request.status().name())
                .addValue("reviewer", request.reviewer())
                .addValue("reviewNotes", request.reviewNotes());
    }

    private static MapSqlParameterSource approvalParams(UpdateApprovalRecordRequest request) {
        return new MapSqlParameterSource()
                .addValue("status", request.status().name())
                .addValue("reviewer", request.reviewer())
                .addValue("reviewNotes", request.reviewNotes());
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
                LyricsFormat.valueOf(rs.getString("format")),
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
