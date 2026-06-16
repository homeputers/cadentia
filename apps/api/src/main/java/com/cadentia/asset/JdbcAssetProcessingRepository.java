package com.cadentia.asset;

import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobStatus;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingJobType;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultRecord;
import com.cadentia.asset.AssetProcessingModels.AssetProcessingResultStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAssetProcessingRepository implements AssetProcessingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAssetProcessingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public AssetProcessingJobRecord enqueue(
            UUID assetVersionId,
            AssetProcessingJobType jobType,
            String processorType,
            String processorVersion,
            String inputChecksum,
            int maxAttempts,
            Instant now) {
        try {
            UUID jobId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO asset_processing_jobs (
                        asset_version_id, job_type_code, processor_type, processor_version,
                        input_checksum, status_code, max_attempts, created_at, available_at
                    ) VALUES (
                        :assetVersionId, :jobTypeCode, :processorType, :processorVersion,
                        :inputChecksum, 'QUEUED', :maxAttempts, :now, :now
                    )
                    RETURNING id
                    """,
                    new MapSqlParameterSource()
                            .addValue("assetVersionId", assetVersionId)
                            .addValue("jobTypeCode", jobType.name())
                            .addValue("processorType", processorType)
                            .addValue("processorVersion", processorVersion)
                            .addValue("inputChecksum", inputChecksum)
                            .addValue("maxAttempts", maxAttempts)
                            .addValue("now", Timestamp.from(now)),
                    UUID.class);
            return findJob(jobId).orElseThrow();
        } catch (DuplicateKeyException ex) {
            return jdbcTemplate.query(
                            """
                            SELECT * FROM asset_processing_jobs
                            WHERE asset_version_id = :assetVersionId
                              AND job_type_code = :jobTypeCode
                              AND processor_type = :processorType
                              AND processor_version = :processorVersion
                              AND input_checksum = :inputChecksum
                            """,
                            new MapSqlParameterSource()
                                    .addValue("assetVersionId", assetVersionId)
                                    .addValue("jobTypeCode", jobType.name())
                                    .addValue("processorType", processorType)
                                    .addValue("processorVersion", processorVersion)
                                    .addValue("inputChecksum", inputChecksum),
                            (rs, rowNum) -> mapJob(rs))
                    .stream()
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Override
    public Optional<AssetProcessingJobRecord> findJob(UUID jobId) {
        return jdbcTemplate.query(
                        "SELECT * FROM asset_processing_jobs WHERE id = :jobId",
                        Map.of("jobId", jobId),
                        (rs, rowNum) -> mapJob(rs))
                .stream()
                .findFirst();
    }

    @Override
    public List<AssetProcessingJobRecord> jobsForVersion(UUID assetVersionId) {
        return jdbcTemplate.query(
                "SELECT * FROM asset_processing_jobs WHERE asset_version_id = :assetVersionId ORDER BY created_at, id",
                Map.of("assetVersionId", assetVersionId),
                (rs, rowNum) -> mapJob(rs));
    }

    @Override
    public List<AssetProcessingJobRecord> runnableJobs(Instant now) {
        return jdbcTemplate.query(
                """
                SELECT * FROM asset_processing_jobs
                WHERE status_code = 'QUEUED' AND available_at <= :now
                ORDER BY created_at, id
                """,
                Map.of("now", Timestamp.from(now)),
                (rs, rowNum) -> mapJob(rs));
    }

    @Override
    public AssetProcessingJobRecord markRunning(UUID jobId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE asset_processing_jobs
                SET status_code = 'RUNNING', attempts = attempts + 1, started_at = :now,
                    completed_at = NULL, error_code = NULL, sanitized_error_detail = NULL
                WHERE id = :jobId
                """,
                Map.of("jobId", jobId, "now", Timestamp.from(now)));
        return findJob(jobId).orElseThrow();
    }

    @Override
    public AssetProcessingJobRecord markSucceeded(UUID jobId, UUID resultId, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE asset_processing_jobs
                SET status_code = 'SUCCEEDED', output_result_id = :resultId, completed_at = :now,
                    error_code = NULL, sanitized_error_detail = NULL
                WHERE id = :jobId
                """,
                new MapSqlParameterSource().addValue("jobId", jobId).addValue("resultId", resultId)
                        .addValue("now", Timestamp.from(now)));
        return findJob(jobId).orElseThrow();
    }

    @Override
    public AssetProcessingJobRecord markFailed(UUID jobId, String errorCode, String sanitizedErrorDetail, Instant retryAt) {
        jdbcTemplate.update(
                """
                UPDATE asset_processing_jobs
                SET status_code = 'QUEUED', available_at = :retryAt,
                    error_code = :errorCode, sanitized_error_detail = :sanitizedErrorDetail
                WHERE id = :jobId
                """,
                new MapSqlParameterSource().addValue("jobId", jobId).addValue("retryAt", Timestamp.from(retryAt))
                        .addValue("errorCode", errorCode).addValue("sanitizedErrorDetail", sanitizedErrorDetail));
        return findJob(jobId).orElseThrow();
    }

    @Override
    public AssetProcessingJobRecord markDeadLettered(UUID jobId, String errorCode, String sanitizedErrorDetail, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE asset_processing_jobs
                SET status_code = 'DEAD_LETTERED', completed_at = :now,
                    error_code = :errorCode, sanitized_error_detail = :sanitizedErrorDetail
                WHERE id = :jobId
                """,
                new MapSqlParameterSource().addValue("jobId", jobId).addValue("now", Timestamp.from(now))
                        .addValue("errorCode", errorCode).addValue("sanitizedErrorDetail", sanitizedErrorDetail));
        return findJob(jobId).orElseThrow();
    }

    @Override
    public AssetProcessingResultRecord saveResult(AssetProcessingResultRecord result) {
        jdbcTemplate.update(
                """
                INSERT INTO asset_processing_results (
                    id, job_id, asset_version_id, result_type_code, processor_type, processor_version,
                    input_checksum, status_code, output_storage_key, output_mime_type, output_byte_size,
                    result_metadata, created_at
                ) VALUES (
                    :id, :jobId, :assetVersionId, :resultTypeCode, :processorType, :processorVersion,
                    :inputChecksum, :statusCode, :outputStorageKey, :outputMimeType, :outputByteSize,
                    '{}'::jsonb, :createdAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", result.id())
                        .addValue("jobId", result.jobId())
                        .addValue("assetVersionId", result.assetVersionId())
                        .addValue("resultTypeCode", result.resultType().name())
                        .addValue("processorType", result.processorType())
                        .addValue("processorVersion", result.processorVersion())
                        .addValue("inputChecksum", result.inputChecksum())
                        .addValue("statusCode", result.status().name())
                        .addValue("outputStorageKey", result.outputStorageKey())
                        .addValue("outputMimeType", result.outputMimeType())
                        .addValue("outputByteSize", result.outputByteSize())
                        .addValue("createdAt", Timestamp.from(result.createdAt())));
        return result;
    }

    @Override
    public List<AssetProcessingResultRecord> resultsForVersion(UUID assetVersionId) {
        return jdbcTemplate.query(
                "SELECT * FROM asset_processing_results WHERE asset_version_id = :assetVersionId ORDER BY created_at, id",
                Map.of("assetVersionId", assetVersionId),
                (rs, rowNum) -> mapResult(rs));
    }

    @Override
    public boolean hasIncompleteRequiredJobs(UUID assetVersionId) {
        Boolean value = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM asset_processing_jobs
                    WHERE asset_version_id = :assetVersionId AND status_code <> 'SUCCEEDED'
                )
                """,
                Map.of("assetVersionId", assetVersionId),
                Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    @Override
    public boolean hasRequiredJobStatus(UUID assetVersionId, AssetProcessingJobStatus status) {
        Boolean value = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM asset_processing_jobs
                    WHERE asset_version_id = :assetVersionId AND status_code = :statusCode
                )
                """,
                new MapSqlParameterSource().addValue("assetVersionId", assetVersionId).addValue("statusCode", status.name()),
                Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private AssetProcessingJobRecord mapJob(ResultSet rs) throws SQLException {
        return new AssetProcessingJobRecord(
                uuid(rs, "id"), uuid(rs, "asset_version_id"), AssetProcessingJobType.valueOf(rs.getString("job_type_code")),
                rs.getString("processor_type"), rs.getString("processor_version"), rs.getString("input_checksum"),
                AssetProcessingJobStatus.valueOf(rs.getString("status_code")), rs.getInt("attempts"), rs.getInt("max_attempts"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("available_at").toInstant(),
                instant(rs, "started_at"), instant(rs, "completed_at"), rs.getString("error_code"),
                rs.getString("sanitized_error_detail"), uuid(rs, "output_result_id"));
    }

    private AssetProcessingResultRecord mapResult(ResultSet rs) throws SQLException {
        return new AssetProcessingResultRecord(
                uuid(rs, "id"), uuid(rs, "job_id"), uuid(rs, "asset_version_id"),
                AssetProcessingJobType.valueOf(rs.getString("result_type_code")), rs.getString("processor_type"),
                rs.getString("processor_version"), rs.getString("input_checksum"),
                AssetProcessingResultStatus.valueOf(rs.getString("status_code")), rs.getString("output_storage_key"),
                rs.getString("output_mime_type"), rs.getObject("output_byte_size", Long.class), Map.of(),
                rs.getTimestamp("created_at").toInstant());
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : (UUID) value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
