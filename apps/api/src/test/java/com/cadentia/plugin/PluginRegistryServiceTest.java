package com.cadentia.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.api.security.PluginRegistryAuthorizationPolicy;
import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.plugin.PluginModels.CertificationStatus;
import com.cadentia.plugin.PluginModels.ConfigurePluginCommand;
import com.cadentia.plugin.PluginModels.EnablePluginCommand;
import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.RegisterPluginCommand;
import com.cadentia.plugin.PluginModels.TrustTier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PluginRegistryServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PluginRegistryService service;
    private PluginRegistryAuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                "admin@example.test", "n/a", RbacAuthorities.ROLE_INTEGRATION_MANAGER));
        auditRecorder = new PluginRegistryAuditRecorder();
        service = new PluginRegistryService(new InMemoryPluginRegistryRepository(), new PluginConfigurationValidator(),
                new PluginRegistryAuthorizationPolicy(), auditRecorder,
                Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInvalidConfigurationAndRawSecrets() throws Exception {
        var plugin = service.registerApprovedPackage(registerCommand("1.0.0"));

        assertThatThrownBy(() -> service.updateConfiguration(new ConfigurePluginCommand(plugin.pluginVersionId(),
                "church-a", Environment.PRODUCTION, "lyrics.import", json("{\"apiToken\":\"raw\"}"), Map.of(), "admin")))
                .isInstanceOf(PluginRegistryException.class)
                .hasMessageContaining("raw secret values are not allowed");
    }

    @Test
    void enablesOnlyExplicitInstanceScopeAndBlocksDisabledExecution() throws Exception {
        var plugin = service.registerApprovedPackage(registerCommand("1.0.0"));
        var config = service.updateConfiguration(new ConfigurePluginCommand(plugin.pluginVersionId(), "church-a",
                Environment.PRODUCTION, "lyrics.import", json("{\"batchSize\":25}"),
                Map.of("apiToken", "vault:cadentia/church-a/import-token"), "admin"));

        var enablement = service.enable(new EnablePluginCommand(plugin.pluginVersionId(), config.configurationVersionId(),
                "church-a", Environment.PRODUCTION, "lyrics.import", "admin"));

        assertThat(enablement.status()).isEqualTo(LifecycleStatus.ENABLED);
        assertThat(service.canExecute("church-a", Environment.PRODUCTION, "lyrics.import", plugin.pluginVersionId())).isTrue();
        assertThat(service.canExecute("church-b", Environment.PRODUCTION, "lyrics.import", plugin.pluginVersionId())).isFalse();

        service.disable("church-a", Environment.PRODUCTION, "lyrics.import", "admin");

        assertThat(service.canExecute("church-a", Environment.PRODUCTION, "lyrics.import", plugin.pluginVersionId())).isFalse();
    }

    @Test
    void tracksLifecycleAuditAndVersionHistory() throws Exception {
        var versionOne = service.registerApprovedPackage(registerCommand("1.0.0"));
        var versionTwo = service.registerApprovedPackage(registerCommand("1.1.0"));

        var revoked = service.revoke(versionOne.pluginVersionId(), "admin");

        assertThat(revoked.lifecycleStatus()).isEqualTo(LifecycleStatus.REVOKED);
        assertThat(service.versionHistory("song-importer")).extracting("semanticVersion").containsExactly("1.0.0", "1.1.0");
        assertThat(auditRecorder.events()).extracting("action")
                .contains("PLUGIN_REGISTERED", "PLUGIN_REVOKED");
        assertThat(versionTwo.pluginVersionId()).isNotEqualTo(versionOne.pluginVersionId());
    }

    private RegisterPluginCommand registerCommand(String version) throws Exception {
        return new RegisterPluginCommand("song-importer", "@cadentia/song-importer", "Cadentia", version,
                List.of("plugin-spi.v1"), List.of("lyrics.import"), TrustTier.VERIFIED,
                "sha256:0123456789abcdef", "sigstore:bundle/ref", CertificationStatus.CERTIFIED,
                "oci://registry.example.test/cadentia/song-importer:" + version, schema(), "admin");
    }

    private static JsonNode schema() throws Exception {
        return json("{\"required\":[\"apiToken\",\"batchSize\"],\"properties\":{\"apiToken\":{\"type\":\"string\",\"secret\":true},\"batchSize\":{\"type\":\"integer\",\"default\":50}}}");
    }

    private static JsonNode json(String value) throws Exception {
        return MAPPER.readTree(value);
    }
}
