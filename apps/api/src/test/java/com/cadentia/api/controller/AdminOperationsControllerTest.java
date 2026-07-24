package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.generated.model.AdminCapability;
import com.cadentia.generated.model.ConfirmAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.PreviewAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.UpdateAdminInstanceConfigurationRequest;
import java.util.List;
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
        AdminOperationsController controller = new AdminOperationsController("local-development");

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
        AdminOperationsController controller = new AdminOperationsController("church-prod");

        // Act
        var response = controller.getAdminSession().getBody();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRoles()).containsExactly("ADMIN");
        assertThat(response.getCapabilities()).containsExactly(AdminCapability.values());
    }

    @Test
    void returnsOperationalConfigurationAndFeatureFlagsOutOfTheBox() {
        // Arrange
        AdminOperationsController controller = new AdminOperationsController("church-prod");

        // Act
        var configuration = controller.getAdminInstanceConfiguration("church-prod").getBody();
        var flags = controller.listAdminFeatureFlags("church-prod").getBody();
        var diagnostics = controller.getAdminDiagnostics("church-prod").getBody();

        // Assert
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedActions()).extracting(Enum::name).contains("VIEW", "UPDATE");
        assertThat(configuration.getConcurrency().getVersion()).isEqualTo(1L);
        assertThat(flags).isNotNull();
        assertThat(flags.getFlags()).extracting(flag -> flag.getFlagKey()).contains("admin-diagnostics", "admin-feature-flags");
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.getComponents()).allSatisfy(component -> assertThat(component.getRedactionApplied()).isTrue());
    }

    @Test
    void previewsAndConfirmsFeatureFlagChangesWithExactPreviewConfirmation() {
        // Arrange
        AdminOperationsController controller = new AdminOperationsController("church-prod");
        PreviewAdminFeatureFlagChangeRequest previewRequest = new PreviewAdminFeatureFlagChangeRequest()
                .enabled(false)
                .expectedVersion(1L)
                .actorId("admin-1")
                .reason("Disable diagnostics locally");

        // Act
        var preview = controller.previewAdminFeatureFlagChange(
                "church-prod",
                "admin-diagnostics",
                previewRequest).getBody();

        // Assert
        assertThat(preview).isNotNull();
        assertThat(preview.getConfirmationRequired()).isTrue();
        assertThatThrownBy(() -> controller.confirmAdminFeatureFlagChange(
                "church-prod",
                "admin-diagnostics",
                new ConfirmAdminFeatureFlagChangeRequest()
                        .previewId(preview.getPreviewId())
                        .actorId("admin-1")
                        .confirmationText("wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        var updated = controller.confirmAdminFeatureFlagChange(
                "church-prod",
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
        AdminOperationsController controller = new AdminOperationsController("church-prod");
        controller.updateAdminInstanceConfiguration(
                "church-prod",
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
                "church-prod",
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
}
