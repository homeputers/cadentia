package com.cadentia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.auth.api.ForgotPasswordRequest;
import com.cadentia.auth.api.InternalAuthInvitationResponse;
import com.cadentia.auth.api.InternalInvitationRequest;
import com.cadentia.auth.api.LoginRequest;
import com.cadentia.auth.api.RefreshRequest;
import com.cadentia.auth.api.TokenResponse;
import com.cadentia.auth.config.AuthProperties;
import com.cadentia.auth.domain.AuthSession;
import com.cadentia.auth.domain.AuthUser;
import com.cadentia.auth.domain.PasswordResetToken;
import com.cadentia.auth.domain.UserStatus;
import com.cadentia.auth.repository.AuthRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private RecordingRepository repository;
    private RecordingPasswordEncoder passwordEncoder;
    private RecordingNotifier notifier;
    private AuthService service;
    private AuthUser user;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                "http://localhost:8081", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(30),
                5, Duration.ofMinutes(15), true, false, "", "", "http://localhost:5173", "", "", "", "");
        repository = new RecordingRepository();
        passwordEncoder = new RecordingPasswordEncoder();
        notifier = new RecordingNotifier();
        service = new AuthService(repository, passwordEncoder, parameters ->
                Jwt.withTokenValue("access-token").header("alg", "RS256").claim("sub", "test").build(), notifier, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new AuthUser(UUID.randomUUID(), "user@example.com", "Test User", "encoded-password",
                UserStatus.ACTIVE, 0, null, NOW, NOW, NOW);
        repository.user = user;
    }

    @Test
    void loginNormalizesEmailAndReturnsRotatingSessionTokens() {
        passwordEncoder.matches = true;

        TokenResponse response = service.login(new LoginRequest(" USER@EXAMPLE.COM ", "correct-password"), "browser", "127.0.0.1");

        assertThat(repository.lookedUpEmail).isEqualTo("user@example.com");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(repository.successfulLoginUser).isEqualTo(user.userId());
        assertThat(repository.createdSession.userId()).isEqualTo(user.userId());
    }

    @Test
    void badPasswordRecordsAFailure() {
        passwordEncoder.matches = false;

        assertThatThrownBy(() -> service.login(new LoginRequest(user.email(), "wrong"), null, null))
                .isInstanceOf(AuthException.class)
                .hasMessage("Email or password is incorrect.");

        assertThat(repository.failedAttempts).isEqualTo(1);
        assertThat(repository.failedLoginUser).isEqualTo(user.userId());
    }

    @Test
    void refreshRevokesTheOldSessionBeforeIssuingANewOne() {
        repository.session = new AuthSession(UUID.randomUUID(), user.userId(), "hashed", NOW.plus(Duration.ofDays(1)),
                null, NOW, NOW, null, null);

        TokenResponse response = service.refresh(new RefreshRequest("refresh-token"), null, null);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(repository.revokedSession).isEqualTo(repository.session.sessionId());
        assertThat(repository.createdSession.userId()).isEqualTo(user.userId());
    }

    @Test
    void forgotPasswordDoesNotNotifyUnknownAddresses() {
        repository.user = null;

        service.requestPasswordReset(new ForgotPasswordRequest("unknown@example.com"));

        assertThat(notifier.notifications).isEmpty();
        assertThat(repository.createdResetToken).isNull();
    }

    @Test
    void invitationCreatesAccountWithOneTimePasswordSetupToken() {
        repository.user = null;

        InternalAuthInvitationResponse invitation = service.invite(
                new InternalInvitationRequest(" New.User@Example.com ", "New User"));

        assertThat(invitation.userId()).isEqualTo(repository.createdUser.userId());
        assertThat(invitation.email()).isEqualTo("new.user@example.com");
        assertThat(invitation.activationToken()).isNotBlank();
        assertThat(repository.createdUser.passwordHash()).startsWith("encoded:");
        assertThat(repository.createdResetToken.userId()).isEqualTo(invitation.userId());
        assertThat(repository.createdResetToken.tokenHash()).isNotEqualTo(invitation.activationToken());
        assertThat(notifier.notifications).containsExactly("new.user@example.com:" + invitation.activationToken());
    }

    private static final class RecordingRepository extends AuthRepository {
        private AuthUser user;
        private String lookedUpEmail;
        private UUID successfulLoginUser;
        private UUID failedLoginUser;
        private int failedAttempts;
        private AuthSession session;
        private UUID revokedSession;
        private AuthSession createdSession;
        private PasswordResetToken createdResetToken;
        private AuthUser createdUser;

        private RecordingRepository() {
            super(null);
        }

        @Override
        public AuthUser createUser(UUID userId, String email, String displayName, String passwordHash) {
            createdUser = new AuthUser(userId, email, displayName, passwordHash, UserStatus.ACTIVE, 0, null, NOW, NOW, NOW);
            user = createdUser;
            return createdUser;
        }

        @Override
        public Optional<AuthUser> findUserByEmail(String email) {
            lookedUpEmail = email;
            return Optional.ofNullable(user);
        }

        @Override
        public Optional<AuthUser> findUserById(UUID userId) {
            return user != null && user.userId().equals(userId) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void recordSuccessfulLogin(UUID userId) {
            successfulLoginUser = userId;
        }

        @Override
        public void recordFailedLogin(UUID userId, int failedAttempts, Instant lockedUntil) {
            failedLoginUser = userId;
            this.failedAttempts = failedAttempts;
        }

        @Override
        public void createSession(UUID sessionId, UUID userId, String refreshTokenHash, Instant expiresAt,
                                  String userAgent, String ipAddress) {
            createdSession = new AuthSession(sessionId, userId, refreshTokenHash, expiresAt, null, NOW, NOW, userAgent, ipAddress);
        }

        @Override
        public Optional<AuthSession> findActiveSessionByRefreshHash(String refreshTokenHash) {
            return Optional.ofNullable(session);
        }

        @Override
        public void revokeSession(UUID sessionId) {
            revokedSession = sessionId;
        }

        @Override
        public void createPasswordResetToken(UUID tokenId, UUID userId, String tokenHash, Instant expiresAt) {
            createdResetToken = new PasswordResetToken(tokenId, userId, tokenHash, expiresAt, null);
        }
    }

    private static final class RecordingPasswordEncoder implements PasswordEncoder {
        private boolean matches;

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return matches;
        }
    }

    private static final class RecordingNotifier implements PasswordResetNotifier {
        private final List<String> notifications = new ArrayList<>();

        @Override
        public void send(AuthUser user, String rawToken) {
            notifications.add(user.email() + ":" + rawToken);
        }
    }
}
