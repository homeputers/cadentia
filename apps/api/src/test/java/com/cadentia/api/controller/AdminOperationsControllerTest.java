package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.model.AdminCapability;
import com.cadentia.generated.model.ConfirmAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.PreviewAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.UpdateAdminInstanceConfigurationRequest;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringProfileLifecycle;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class AdminOperationsControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesLocalDevelopmentBasicUserAsAdminSession() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        AdminOperationsController controller = controller("local-development");

        // Act
        var response = controller.getAdminSession().getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getActorId()).isEqualTo("user");
        assertThat(response.getDisplayName()).isEqualTo("user");
        assertThat(response.getChurchInstanceId()).isEqualTo("local-development");
        assertThat(response.getRoles()).containsExactly("ADMIN");
        assertThat(response.getCapabilities()).containsExactly(AdminCapability.values());
    }

    @Test
    void mapsNonLocalAdminAuthorityToCapabilities() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "operator",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        AdminOperationsController controller = controller("church-prod");

        // Act
        var response = controller.getAdminSession().getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).containsExactly("ADMIN");
        assertThat(response.getCapabilities()).containsExactly(AdminCapability.values());
    }

    @Test
    void mapsNonLocalCatalogEditorAuthorityToReviewAndAuditCapabilities() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "catalog-editor",
                "password",
                List.of(new SimpleGrantedAuthority(RbacAuthorities.ROLE_CATALOG_EDITOR))));
        AdminOperationsController controller = controller("church-prod");

        // Act
        var response = controller.getAdminSession().getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).containsExactly("CATALOG_EDITOR");
        assertThat(response.getCapabilities()).containsExactly(
                AdminCapability.VIEW_IMPORT_QUEUE,
                AdminCapability.REVIEW_CATALOG,
                AdminCapability.MANAGE_MODERATION,
                AdminCapability.VIEW_AUDIT);
    }

    @Test
    void mapsNonLocalReviewerAuthoritiesToReviewOnlyCapabilities() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "doctrinal-reviewer",
                "password",
                List.of(new SimpleGrantedAuthority(RbacAuthorities.ROLE_DOCTRINAL_REVIEWER))));
        AdminOperationsController controller = controller("church-prod");

        // Act
        var response = controller.getAdminSession().getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).containsExactly("DOCTRINAL_REVIEWER");
        assertThat(response.getCapabilities()).containsExactly(
                AdminCapability.VIEW_IMPORT_QUEUE,
                AdminCapability.REVIEW_CATALOG);
    }

    @Test
    void mapsLegacyCatalogReviewAuthorityToReviewCapabilities() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "legacy-reviewer",
                "password",
                List.of(new SimpleGrantedAuthority("catalog.admin.review"))));
        AdminOperationsController controller = controller("church-prod");

        // Act
        var response = controller.getAdminSession().getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).containsExactly("catalog.admin.review");
        assertThat(response.getCapabilities()).containsExactly(
                AdminCapability.VIEW_IMPORT_QUEUE,
                AdminCapability.REVIEW_CATALOG,
                AdminCapability.MANAGE_MODERATION);
    }

    @Test
    void returnsLocalOperationalConfigurationAndFeatureFlagsOutOfTheBox() {
        // Arrange
        AdminOperationsController controller = controller("local-development");

        // Act
        var configuration = controller.getAdminInstanceConfiguration("local-development").getBody();
        var flags = controller.listAdminFeatureFlags("local-development").getBody();
        var diagnostics = controller.getAdminDiagnostics("local-development").getBody();

        // Assert
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedActions()).extracting(Enum::name).contains("VIEW", "UPDATE");
        assertThat(configuration.getConcurrency().getVersion()).isEqualTo(1L);
        assertThat(configuration.getScoringProfiles()).extracting(profile -> profile.getProfileKey())
                .containsExactly("local-development");
        assertThat(configuration.getOperationalSettings()).extracting(setting -> setting.getKey())
                .contains("cacheNamespace", "eventNamespace", "telemetryMetrics");
        assertThat(flags).isNotNull();
        assertThat(flags.getFlags()).extracting(flag -> flag.getFlagKey()).contains("admin-diagnostics", "admin-feature-flags");
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.getCapabilityEnabled()).isTrue();
        assertThat(diagnostics.getRecommendations()).isEmpty();
        assertThat(diagnostics.getComponents()).allSatisfy(component -> assertThat(component.getRedactionApplied()).isTrue());
    }

    @Test
    void derivesReadOnlyOperationalSummariesFromRuntimeConfigurationWithoutSecrets() {
        // Arrange
        InstanceConfiguration configuration = new InstanceConfiguration(
                "church-prod",
                "package-2026.07",
                new InstanceConfiguration.Modules(true, true, true, false, false, true),
                new InstanceConfiguration.RecommendationPolicy(
                        10,
                        5,
                        new InstanceConfiguration.KeyPolicy(true, true, 2),
                        new InstanceConfiguration.TempoPolicy(12),
                        true,
                        true),
                new ScoringProfile(
                        "reng-v4",
                        Map.of("themeFit", 3.0, "scriptureFit", 3.0),
                        List.of("title"),
                        ScoringProfileLifecycle.active()),
                List.of(
                        new InstanceConfiguration.IntegrationProvider(
                                "songselect",
                                "lyrics",
                                true,
                                "secret://songselect/api-key",
                                "https://songselect.example.test"),
                        new InstanceConfiguration.IntegrationProvider(
                                "planning-center",
                                "planning",
                                false,
                                null,
                                null)),
                List.of(new InstanceConfiguration.PluginDefinition("telegram-bot", true, List.of("messages:send"))),
                new InstanceConfiguration.AssetStorage("s3", "cadentia-assets", "church-prod", "kms-key"),
                new InstanceConfiguration.RuntimeNamespaces(
                        "church-prod/assets",
                        "church-prod/cache",
                        "church-prod/events",
                        List.of("church-prod.events.audit-events")),
                new InstanceConfiguration.TelemetryExport(true, "church-prod.metrics", true, "otlp", "ops-telemetry"));
        AdminOperationsController controller = controller(configuration);

        // Act
        var response = controller.getAdminInstanceConfiguration("church-prod").getBody();
        var diagnostics = controller.getAdminDiagnostics("church-prod").getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAllowedActions()).extracting(Enum::name).containsExactly("VIEW");
        assertThat(response.getConnectors()).hasSize(2);
        assertThat(response.getConnectors().get(0).getKey()).isEqualTo("songselect");
        assertThat(response.getConnectors().get(0).getCredentialState()).isEqualTo("Configured; secret redacted");
        assertThat(response.getConnectors().get(0).toString()).doesNotContain("secret://songselect/api-key");
        assertThat(response.getBotChannels()).singleElement().satisfies(channel -> {
            assertThat(channel.getChannelId()).isEqualTo("external-messaging");
            assertThat(channel.getEnabled()).isTrue();
        });
        assertThat(response.getScoringProfiles()).singleElement().satisfies(profile -> {
            assertThat(profile.getProfileKey()).isEqualTo("reng-v4");
            assertThat(profile.getPolicyVersion()).isEqualTo("package-2026.07");
        });
        assertThat(response.getOperationalSettings()).extracting(setting -> setting.getValue())
                .contains("church-prod/cache", "church-prod/events", "Enabled");
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.getComponents()).extracting(component -> component.getName())
                .contains("runtime-configuration");
    }

    @Test
    void previewsAndConfirmsFeatureFlagChangesWithExactPreviewConfirmation() {
        // Arrange
        AdminOperationsController controller = controller("local-development");
        PreviewAdminFeatureFlagChangeRequest previewRequest = new PreviewAdminFeatureFlagChangeRequest()
                .enabled(false)
                .expectedVersion(1L)
                .actorId("admin-1")
                .reason("Disable diagnostics locally");

        // Act
        var preview = controller.previewAdminFeatureFlagChange(
                "local-development",
                "admin-diagnostics",
                previewRequest).getBody();

        // Assert
        assertThat(preview).isNotNull();
        assertThat(preview.getConfirmationRequired()).isTrue();
        assertThatThrownBy(() -> controller.confirmAdminFeatureFlagChange(
                "local-development",
                "admin-diagnostics",
                new ConfirmAdminFeatureFlagChangeRequest()
                        .previewId(preview.getPreviewId())
                        .actorId("admin-1")
                        .confirmationText("wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        var updated = controller.confirmAdminFeatureFlagChange(
                "local-development",
                "admin-diagnostics",
                new ConfirmAdminFeatureFlagChangeRequest()
                        .previewId(preview.getPreviewId())
                        .actorId("admin-1")
                        .confirmationText(preview.getPreviewId().toString())).getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getConcurrency().getVersion()).isEqualTo(2L);
    }

    @Test
    void rejectsStaleInstanceConfigurationUpdates() {
        // Arrange
        AdminOperationsController controller = controller("local-development");
        controller.updateAdminInstanceConfiguration(
                "local-development",
                new UpdateAdminInstanceConfigurationRequest()
                        .displayName("Updated")
                        .defaultLocale("en-US")
                        .timeZone("America/Guatemala")
                        .diagnosticsEnabled(true)
                        .botChannelsEnabled(true)
                        .expectedVersion(1L)
                        .actorId("admin-1")
                        .reason("first update"));

        // Act / Assert
        assertThatThrownBy(() -> controller.updateAdminInstanceConfiguration(
                "local-development",
                new UpdateAdminInstanceConfigurationRequest()
                        .displayName("Stale")
                        .defaultLocale("en-US")
                        .timeZone("America/Guatemala")
                        .diagnosticsEnabled(true)
                        .botChannelsEnabled(true)
                        .expectedVersion(1L)
                        .actorId("admin-1")
                        .reason("stale update")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void rejectsIncompleteInstanceConfigurationUpdates() {
        // Arrange
        AdminOperationsController controller = controller("local-development");

        // Act / Assert
        assertThatThrownBy(() -> controller.updateAdminInstanceConfiguration(
                "local-development",
                new UpdateAdminInstanceConfigurationRequest()
                        .displayName(" ")
                        .defaultLocale("en-US")
                        .timeZone("America/Guatemala")
                        .diagnosticsEnabled(true)
                        .botChannelsEnabled(true)
                        .expectedVersion(1L)
                        .actorId("admin-1")
                        .reason("update")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("Instance configuration update is incomplete");
    }

    @Test
    void rejectsIncompleteFeatureFlagPreviewAndConfirmationRequests() {
        // Arrange
        AdminOperationsController controller = controller("local-development");

        // Act / Assert
        assertThatThrownBy(() -> controller.previewAdminFeatureFlagChange(
                "local-development",
                "admin-diagnostics",
                new PreviewAdminFeatureFlagChangeRequest()
                        .enabled(false)
                        .expectedVersion(1L)
                        .actorId(" ")
                        .reason("Disable diagnostics locally")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("Feature flag preview request is incomplete");

        assertThatThrownBy(() -> controller.confirmAdminFeatureFlagChange(
                "local-development",
                "admin-diagnostics",
                new ConfirmAdminFeatureFlagChangeRequest()
                        .previewId(null)
                        .actorId("admin-1")
                        .confirmationText(" ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST")
                .hasMessageContaining("Feature flag confirmation request is incomplete");
    }

    @Test
    void diagnosticsReflectLocalConfigurationCapabilityState() {
        // Arrange
        AdminOperationsController controller = controller("local-development");
        controller.updateAdminInstanceConfiguration(
                "local-development",
                new UpdateAdminInstanceConfigurationRequest()
                        .displayName("Local")
                        .defaultLocale("en-US")
                        .timeZone("America/Guatemala")
                        .diagnosticsEnabled(false)
                        .botChannelsEnabled(true)
                        .expectedVersion(1L)
                        .actorId("admin-1")
                        .reason("disable diagnostics"));

        // Act
        var diagnostics = controller.getAdminDiagnostics("local-development").getBody();

        // Assert
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.getCapabilityEnabled()).isFalse();
        assertThat(diagnostics.getRecommendations()).isEmpty();
    }

    @Test
    void doesNotExposeLocalFeatureFlagsForNonLocalInstances() {
        // Arrange
        AdminOperationsController controller = controller("church-prod");

        // Act
        var flags = controller.listAdminFeatureFlags("church-prod").getBody();

        // Assert
        assertThat(flags).isNotNull();
        assertThat(flags.getFlags()).isEmpty();
        assertThatThrownBy(() -> controller.previewAdminFeatureFlagChange(
                "church-prod",
                "admin-diagnostics",
                new PreviewAdminFeatureFlagChangeRequest()
                        .enabled(false)
                        .expectedVersion(1L)
                        .actorId("admin-1")
                        .reason("not local")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("501 NOT_IMPLEMENTED");
    }

    private static AdminOperationsController controller(String instanceId) {
        InstanceConfiguration configuration = InstanceConfiguration.localDevelopment(
                instanceId,
                "local",
                "bucket",
                instanceId,
                "key",
                "cache",
                "events",
                List.of("events.audit-events"));
        return controller(configuration);
    }

    private static AdminOperationsController controller(InstanceConfiguration configuration) {
        AdminOperationsService service = new AdminOperationsService(new StaticInstanceConfigurationProvider(configuration));
        return new AdminOperationsController(configuration.instanceId(), service);
    }
}
