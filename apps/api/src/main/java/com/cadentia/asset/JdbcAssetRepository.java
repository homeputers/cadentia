package com.cadentia.asset;

import com.cadentia.asset.AssetModels.AssetAccessPolicyCode;
import com.cadentia.asset.AssetModels.AssetLifecycleStatusCode;
import com.cadentia.asset.AssetModels.AssetLicenseStatusCode;
import com.cadentia.asset.AssetModels.AssetProcessingStatusCode;
import com.cadentia.asset.AssetModels.AssetRecord;
import com.cadentia.asset.AssetModels.AssetTypeCode;
import com.cadentia.asset.AssetModels.AssetVersionRecord;
import com.cadentia.asset.AssetModels.ControlledVocabularyRecord;
import com.cadentia.asset.AssetModels.CreateAssetCommand;
import com.cadentia.asset.AssetModels.CreateAssetVersionCommand;
import com.cadentia.asset.AssetModels.LicenseMetadata;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAssetRepository implements AssetRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAssetRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public AssetRecord createAsset(CreateAssetCommand command) {
        UUID id = jdbcTemplate.queryForObject(
                """
                INSERT INTO logical_assets (
                    asset_type_code, title, description, owner_actor, owning_ministry,
                    default_access_policy_code, lifecycle_status_code, created_by
                ) VALUES (
                    :assetTypeCode, :title, :description, :ownerActor, :owningMinistry,
                    :defaultAccessPolicyCode, :lifecycleStatusCode, :createdBy
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("assetTypeCode", command.assetTypeCode().name())
                        .addValue("title", command.title())
                        .addValue("description", command.description())
                        .addValue("ownerActor", command.ownerActor())
                        .addValue("owningMinistry", command.owningMinistry())
                        .addValue("defaultAccessPolicyCode", command.defaultAccessPolicyCode().name())
                        .addValue("lifecycleStatusCode", command.lifecycleStatusCode().name())
                        .addValue("createdBy", command.createdBy()),
                UUID.class);
        return findAsset(id).orElseThrow();
    }

    @Override
    @Transactional
    public AssetVersionRecord createVersion(CreateAssetVersionCommand command) {
        UUID versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO asset_versions (
                    asset_id, version_number, revision_code, storage_provider_code, storage_region,
                    storage_bucket_alias, storage_key, checksum_algorithm, checksum_value, mime_type,
                    byte_size, source_uri, provenance_summary, created_by, lifecycle_status_code,
                    processing_status_code, access_policy_code
                ) VALUES (
                    :assetId, :versionNumber, :revisionCode, :storageProviderCode, :storageRegion,
                    :storageBucketAlias, :storageKey, :checksumAlgorithm, :checksumValue, :mimeType,
                    :byteSize, :sourceUri, :provenanceSummary, :createdBy, :lifecycleStatusCode,
                    :processingStatusCode, :accessPolicyCode
                )
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("assetId", command.assetId())
                        .addValue("versionNumber", command.versionNumber())
                        .addValue("revisionCode", command.revisionCode())
                        .addValue("storageProviderCode", command.storageProviderCode())
                        .addValue("storageRegion", command.storageRegion())
                        .addValue("storageBucketAlias", command.storageBucketAlias())
                        .addValue("storageKey", command.storageKey())
                        .addValue("checksumAlgorithm", command.checksumAlgorithm())
                        .addValue("checksumValue", command.checksumValue())
                        .addValue("mimeType", command.mimeType())
                        .addValue("byteSize", command.byteSize())
                        .addValue("sourceUri", command.sourceUri())
                        .addValue("provenanceSummary", command.provenanceSummary())
                        .addValue("createdBy", command.createdBy())
                        .addValue("lifecycleStatusCode", command.lifecycleStatusCode().name())
                        .addValue("processingStatusCode", command.processingStatusCode().name())
                        .addValue("accessPolicyCode", command.accessPolicyCode().name()),
                UUID.class);

        LicenseMetadata license = command.licenseMetadata();
        jdbcTemplate.update(
                """
                INSERT INTO asset_version_licenses (
                    asset_version_id, license_status_code, license_source, license_reference,
                    usage_restrictions, license_holder, effective_at, expires_at, visibility_policy_code
                ) VALUES (
                    :assetVersionId, :licenseStatusCode, :licenseSource, :licenseReference,
                    :usageRestrictions, :licenseHolder, :effectiveAt, :expiresAt, :visibilityPolicyCode
                )
                """,
                new MapSqlParameterSource()
                        .addValue("assetVersionId", versionId)
                        .addValue("licenseStatusCode", license.licenseStatusCode().name())
                        .addValue("licenseSource", license.licenseSource())
                        .addValue("licenseReference", license.licenseReference())
                        .addValue("usageRestrictions", license.usageRestrictions())
                        .addValue("licenseHolder", license.licenseHolder())
                        .addValue("effectiveAt", timestamp(license.effectiveAt()))
                        .addValue("expiresAt", timestamp(license.expiresAt()))
                        .addValue("visibilityPolicyCode", license.visibilityPolicyCode().name()));

        jdbcTemplate.update(
                """
                INSERT INTO asset_version_lifecycle_events (
                    asset_version_id, from_lifecycle_status_code, to_lifecycle_status_code, reason_code, changed_by
                ) VALUES (:assetVersionId, NULL, :toLifecycleStatusCode, 'VERSION_CREATED', :changedBy)
                """,
                new MapSqlParameterSource()
                        .addValue("assetVersionId", versionId)
                        .addValue("toLifecycleStatusCode", command.lifecycleStatusCode().name())
                        .addValue("changedBy", command.createdBy()));

        if (command.makeCurrent()) {
            jdbcTemplate.update(
                    """
                    UPDATE logical_assets
                    SET current_asset_version_id = :versionId,
                        updated_at = NOW()
                    WHERE id = :assetId
                    """,
                    Map.of("versionId", versionId, "assetId", command.assetId()));
        }
        return findVersion(versionId).orElseThrow();
    }

    @Override
    public Optional<AssetRecord> findAsset(UUID assetId) {
        List<AssetRecord> rows = jdbcTemplate.query(
                "SELECT * FROM logical_assets WHERE id = :assetId",
                Map.of("assetId", assetId),
                (rs, rowNum) -> mapAsset(rs));
        return rows.stream().findFirst();
    }

    @Override
    public List<AssetRecord> listAssets() {
        return jdbcTemplate.query(
                "SELECT * FROM logical_assets ORDER BY created_at DESC, id ASC",
                (rs, rowNum) -> mapAsset(rs));
    }

    @Override
    public Optional<AssetVersionRecord> findVersion(UUID assetVersionId) {
        List<AssetVersionRecord> rows = jdbcTemplate.query(
                "SELECT * FROM asset_versions WHERE id = :assetVersionId",
                Map.of("assetVersionId", assetVersionId),
                (rs, rowNum) -> mapVersion(rs));
        return rows.stream().findFirst();
    }


    @Override
    @Transactional
    public AssetVersionRecord transitionProcessingStatus(
            UUID assetVersionId,
            AssetProcessingStatusCode processingStatusCode,
            String changedBy,
            String reasonCode) {
        jdbcTemplate.update(
                """
                UPDATE asset_versions
                SET processing_status_code = :processingStatusCode
                WHERE id = :assetVersionId
                """,
                new MapSqlParameterSource()
                        .addValue("assetVersionId", assetVersionId)
                        .addValue("processingStatusCode", processingStatusCode.name()));
        return findVersion(assetVersionId).orElseThrow();
    }

    @Override
    @Transactional
    public AssetVersionRecord quarantineVersion(UUID assetVersionId, String changedBy, String reasonCode) {
        AssetVersionRecord existing = findVersion(assetVersionId).orElseThrow();
        jdbcTemplate.update(
                """
                UPDATE asset_versions
                SET lifecycle_status_code = 'QUARANTINED',
                    processing_status_code = 'REJECTED'
                WHERE id = :assetVersionId
                """,
                Map.of("assetVersionId", assetVersionId));
        jdbcTemplate.update(
                """
                INSERT INTO asset_version_lifecycle_events (
                    asset_version_id, from_lifecycle_status_code, to_lifecycle_status_code, reason_code, changed_by
                ) VALUES (
                    :assetVersionId, :fromLifecycleStatusCode, 'QUARANTINED', :reasonCode, :changedBy
                )
                """,
                new MapSqlParameterSource()
                        .addValue("assetVersionId", assetVersionId)
                        .addValue("fromLifecycleStatusCode", existing.lifecycleStatusCode().name())
                        .addValue("reasonCode", reasonCode)
                        .addValue("changedBy", changedBy));
        return findVersion(assetVersionId).orElseThrow();
    }

    @Override
    @Transactional
    public AssetRecord archiveAsset(UUID assetId, String archivedBy, String reason) {
        jdbcTemplate.update(
                """
                UPDATE logical_assets
                SET lifecycle_status_code = 'ARCHIVED',
                    updated_at = NOW()
                WHERE id = :assetId
                """,
                Map.of("assetId", assetId));
        return findAsset(assetId).orElseThrow();
    }

    @Override
    @Transactional
    public AssetVersionRecord archiveVersion(UUID assetVersionId, String archivedBy, String reason) {
        AssetVersionRecord existing = findVersion(assetVersionId).orElseThrow();
        jdbcTemplate.update(
                """
                UPDATE asset_versions
                SET lifecycle_status_code = 'ARCHIVED'
                WHERE id = :assetVersionId
                """,
                Map.of("assetVersionId", assetVersionId));
        jdbcTemplate.update(
                """
                INSERT INTO asset_version_lifecycle_events (
                    asset_version_id, from_lifecycle_status_code, to_lifecycle_status_code, reason_code, changed_by
                ) VALUES (
                    :assetVersionId, :fromLifecycleStatusCode, 'ARCHIVED', :reasonCode, :changedBy
                )
                """,
                new MapSqlParameterSource()
                        .addValue("assetVersionId", assetVersionId)
                        .addValue("fromLifecycleStatusCode", existing.lifecycleStatusCode().name())
                        .addValue("reasonCode", reason == null ? "API_ARCHIVE" : reason)
                        .addValue("changedBy", archivedBy));
        return findVersion(assetVersionId).orElseThrow();
    }

    @Override
    public List<ControlledVocabularyRecord> listAssetTypes() {
        return vocabulary("asset_type_codes");
    }

    @Override
    public List<ControlledVocabularyRecord> listLifecycleStatuses() {
        return vocabulary("asset_lifecycle_status_codes");
    }

    @Override
    public List<ControlledVocabularyRecord> listProcessingStatuses() {
        return vocabulary("asset_processing_status_codes");
    }

    @Override
    public List<ControlledVocabularyRecord> listLicenseStatuses() {
        return vocabulary("asset_license_status_codes");
    }

    @Override
    public List<ControlledVocabularyRecord> listAccessPolicies() {
        return vocabulary("asset_access_policy_codes");
    }

    private List<ControlledVocabularyRecord> vocabulary(String tableName) {
        return jdbcTemplate.query(
                "SELECT code, display_name, is_active, sort_order FROM " + tableName + " ORDER BY sort_order ASC",
                (rs, rowNum) -> new ControlledVocabularyRecord(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        rs.getBoolean("is_active"),
                        rs.getInt("sort_order")));
    }

    private AssetRecord mapAsset(ResultSet rs) throws SQLException {
        UUID id = uuid(rs, "id");
        return new AssetRecord(
                id,
                uuid(rs, "stable_identifier"),
                AssetTypeCode.valueOf(rs.getString("asset_type_code")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("owner_actor"),
                rs.getString("owning_ministry"),
                AssetAccessPolicyCode.valueOf(rs.getString("default_access_policy_code")),
                AssetLifecycleStatusCode.valueOf(rs.getString("lifecycle_status_code")),
                uuid(rs, "current_asset_version_id"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                listVersions(id));
    }

    private List<AssetVersionRecord> listVersions(UUID assetId) {
        return jdbcTemplate.query(
                "SELECT * FROM asset_versions WHERE asset_id = :assetId ORDER BY version_number ASC",
                Map.of("assetId", assetId),
                (rs, rowNum) -> mapVersion(rs));
    }

    private AssetVersionRecord mapVersion(ResultSet rs) throws SQLException {
        UUID id = uuid(rs, "id");
        return new AssetVersionRecord(
                id,
                uuid(rs, "stable_identifier"),
                uuid(rs, "asset_id"),
                rs.getInt("version_number"),
                rs.getString("revision_code"),
                rs.getString("storage_provider_code"),
                rs.getString("storage_region"),
                rs.getString("storage_bucket_alias"),
                rs.getString("storage_key"),
                rs.getString("checksum_algorithm"),
                rs.getString("checksum_value"),
                rs.getString("mime_type"),
                rs.getLong("byte_size"),
                rs.getString("source_uri"),
                rs.getString("provenance_summary"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                AssetLifecycleStatusCode.valueOf(rs.getString("lifecycle_status_code")),
                AssetProcessingStatusCode.valueOf(rs.getString("processing_status_code")),
                AssetAccessPolicyCode.valueOf(rs.getString("access_policy_code")),
                findLicense(id));
    }

    private LicenseMetadata findLicense(UUID assetVersionId) {
        return jdbcTemplate.query(
                        "SELECT * FROM asset_version_licenses WHERE asset_version_id = :assetVersionId",
                        Map.of("assetVersionId", assetVersionId),
                        (rs, rowNum) -> new LicenseMetadata(
                                AssetLicenseStatusCode.valueOf(rs.getString("license_status_code")),
                                rs.getString("license_source"),
                                rs.getString("license_reference"),
                                rs.getString("usage_restrictions"),
                                rs.getString("license_holder"),
                                instant(rs, "effective_at"),
                                instant(rs, "expires_at"),
                                AssetAccessPolicyCode.valueOf(rs.getString("visibility_policy_code"))))
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
