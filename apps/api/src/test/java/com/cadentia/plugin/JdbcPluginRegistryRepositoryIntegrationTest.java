package com.cadentia.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.plugin.PluginModels.CertificationStatus;
import com.cadentia.plugin.PluginModels.DeprecationStatus;
import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import com.cadentia.plugin.PluginModels.TrustTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPluginRegistryRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-17T00:00:00Z");

    private JdbcPluginRegistryRepository repository;
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        jdbcTemplate.getJdbcTemplate().execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        Flyway.configure().dataSource(dataSource).load().migrate();

        repository = new JdbcPluginRegistryRepository(jdbcTemplate, OBJECT_MAPPER);
    }

    @Test
    void persistsPackageConfigurationSnapshotAndScopedEnablement() throws Exception {
        // Arrange
        PluginPackage pluginPackage = pluginPackage("1.0.0", LifecycleStatus.APPROVED);

        // Act
        PluginPackage savedPackage = repository.savePackage(pluginPackage);
        PluginConfigurationSnapshot snapshot = repository.saveConfiguration(configuration(savedPackage.pluginVersionId()));
        PluginEnablement enablement = repository.saveEnablement(enablement(savedPackage.pluginVersionId(), snapshot.configurationVersionId()));

        // Assert
        assertThat(repository.findPackage(savedPackage.pluginVersionId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.stablePluginId()).isEqualTo("song-importer");
            assertThat(reloaded.supportedSpiVersions()).containsExactly("plugin-spi.v1");
            assertThat(reloaded.extensionPoints()).containsExactly("lyrics.import");
            assertThat(reloaded.configurationSchema().path("required")).hasSize(2);
        });
        assertThat(repository.findConfiguration(snapshot.configurationVersionId())).hasValueSatisfying(reloaded -> {
            assertThat(reloaded.churchInstanceId()).isEqualTo("church-a");
            assertThat(reloaded.environment()).isEqualTo(Environment.PRODUCTION);
            assertThat(reloaded.configurationValues().path("batchSize").asInt()).isEqualTo(25);
            assertThat(reloaded.secretRefs()).containsEntry("apiToken", "vault:cadentia/church-a/import-token");
        });
        assertThat(repository.findEnablement("church-a", Environment.PRODUCTION, "lyrics.import"))
                .hasValueSatisfying(reloaded -> {
                    assertThat(reloaded.enablementId()).isEqualTo(enablement.enablementId());
                    assertThat(reloaded.pluginVersionId()).isEqualTo(savedPackage.pluginVersionId());
                    assertThat(reloaded.status()).isEqualTo(LifecycleStatus.ENABLED);
                });
    }

    @Test
    void upsertsLifecycleStateAndReturnsStableVersionHistory() throws Exception {
        // Arrange
        PluginPackage versionOne = repository.savePackage(pluginPackage("1.0.0", LifecycleStatus.APPROVED));
        PluginPackage versionTwo = repository.savePackage(pluginPackage("1.1.0", LifecycleStatus.APPROVED));
        PluginPackage revoked = new PluginPackage(
                versionOne.pluginVersionId(),
                versionOne.stablePluginId(),
                versionOne.packageName(),
                versionOne.provider(),
                versionOne.semanticVersion(),
                versionOne.supportedSpiVersions(),
                versionOne.extensionPoints(),
                versionOne.trustTier(),
                versionOne.checksumSha256(),
                versionOne.signatureRef(),
                versionOne.certificationStatus(),
                versionOne.installationSource(),
                LifecycleStatus.REVOKED,
                DeprecationStatus.DEPRECATED,
                versionOne.configurationSchema(),
                versionOne.createdAt(),
                FIXED_INSTANT.plusSeconds(60),
                versionOne.createdBy(),
                "integrations-admin");

        // Act
        repository.savePackage(revoked);

        // Assert
        assertThat(repository.findPackage(versionOne.pluginVersionId()))
                .hasValueSatisfying(reloaded -> {
                    assertThat(reloaded.lifecycleStatus()).isEqualTo(LifecycleStatus.REVOKED);
                    assertThat(reloaded.deprecationStatus()).isEqualTo(DeprecationStatus.DEPRECATED);
                    assertThat(reloaded.updatedBy()).isEqualTo("integrations-admin");
                });
        assertThat(repository.findByStablePluginId("song-importer"))
                .extracting(PluginPackage::semanticVersion)
                .containsExactly("1.0.0", "1.1.0");
        assertThat(versionTwo.pluginVersionId()).isNotEqualTo(versionOne.pluginVersionId());
    }

    @Test
    void databaseConstraintsRejectPlaintextSecretsAndInvalidScopes() throws Exception {
        // Arrange
        PluginPackage pluginPackage = repository.savePackage(pluginPackage("1.0.0", LifecycleStatus.APPROVED));

        // Act / Assert
        assertThatThrownBy(() -> repository.saveConfiguration(new PluginConfigurationSnapshot(
                        UUID.randomUUID(),
                        pluginPackage.pluginVersionId(),
                        "church-a",
                        Environment.PRODUCTION,
                        "lyrics.import",
                        json("{\"apiToken\":\"plain-text\"}"),
                        Map.of("apiToken", "vault:cadentia/church-a/import-token"),
                        FIXED_INSTANT,
                        "integrations-admin")))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.saveEnablement(enablement(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(DataAccessException.class);
    }

    private static PluginPackage pluginPackage(String version, LifecycleStatus lifecycleStatus) throws Exception {
        UUID pluginVersionId = UUID.nameUUIDFromBytes(("song-importer-" + version).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new PluginPackage(
                pluginVersionId,
                "song-importer",
                "@cadentia/song-importer",
                "Cadentia",
                version,
                List.of("plugin-spi.v1"),
                List.of("lyrics.import"),
                TrustTier.VERIFIED,
                "sha256:0123456789abcdef",
                "sigstore:bundle/ref",
                CertificationStatus.CERTIFIED,
                "oci://registry.example.test/cadentia/song-importer:" + version,
                lifecycleStatus,
                DeprecationStatus.ACTIVE,
                schema(),
                FIXED_INSTANT,
                FIXED_INSTANT,
                "integrations-admin",
                "integrations-admin");
    }

    private static PluginConfigurationSnapshot configuration(UUID pluginVersionId) throws Exception {
        return new PluginConfigurationSnapshot(
                UUID.randomUUID(),
                pluginVersionId,
                "church-a",
                Environment.PRODUCTION,
                "lyrics.import",
                json("{\"batchSize\":25}"),
                Map.of("apiToken", "vault:cadentia/church-a/import-token"),
                FIXED_INSTANT,
                "integrations-admin");
    }

    private static PluginEnablement enablement(UUID pluginVersionId, UUID configurationVersionId) {
        return new PluginEnablement(
                UUID.randomUUID(),
                pluginVersionId,
                configurationVersionId,
                "church-a",
                Environment.PRODUCTION,
                "lyrics.import",
                LifecycleStatus.ENABLED,
                FIXED_INSTANT,
                null,
                "integrations-admin",
                null);
    }

    private static JsonNode schema() throws Exception {
        return json("{\"required\":[\"apiToken\",\"batchSize\"],\"properties\":{\"apiToken\":{\"type\":\"string\",\"secret\":true},\"batchSize\":{\"type\":\"integer\",\"default\":50}}}");
    }

    private static JsonNode json(String value) throws Exception {
        return OBJECT_MAPPER.readTree(value);
    }
}
