package com.cadentia.auth.config;

import com.cadentia.auth.repository.AuthRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrapRunner implements ApplicationRunner {

    private final AuthProperties properties;
    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthBootstrapRunner(AuthProperties properties, AuthRepository repository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = trimToEmpty(properties.bootstrapEmail());
        String displayName = trimToEmpty(properties.bootstrapDisplayName());
        String password = properties.bootstrapPassword() == null ? "" : properties.bootstrapPassword();
        if (email.isEmpty() && displayName.isEmpty() && password.isEmpty()) {
            return;
        }
        if (email.isEmpty() || displayName.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException(
                    "CADENTIA_AUTH_BOOTSTRAP_EMAIL, CADENTIA_AUTH_BOOTSTRAP_DISPLAY_NAME, and "
                            + "CADENTIA_AUTH_BOOTSTRAP_PASSWORD must be provided together.");
        }
        if (password.length() < 12) {
            throw new IllegalStateException("CADENTIA_AUTH_BOOTSTRAP_PASSWORD must contain at least 12 characters.");
        }
        email = email.toLowerCase(Locale.ROOT);
        if (repository.findUserByEmail(email).isEmpty()) {
            repository.createUser(UUID.randomUUID(), email, displayName, passwordEncoder.encode(password));
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
