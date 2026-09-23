package com.cadentia.auth.service;

import com.cadentia.auth.api.AuthUserResponse;
import com.cadentia.auth.api.ChangePasswordRequest;
import com.cadentia.auth.api.ForgotPasswordRequest;
import com.cadentia.auth.api.InternalAuthInvitationResponse;
import com.cadentia.auth.api.InternalInvitationRequest;
import com.cadentia.auth.api.LoginRequest;
import com.cadentia.auth.api.RefreshRequest;
import com.cadentia.auth.api.RegisterRequest;
import com.cadentia.auth.api.ResetPasswordRequest;
import com.cadentia.auth.api.TokenResponse;
import com.cadentia.auth.config.AuthProperties;
import com.cadentia.auth.domain.AuthSession;
import com.cadentia.auth.domain.AuthUser;
import com.cadentia.auth.domain.PasswordResetToken;
import com.cadentia.auth.domain.UserStatus;
import com.cadentia.auth.repository.AuthRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Email or password is incorrect.";

    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final PasswordResetNotifier passwordResetNotifier;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthService(
            AuthRepository repository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            PasswordResetNotifier passwordResetNotifier,
            AuthProperties properties) {
        this(repository, passwordEncoder, jwtEncoder, passwordResetNotifier, properties, Clock.systemUTC());
    }

    AuthService(
            AuthRepository repository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            PasswordResetNotifier passwordResetNotifier,
            AuthProperties properties,
            Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.passwordResetNotifier = passwordResetNotifier;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse login(LoginRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());
        AuthUser user = repository.findUserByEmail(email).orElseThrow(this::invalidCredentials);
        Instant now = clock.instant();
        if (user.status() != UserStatus.ACTIVE || user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            int failedAttempts = user.failedLoginAttempts() + 1;
            Instant lockedUntil = failedAttempts >= properties.maxFailedLoginAttempts()
                    ? now.plus(properties.lockDuration()) : null;
            repository.recordFailedLogin(user.userId(), failedAttempts, lockedUntil);
            throw invalidCredentials();
        }
        repository.recordSuccessfulLogin(user.userId());
        return issueTokens(user, userAgent, ipAddress, now);
    }

    public AuthUserResponse register(RegisterRequest request) {
        if (!properties.allowRegistration()) {
            throw new AuthException("REGISTRATION_DISABLED", HttpStatus.FORBIDDEN, "Public registration is disabled.");
        }
        String email = normalizeEmail(request.email());
        if (repository.findUserByEmail(email).isPresent()) {
            throw new AuthException("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT, "An account already exists for this email.");
        }
        AuthUser user = repository.createUser(UUID.randomUUID(), email, request.displayName().trim(), passwordEncoder.encode(request.password()));
        return AuthUserResponse.from(user);
    }

    @Transactional
    public InternalAuthInvitationResponse invite(InternalInvitationRequest request) {
        String email = normalizeEmail(request.email());
        if (repository.findUserByEmail(email).isPresent()) {
            throw new AuthException("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT,
                    "An account already exists for this email.");
        }
        AuthUser user = repository.createUser(
                UUID.randomUUID(),
                email,
                request.displayName().trim(),
                passwordEncoder.encode(randomToken()));
        String activationToken = randomToken();
        repository.createPasswordResetToken(
                UUID.randomUUID(),
                user.userId(),
                hash(activationToken),
                clock.instant().plus(properties.passwordResetTtl()));
        passwordResetNotifier.send(user, activationToken);
        return new InternalAuthInvitationResponse(user.userId(), user.email(), user.displayName(), activationToken);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, String userAgent, String ipAddress) {
        String rawRefreshToken = request.refreshToken();
        AuthSession session = repository.findActiveSessionByRefreshHash(hash(rawRefreshToken))
                .orElseThrow(this::invalidRefreshToken);
        AuthUser user = repository.findUserById(session.userId()).filter(candidate -> candidate.status() == UserStatus.ACTIVE)
                .orElseThrow(this::invalidRefreshToken);
        Instant now = clock.instant();
        repository.revokeSession(session.sessionId());
        return issueTokens(user, userAgent, ipAddress, now);
    }

    public void logout(String refreshToken) {
        repository.findActiveSessionByRefreshHash(hash(refreshToken)).ifPresent(session -> repository.revokeSession(session.sessionId()));
    }

    public AuthUserResponse currentUser(UUID userId) {
        return AuthUserResponse.from(findActiveUser(userId));
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthUser user = findActiveUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new AuthException("CURRENT_PASSWORD_INVALID", HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        }
        repository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        repository.revokeAllSessions(userId);
    }

    public void requestPasswordReset(ForgotPasswordRequest request) {
        repository.findUserByEmail(normalizeEmail(request.email()))
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    String rawToken = randomToken();
                    repository.createPasswordResetToken(UUID.randomUUID(), user.userId(), hash(rawToken),
                            clock.instant().plus(properties.passwordResetTtl()));
                    passwordResetNotifier.send(user, rawToken);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = repository.findActivePasswordResetToken(hash(request.token()))
                .orElseThrow(this::invalidPasswordResetToken);
        AuthUser user = findActiveUser(token.userId());
        repository.updatePassword(user.userId(), passwordEncoder.encode(request.newPassword()));
        repository.markPasswordResetTokenUsed(token.tokenId());
        repository.revokeAllSessions(user.userId());
    }

    private TokenResponse issueTokens(AuthUser user, String userAgent, String ipAddress, Instant now) {
        UUID sessionId = UUID.randomUUID();
        String refreshToken = randomToken();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        repository.createSession(sessionId, user.userId(), hash(refreshToken), expiresAt, userAgent, ipAddress);
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.userId().toString())
                .audience(List.of("cadentia-api"))
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .claim("email", user.email())
                .claim("name", user.displayName())
                .claim("sid", sessionId.toString())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenResponse(accessToken, "Bearer", properties.accessTokenTtl().toSeconds(), refreshToken, AuthUserResponse.from(user));
    }

    private AuthUser findActiveUser(UUID userId) {
        return repository.findUserById(userId)
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "User was not found."));
    }

    private AuthException invalidCredentials() {
        return new AuthException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
    }

    private AuthException invalidRefreshToken() {
        return new AuthException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "The refresh token is invalid or expired.");
    }

    private AuthException invalidPasswordResetToken() {
        return new AuthException("INVALID_PASSWORD_RESET_TOKEN", HttpStatus.BAD_REQUEST, "The password reset link is invalid or expired.");
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
