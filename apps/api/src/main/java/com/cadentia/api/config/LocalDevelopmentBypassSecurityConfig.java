package com.cadentia.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Permits all requests when running in local development mode (cadentia.instance.id=local-development).
 * Combined with LocalDevelopmentSecurityConfig's in-memory user, this allows the admin console
 * to be used without an OAuth provider. The AdminOperationsController automatically grants all
 * capabilities when isLocalDevelopment() is true.
 *
 * <p>A {@link LocalDevelopmentAuthenticationFilter} is inserted before the anonymous filter so that
 * every request carries a fully-authenticated local-admin-approver principal. This satisfies
 * {@code @PreAuthorize} method-security guards on import/review controllers that check for specific
 * authorities even though the admin console sends no OAuth credentials.
 */
@Configuration
@ConditionalOnProperty(name = "cadentia.instance.id", havingValue = "local-development")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(1)
public class LocalDevelopmentBypassSecurityConfig {

    @Bean
    SecurityFilterChain localDevelopmentBypassFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new LocalDevelopmentAuthenticationFilter(), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
