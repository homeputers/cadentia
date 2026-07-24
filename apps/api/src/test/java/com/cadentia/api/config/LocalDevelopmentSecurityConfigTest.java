package com.cadentia.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.security.RbacAuthorities;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;

class LocalDevelopmentSecurityConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalDevelopmentSecurityConfig.class, TestPasswordConfig.class);

    @Test
    void localDevelopmentUserHasCadentiaAdminAuthorities() {
        // Arrange
        LocalDevelopmentSecurityConfig config = new LocalDevelopmentSecurityConfig();
        var users = config.localDevelopmentUserDetailsService(new BCryptPasswordEncoder(), "user", "cadentia");

        // Act
        var user = users.loadUserByUsername("user");

        // Assert
        assertThat(user.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains(
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_CATALOG_EDITOR,
                        RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                        RbacAuthorities.ROLE_MUSICAL_REVIEWER,
                        "catalog.admin.review",
                        "catalog.admin.approve");
    }

    @Test
    void localAuthProviderCreatesLocalAdminUserForNonLocalInstance() {
        // Arrange / Act / Assert
        contextRunner
                .withPropertyValues(
                        "cadentia.instance.id=iglesia-local",
                        "cadentia.auth.provider=local")
                .run(context -> assertThat(context).hasSingleBean(UserDetailsService.class));
    }

    @Test
    void nonLocalAuthProviderDoesNotCreateLocalAdminUser() {
        // Arrange / Act / Assert
        contextRunner
                .withPropertyValues(
                        "cadentia.instance.id=iglesia-local",
                        "cadentia.auth.provider=oidc")
                .run(context -> assertThat(context).doesNotHaveBean(UserDetailsService.class));
    }

    @Configuration
    static class TestPasswordConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
