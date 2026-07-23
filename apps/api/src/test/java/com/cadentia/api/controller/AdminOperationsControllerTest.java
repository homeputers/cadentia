package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.AdminCapability;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
