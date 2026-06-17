package com.cadentia.plugin;

import com.cadentia.plugin.PluginModels.CertificationStatus;
import com.cadentia.plugin.PluginModels.DeprecationStatus;
import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import com.cadentia.plugin.PluginModels.TrustTier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!plugin-in-memory")
public class JdbcPluginRegistryRepository implements PluginRegistryRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPluginRegistryRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PluginPackage savePackage(PluginPackage pluginPackage) {
        jdbcTemplate.update(
                """
                INSERT INTO plugin_package_versions (
                    plugin_version_id, stable_plugin_id, package_name, provider, semantic_version,
                    supported_spi_versions, extension_points, trust_tier, checksum_sha256, signature_ref,
                    certification_status, installation_source, lifecycle_status, deprecation_status,
                    configuration_schema, created_at, updated_at, created_by, updated_by
                ) VALUES (
                    :pluginVersionId, :stablePluginId, :packageName, :provider, :semanticVersion,
                    CAST(:supportedSpiVersions AS jsonb), CAST(:extensionPoints AS jsonb), :trustTier,
                    :checksumSha256, :signatureRef, :certificationStatus, :installationSource,
                    :lifecycleStatus, :deprecationStatus, CAST(:configurationSchema AS jsonb),
                    :createdAt, :updatedAt, :createdBy, :updatedBy
                )
                ON CONFLICT (plugin_version_id) DO UPDATE SET
                    lifecycle_status = EXCLUDED.lifecycle_status,
                    deprecation_status = EXCLUDED.deprecation_status,
                    updated_at = EXCLUDED.updated_at,
                    updated_by = EXCLUDED.updated_by
                """,
                packageParams(pluginPackage));
        return findPackage(pluginPackage.pluginVersionId()).orElseThrow();
    }

    @Override
    public Optional<PluginPackage> findPackage(UUID pluginVersionId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM plugin_package_versions
                WHERE plugin_version_id = :pluginVersionId
                """,
                new MapSqlParameterSource("pluginVersionId", pluginVersionId),
                this::mapPackage).stream().findFirst();
    }

    @Override
    public List<PluginPackage> findByStablePluginId(String stablePluginId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM plugin_package_versions
                WHERE stable_plugin_id = :stablePluginId
                ORDER BY semantic_version
                """,
                new MapSqlParameterSource("stablePluginId", stablePluginId),
                this::mapPackage);
    }

    @Override
    public List<PluginPackage> findAllPackages() {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM plugin_package_versions
                ORDER BY stable_plugin_id, semantic_version
                """,
                this::mapPackage);
    }

    @Override
    @Transactional
    public PluginConfigurationSnapshot saveConfiguration(PluginConfigurationSnapshot snapshot) {
        jdbcTemplate.update(
                """
                INSERT INTO plugin_configuration_snapshots (
                    configuration_version_id, plugin_version_id, church_instance_id, environment,
                    extension_point, configuration_values, secret_refs, created_at, created_by
                ) VALUES (
                    :configurationVersionId, :pluginVersionId, :churchInstanceId, :environment,
                    :extensionPoint, CAST(:configurationValues AS jsonb), CAST(:secretRefs AS jsonb),
                    :createdAt, :createdBy
                )
                """,
                configurationParams(snapshot));
        return findConfiguration(snapshot.configurationVersionId()).orElseThrow();
    }

    @Override
    public Optional<PluginConfigurationSnapshot> findConfiguration(UUID configurationVersionId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM plugin_configuration_snapshots
                WHERE configuration_version_id = :configurationVersionId
                """,
                new MapSqlParameterSource("configurationVersionId", configurationVersionId),
                this::mapConfiguration).stream().findFirst();
    }

    @Override
    @Transactional
    public PluginEnablement saveEnablement(PluginEnablement enablement) {
        jdbcTemplate.update(
                """
                INSERT INTO plugin_instance_enablements (
                    enablement_id, plugin_version_id, configuration_version_id, church_instance_id,
                    environment, extension_point, lifecycle_status, enabled_at, disabled_at, enabled_by, disabled_by
                ) VALUES (
                    :enablementId, :pluginVersionId, :configurationVersionId, :churchInstanceId,
                    :environment, :extensionPoint, :lifecycleStatus, :enabledAt, :disabledAt, :enabledBy, :disabledBy
                )
                ON CONFLICT (church_instance_id, environment, extension_point) DO UPDATE SET
                    plugin_version_id = EXCLUDED.plugin_version_id,
                    configuration_version_id = EXCLUDED.configuration_version_id,
                    lifecycle_status = EXCLUDED.lifecycle_status,
                    disabled_at = EXCLUDED.disabled_at,
                    disabled_by = EXCLUDED.disabled_by
                """,
                enablementParams(enablement));
        return findEnablement(enablement.churchInstanceId(), enablement.environment(), enablement.extensionPoint()).orElseThrow();
    }

    @Override
    public Optional<PluginEnablement> findEnablement(
            String churchInstanceId,
            Environment environment,
            String extensionPoint) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM plugin_instance_enablements
                WHERE church_instance_id = :churchInstanceId
                  AND environment = :environment
                  AND extension_point = :extensionPoint
                """,
                new MapSqlParameterSource()
                        .addValue("churchInstanceId", churchInstanceId)
                        .addValue("environment", environment.name())
                        .addValue("extensionPoint", extensionPoint),
                this::mapEnablement).stream().findFirst();
    }

    @Override
    public List<PluginEnablement> findEnablements(UUID pluginVersionId) {
        return jdbcTemplate.query(
                """
                SELECT *
                FROM plugin_instance_enablements
                WHERE plugin_version_id = :pluginVersionId
                ORDER BY church_instance_id, environment, extension_point
                """,
                new MapSqlParameterSource("pluginVersionId", pluginVersionId),
                this::mapEnablement);
    }

    private MapSqlParameterSource packageParams(PluginPackage pluginPackage) {
        return new MapSqlParameterSource()
                .addValue("pluginVersionId", pluginPackage.pluginVersionId())
                .addValue("stablePluginId", pluginPackage.stablePluginId())
                .addValue("packageName", pluginPackage.packageName())
                .addValue("provider", pluginPackage.provider())
                .addValue("semanticVersion", pluginPackage.semanticVersion())
                .addValue("supportedSpiVersions", writeJson(pluginPackage.supportedSpiVersions()))
                .addValue("extensionPoints", writeJson(pluginPackage.extensionPoints()))
                .addValue("trustTier", pluginPackage.trustTier().name())
                .addValue("checksumSha256", pluginPackage.checksumSha256())
                .addValue("signatureRef", pluginPackage.signatureRef())
                .addValue("certificationStatus", pluginPackage.certificationStatus().name())
                .addValue("installationSource", pluginPackage.installationSource())
                .addValue("lifecycleStatus", pluginPackage.lifecycleStatus().name())
                .addValue("deprecationStatus", pluginPackage.deprecationStatus().name())
                .addValue("configurationSchema", writeJson(pluginPackage.configurationSchema()))
                .addValue("createdAt", Timestamp.from(pluginPackage.createdAt()))
                .addValue("updatedAt", Timestamp.from(pluginPackage.updatedAt()))
                .addValue("createdBy", pluginPackage.createdBy())
                .addValue("updatedBy", pluginPackage.updatedBy());
    }

    private MapSqlParameterSource configurationParams(PluginConfigurationSnapshot snapshot) {
        return new MapSqlParameterSource()
                .addValue("configurationVersionId", snapshot.configurationVersionId())
                .addValue("pluginVersionId", snapshot.pluginVersionId())
                .addValue("churchInstanceId", snapshot.churchInstanceId())
                .addValue("environment", snapshot.environment().name())
                .addValue("extensionPoint", snapshot.extensionPoint())
                .addValue("configurationValues", writeJson(snapshot.configurationValues()))
                .addValue("secretRefs", writeJson(snapshot.secretRefs()))
                .addValue("createdAt", Timestamp.from(snapshot.createdAt()))
                .addValue("createdBy", snapshot.createdBy());
    }

    private MapSqlParameterSource enablementParams(PluginEnablement enablement) {
        return new MapSqlParameterSource()
                .addValue("enablementId", enablement.enablementId())
                .addValue("pluginVersionId", enablement.pluginVersionId())
                .addValue("configurationVersionId", enablement.configurationVersionId())
                .addValue("churchInstanceId", enablement.churchInstanceId())
                .addValue("environment", enablement.environment().name())
                .addValue("extensionPoint", enablement.extensionPoint())
                .addValue("lifecycleStatus", enablement.status().name())
                .addValue("enabledAt", Timestamp.from(enablement.enabledAt()))
                .addValue("disabledAt", timestamp(enablement.disabledAt()))
                .addValue("enabledBy", enablement.enabledBy())
                .addValue("disabledBy", enablement.disabledBy());
    }

    private PluginPackage mapPackage(ResultSet rs, int rowNumber) throws SQLException {
        return new PluginPackage(
                rs.getObject("plugin_version_id", UUID.class),
                rs.getString("stable_plugin_id"),
                rs.getString("package_name"),
                rs.getString("provider"),
                rs.getString("semantic_version"),
                readValue(rs.getString("supported_spi_versions"), STRING_LIST),
                readValue(rs.getString("extension_points"), STRING_LIST),
                TrustTier.valueOf(rs.getString("trust_tier")),
                rs.getString("checksum_sha256"),
                rs.getString("signature_ref"),
                CertificationStatus.valueOf(rs.getString("certification_status")),
                rs.getString("installation_source"),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                DeprecationStatus.valueOf(rs.getString("deprecation_status")),
                readTree(rs.getString("configuration_schema")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getString("created_by"),
                rs.getString("updated_by"));
    }

    private PluginConfigurationSnapshot mapConfiguration(ResultSet rs, int rowNumber) throws SQLException {
        return new PluginConfigurationSnapshot(
                rs.getObject("configuration_version_id", UUID.class),
                rs.getObject("plugin_version_id", UUID.class),
                rs.getString("church_instance_id"),
                Environment.valueOf(rs.getString("environment")),
                rs.getString("extension_point"),
                readTree(rs.getString("configuration_values")),
                readValue(rs.getString("secret_refs"), STRING_MAP),
                instant(rs, "created_at"),
                rs.getString("created_by"));
    }

    private PluginEnablement mapEnablement(ResultSet rs, int rowNumber) throws SQLException {
        return new PluginEnablement(
                rs.getObject("enablement_id", UUID.class),
                rs.getObject("plugin_version_id", UUID.class),
                rs.getObject("configuration_version_id", UUID.class),
                rs.getString("church_instance_id"),
                Environment.valueOf(rs.getString("environment")),
                rs.getString("extension_point"),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                instant(rs, "enabled_at"),
                nullableInstant(rs, "disabled_at"),
                rs.getString("enabled_by"),
                rs.getString("disabled_by"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new PluginRegistryException(List.of("Unable to serialize plugin registry JSON."));
        }
    }

    private JsonNode readTree(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new PluginRegistryException(List.of("Unable to read plugin registry JSON."));
        }
    }

    private <T> T readValue(String value, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new PluginRegistryException(List.of("Unable to read plugin registry JSON."));
        }
    }

    private static Instant instant(ResultSet rs, String columnName) throws SQLException {
        return rs.getTimestamp(columnName).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
