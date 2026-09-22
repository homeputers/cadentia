package com.cadentia.api.controller;

import com.cadentia.admin.AdminUserAdministrationService;
import com.cadentia.admin.AdminUserRecord;
import com.cadentia.admin.AdminUserStatus;
import com.cadentia.generated.api.AdminUserAdministrationApi;
import com.cadentia.generated.model.AdminRole;
import com.cadentia.generated.model.AdminUser;
import com.cadentia.generated.model.AdminUserListResponse;
import com.cadentia.generated.model.CreateAdminUserRequest;
import com.cadentia.generated.model.ReplaceAdminUserRolesRequest;
import com.cadentia.generated.model.UpdateAdminUserRequest;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminUserAdministrationController implements AdminUserAdministrationApi {

    private final String instanceId;
    private final AdminUserAdministrationService service;

    public AdminUserAdministrationController(
            @Value("${cadentia.instance.id:local-development}") String instanceId,
            AdminUserAdministrationService service) {
        this.instanceId = instanceId;
        this.service = service;
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminUserListResponse> listAdminUsers(
            String xChurchInstanceId, com.cadentia.generated.model.AdminUserStatus status, String search) {
        requireInstanceScope(xChurchInstanceId);
        List<AdminUser> users = service.list(instanceId, status == null ? null : AdminUserStatus.valueOf(status.name()), search)
                .stream().map(AdminUserAdministrationController::toApi).toList();
        return ResponseEntity.ok(new AdminUserListResponse().items(users).totalItems((long) users.size()));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminUser> createAdminUser(String xChurchInstanceId, CreateAdminUserRequest request) {
        requireInstanceScope(xChurchInstanceId);
        AdminUserRecord user = service.create(instanceId, request.getExternalSubject(), request.getDisplayName(), request.getEmail(),
                request.getRoles().stream().map(Enum::name).toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(user));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminUser> updateAdminUser(String xChurchInstanceId, UUID userId, UpdateAdminUserRequest request) {
        requireInstanceScope(xChurchInstanceId);
        AdminUserRecord user = service.update(instanceId, userId, actorSubject(), request.getDisplayName(), request.getEmail(),
                AdminUserStatus.valueOf(request.getStatus().name()), request.getExpectedVersion());
        return ResponseEntity.ok(toApi(user));
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.cadentia.api.security.RbacAuthorities).ROLE_ADMIN)")
    public ResponseEntity<AdminUser> replaceAdminUserRoles(
            String xChurchInstanceId, UUID userId, ReplaceAdminUserRolesRequest request) {
        requireInstanceScope(xChurchInstanceId);
        AdminUserRecord user = service.replaceRoles(instanceId, userId, actorSubject(),
                request.getRoles().stream().map(Enum::name).toList(), request.getExpectedVersion(), request.getReason());
        return ResponseEntity.ok(toApi(user));
    }

    private String actorSubject() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return authentication.getName();
    }

    private void requireInstanceScope(String requestedInstanceId) {
        if (requestedInstanceId == null || !instanceId.equals(requestedInstanceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The requested church instance is not available to this actor.");
        }
    }

    private static AdminUser toApi(AdminUserRecord user) {
        Set<AdminRole> roles = user.roles().stream().map(AdminRole::fromValue).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new AdminUser()
                .userId(user.userId())
                .churchInstanceId(user.churchInstanceId())
                .externalSubject(user.externalSubject())
                .displayName(user.displayName())
                .email(user.email())
                .status(com.cadentia.generated.model.AdminUserStatus.fromValue(user.status().name()))
                .roles(roles)
                .version(user.version())
                .createdAt(user.createdAt().atOffset(ZoneOffset.UTC))
                .updatedAt(user.updatedAt().atOffset(ZoneOffset.UTC));
    }
}
