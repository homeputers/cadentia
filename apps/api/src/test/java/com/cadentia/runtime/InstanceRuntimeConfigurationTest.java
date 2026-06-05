package com.cadentia.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.reng.scoring.TeamConstraintCode;
import com.cadentia.reng.scoring.TeamConstraintMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstanceRuntimeConfigurationTest {
    private static final Path FIXTURE = Path.of(
            "..",
            "..",
            "packages",
            "intent-contracts",
            "fixtures",
            "church-config",
            "v1",
            "valid",
            "complete-package.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesValidatedPackageIntoRuntimeSettings() throws IOException {
        // Arrange
        JsonNode root = objectMapper.readTree(Files.readString(FIXTURE));

        // Act
        InstanceConfiguration configuration = InstanceConfiguration.fromPackage(root);

        // Assert
        assertThat(configuration.instanceId()).isEqualTo("river-city-worship");
        assertThat(configuration.modules().recommendation()).isTrue();
        assertThat(configuration.recommendationPolicy().praiseCount()).isEqualTo(10);
        assertThat(configuration.scoringProfile().version()).isEqualTo("balanced-default");
        assertThat(configuration.assetStorage().namespacePrefix()).isEqualTo("river-city-worship");
        assertThat(configuration.telemetryExport().metricsNamespace()).isEqualTo("river-city-worship");
    }

    @Test
    void parsesVersionedTeamConstraintModesFromScoringProfile() throws IOException {
        // Arrange
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(FIXTURE));
        ObjectNode activeProfile = (ObjectNode) root.path("scoringProfiles").path("profiles").get(0);
        activeProfile.set("teamConstraints", objectMapper.createObjectNode()
                .put("MISSING_REQUIRED_INSTRUMENT", "HARD_FILTER")
                .put("OPTIONAL_INSTRUMENT_FIT", "SCORING_INPUT")
                .put("INCOMPLETE_TEAM", "WARNING_ONLY"));

        // Act
        InstanceConfiguration configuration = InstanceConfiguration.fromPackage(root);

        // Assert
        assertThat(configuration.scoringProfile().teamConstraintMode(TeamConstraintCode.MISSING_REQUIRED_INSTRUMENT))
                .isEqualTo(TeamConstraintMode.HARD_FILTER);
        assertThat(configuration.scoringProfile().teamConstraintMode(TeamConstraintCode.OPTIONAL_INSTRUMENT_FIT))
                .isEqualTo(TeamConstraintMode.SCORING_INPUT);
        assertThat(configuration.scoringProfile().teamConstraintMode(TeamConstraintCode.INCOMPLETE_TEAM))
                .isEqualTo(TeamConstraintMode.WARNING_ONLY);
        assertThat(configuration.scoringProfile().teamConstraintMode(TeamConstraintCode.LEAD_VOCAL_RANGE_MISMATCH))
                .isEqualTo(TeamConstraintMode.DISABLED);
    }

    @Test
    void pluginAndIntegrationRegistriesRequireExplicitEnabledConfiguration() throws IOException {
        // Arrange
        InstanceConfiguration configuration = InstanceConfiguration.fromPackage(objectMapper.readTree(Files.readString(FIXTURE)));
        StaticInstanceConfigurationProvider provider = new StaticInstanceConfigurationProvider(configuration);
        PluginRegistry pluginRegistry = new PluginRegistry(provider);
        IntegrationRegistry integrationRegistry = new IntegrationRegistry(provider);

        // Act / Assert
        assertThat(pluginRegistry.requireEnabledPlugin("@cadentia/export-csv@1.0.0").permissions())
                .contains("read_catalog", "write_exports");
        assertThat(integrationRegistry.requireConfiguredIntegration("telegram.bot.v1").secretRef())
                .startsWith("secret-manager:");
        assertThatThrownBy(() -> pluginRegistry.requireEnabledPlugin("@cadentia/not-allowed@1.0.0"))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("allow-list");
        assertThatThrownBy(() -> integrationRegistry.requireConfiguredIntegration("missing.provider.v1"))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("disabled or missing");
    }

    @Test
    void resourceIdentifiersAndTelemetryUseInstanceNamespacesAndSafeLabels() throws IOException {
        // Arrange
        InstanceConfiguration configuration = InstanceConfiguration.fromPackage(objectMapper.readTree(Files.readString(FIXTURE)));
        StaticInstanceConfigurationProvider provider = new StaticInstanceConfigurationProvider(configuration);
        RuntimeResourceIdentifiers resources = new RuntimeResourceIdentifiers(provider);
        TelemetryEventFactory telemetry = new TelemetryEventFactory(provider);

        // Act / Assert
        assertThat(resources.assetObjectKey("slides/week-one.png")).isEqualTo("river-city-worship/slides/week-one.png");
        assertThat(resources.cacheKey("recommendation:abc")).isEqualTo("river-city-worship:recommendation:abc");
        assertThat(resources.eventStream("audit-events")).isEqualTo("river-city-worship.audit-events");
        assertThat(resources.backgroundJobQueue("imports")).isEqualTo("river-city-worship.jobs.imports");
        assertThat(telemetry.event("recommendation.generated", Map.of("module", "recommendation")).instanceId())
                .isEqualTo("river-city-worship");
        assertThatThrownBy(() -> telemetry.event("unsafe", Map.of("churchName", "River City Worship")))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("low-cardinality");
    }
}
