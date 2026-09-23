package com.cadentia.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.auth.domain.AuthUser;
import com.cadentia.auth.repository.AuthRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthBootstrapRunnerTest {

    @Test
    void seedsTheConfiguredAccountAndIsIdempotent() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AuthBootstrapRunner runner = new AuthBootstrapRunner(properties(" Admin@Example.com ", "Initial Admin", "a-strong-password"), repository,
                new RecordingPasswordEncoder());

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(repository.createdUserId).isNotNull();
        assertThat(repository.createdEmail).isEqualTo("admin@example.com");
        assertThat(repository.createdDisplayName).isEqualTo("Initial Admin");
        assertThat(repository.createdPasswordHash).isEqualTo("encoded:a-strong-password");
        assertThat(repository.createCalls).isEqualTo(1);
    }

    @Test
    void doesNothingWhenBootstrapIsNotConfigured() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        AuthBootstrapRunner runner = new AuthBootstrapRunner(properties("", "", ""), repository,
                new RecordingPasswordEncoder());

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(repository.createCalls).isZero();
    }

    @Test
    void rejectsPartialOrWeakBootstrapConfiguration() {
        RecordingRepository repository = new RecordingRepository();

        AuthBootstrapRunner partial = new AuthBootstrapRunner(properties("admin@example.com", "", ""), repository,
                new RecordingPasswordEncoder());
        assertThatThrownBy(() -> partial.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be provided together");

        AuthBootstrapRunner weak = new AuthBootstrapRunner(properties("admin@example.com", "Admin", "short"), repository,
                new RecordingPasswordEncoder());
        assertThatThrownBy(() -> weak.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters");
    }

    private static AuthProperties properties(String email, String displayName, String password) {
        return new AuthProperties(
                "http://localhost:8081", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(30),
                5, Duration.ofMinutes(15), true, false, "", "", "http://localhost:5173", "internal-key",
                email, displayName, password);
    }

    private static final class RecordingRepository extends AuthRepository {
        private UUID createdUserId;
        private String createdEmail;
        private String createdDisplayName;
        private String createdPasswordHash;
        private int createCalls;

        private RecordingRepository() {
            super(null);
        }

        @Override
        public Optional<AuthUser> findUserByEmail(String email) {
            return createCalls == 0 ? Optional.empty() : Optional.of(new AuthUser(
                    createdUserId, createdEmail, createdDisplayName, createdPasswordHash, null, 0,
                    null, null, null, null));
        }

        @Override
        public AuthUser createUser(UUID userId, String email, String displayName, String passwordHash) {
            createCalls++;
            createdUserId = userId;
            createdEmail = email;
            createdDisplayName = displayName;
            createdPasswordHash = passwordHash;
            return new AuthUser(userId, email, displayName, passwordHash, null, 0, null, null, null, null);
        }
    }

    private static final class RecordingPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return false;
        }
    }
}
