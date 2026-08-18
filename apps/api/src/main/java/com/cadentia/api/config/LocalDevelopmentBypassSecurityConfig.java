package com.cadentia.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permits all requests when running in local development mode (cadentia.instance.id=local-development).
 * Combined with LocalDevelopmentSecurityConfig's in-memory user, this allows the admin console
 * to be used without an OAuth provider. The AdminOperationsController automatically grants all
 * capabilities when isLocalDevelopment() is true.
 */
@Configuration
@ConditionalOnProperty(name = "cadentia.instance.id", havingValue = "local-development")
@Order(1)
public class LocalDevelopmentBypassSecurityConfig {

    @Bean
    SecurityFilterChain localDevelopmentBypassFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
