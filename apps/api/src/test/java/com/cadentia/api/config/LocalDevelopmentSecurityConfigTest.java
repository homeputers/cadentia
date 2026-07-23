package com.cadentia.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.security.RbacAuthorities;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalDevelopmentSecurityConfigTest {

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
}
