package com.cadentia.auth.api;

import com.cadentia.auth.config.AuthProperties;
import com.cadentia.auth.domain.UserStatus;
import com.cadentia.auth.repository.AuthRepository;
import com.cadentia.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private static final String INTERNAL_API_KEY_HEADER = "X-Cadentia-Internal-Key";

    private final AuthRepository repository;
    private final AuthProperties properties;
    private final AuthService authService;

    public InternalUserController(AuthRepository repository, AuthProperties properties, AuthService authService) {
        this.repository = repository;
        this.properties = properties;
        this.authService = authService;
    }

    @GetMapping("/by-email")
    public ResponseEntity<InternalAuthUserResponse> findByEmail(
            @RequestParam String email,
            @RequestHeader(value = INTERNAL_API_KEY_HEADER, required = false) String apiKey) {
        requireInternalApiKey(apiKey);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return repository.findUserByEmail(normalizedEmail)
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .map(user -> new InternalAuthUserResponse(user.userId(), user.email(), user.displayName()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/invitations")
    public ResponseEntity<InternalAuthInvitationResponse> invite(
            @Valid @RequestBody InternalInvitationRequest request,
            @RequestHeader(value = INTERNAL_API_KEY_HEADER, required = false) String apiKey) {
        requireInternalApiKey(apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.invite(request));
    }

    private void requireInternalApiKey(String suppliedApiKey) {
        String configuredApiKey = properties.internalApiKey();
        if (configuredApiKey == null || configuredApiKey.isBlank() || suppliedApiKey == null
                || !MessageDigest.isEqual(configuredApiKey.getBytes(StandardCharsets.UTF_8), suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal API access is not authorized.");
        }
    }
}
