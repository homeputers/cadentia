package com.cadentia.admin;

import com.cadentia.api.auth.FirstPartyAuthClient;
import com.cadentia.api.auth.FirstPartyAuthInvitation;
import com.cadentia.api.auth.FirstPartyAuthUser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserAdministrationService {

    private static final Set<String> VALID_ROLES = Set.of(
            "VIEWER", "WORSHIP_LEADER", "CATALOG_EDITOR", "DOCTRINAL_REVIEWER", "MUSICAL_REVIEWER",
            "ADMIN", "TEAM_SCHEDULER", "ASSIGNED_MUSICIAN", "REPORTING_VIEWER", "INTEGRATION_MANAGER");

    private final AdminUserRepository repository;
    private final ObjectProvider<FirstPartyAuthClient> firstPartyAuthClient;
    private final String authProvider;

    public AdminUserAdministrationService(
            AdminUserRepository repository,
            ObjectProvider<FirstPartyAuthClient> firstPartyAuthClient,
            @Value("${cadentia.auth.provider:local}") String authProvider) {
        this.repository = repository;
        this.firstPartyAuthClient = firstPartyAuthClient;
        this.authProvider = authProvider;
    }

    public List<AdminUserRecord> list(String churchInstanceId, AdminUserStatus status, String search) {
        return repository.findAll(churchInstanceId, status, search);
    }

    public AdminUserCreationResult create(
            String churchInstanceId,
            String externalSubject,
            String displayName,
            String email,
            List<String> roles) {
        List<String> normalizedRoles = normalizeRoles(roles);
        String activationToken = null;
        if ("first-party".equals(authProvider)) {
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An email is required for a first-party account.");
            }
            FirstPartyAuthClient authClient = firstPartyAuthClient.getIfAvailable();
            if (authClient == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "First-party auth service integration is not configured.");
            }
            FirstPartyAuthUser authUser;
            try {
                authUser = authClient.findByEmail(email);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
                    throw exception;
                }
                FirstPartyAuthInvitation invitation = authClient.invite(email, displayName);
                authUser = new FirstPartyAuthUser(invitation.userId(), invitation.email(), invitation.displayName());
                activationToken = invitation.activationToken();
            }
            externalSubject = authUser.userId().toString();
            email = authUser.email();
            displayName = authUser.displayName();
        }
        validateIdentity(externalSubject, displayName);
        try {
            AdminUserRecord user = repository.create(churchInstanceId, externalSubject.trim(), displayName.trim(), normalizeEmail(email), normalizedRoles);
            return new AdminUserCreationResult(user, activationToken);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An identity with this subject is already provisioned.", exception);
        }
    }

    public AdminUserRecord update(
            String churchInstanceId,
            UUID userId,
            String actorSubject,
            String displayName,
            String email,
            AdminUserStatus status,
            long expectedVersion) {
        validateIdentity(userId.toString(), displayName);
        AdminUserRecord current = findOrNotFound(churchInstanceId, userId);
        if (current.externalSubject().equals(actorSubject)
                && current.status() == AdminUserStatus.ACTIVE
                && status == AdminUserStatus.SUSPENDED
                && current.roles().contains("ADMIN")
                && repository.countActiveUsersWithRole(churchInstanceId, "ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active administrator cannot be suspended.");
        }
        try {
            return repository.update(churchInstanceId, userId, displayName.trim(), normalizeEmail(email), status, expectedVersion);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The user changed while you were editing it.", exception);
        }
    }

    public AdminUserRecord replaceRoles(
            String churchInstanceId,
            UUID userId,
            String actorSubject,
            List<String> roles,
            long expectedVersion,
            String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required for role changes.");
        }
        List<String> normalizedRoles = normalizeRoles(roles);
        AdminUserRecord current = findOrNotFound(churchInstanceId, userId);
        if (current.externalSubject().equals(actorSubject)
                && current.status() == AdminUserStatus.ACTIVE
                && current.roles().contains("ADMIN")
                && !normalizedRoles.contains("ADMIN")
                && repository.countActiveUsersWithRole(churchInstanceId, "ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active administrator cannot remove their own administrator role.");
        }
        try {
            return repository.replaceRoles(churchInstanceId, userId, normalizedRoles, expectedVersion);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The user changed while you were editing it.", exception);
        }
    }

    public AdminUserRecord findOrNotFound(String churchInstanceId, UUID userId) {
        return repository.findById(churchInstanceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found."));
    }

    public static List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role is required.");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || !VALID_ROLES.contains(role.trim().toUpperCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more requested roles are invalid.");
            }
            normalized.add(role.trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private static void validateIdentity(String subject, String displayName) {
        if (subject == null || subject.isBlank() || displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject and display name are required.");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
