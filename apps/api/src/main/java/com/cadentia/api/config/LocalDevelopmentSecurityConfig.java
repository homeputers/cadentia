package com.cadentia.api.config;

import com.cadentia.api.security.RbacAuthorities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@ConditionalOnProperty(name = "cadentia.auth.provider", havingValue = "local", matchIfMissing = true)
public class LocalDevelopmentSecurityConfig {

    @Bean
    UserDetailsService localDevelopmentUserDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${cadentia.local-admin.username:user}") String username,
            @Value("${cadentia.local-admin.password:cadentia}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .passwordEncoder(passwordEncoder::encode)
                .password(password)
                .authorities(
                        RbacAuthorities.ROLE_ADMIN,
                        RbacAuthorities.ROLE_CATALOG_EDITOR,
                        RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                        RbacAuthorities.ROLE_MUSICAL_REVIEWER,
                        RbacAuthorities.ROLE_WORSHIP_LEADER,
                        RbacAuthorities.ROLE_TEAM_SCHEDULER,
                        RbacAuthorities.ROLE_REPORTING_VIEWER,
                        RbacAuthorities.ROLE_INTEGRATION_MANAGER,
                        "catalog.admin.review",
                        "catalog.admin.approve")
                .build());
    }
}
