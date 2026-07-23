package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.AdminOperationsApi;
import com.cadentia.generated.model.AdminCapability;
import com.cadentia.generated.model.AdminSessionResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOperationsController implements AdminOperationsApi {

    private static final String LOCAL_DEVELOPMENT_INSTANCE = "local-development";

    private final String instanceId;

    public AdminOperationsController(@Value("${cadentia.instance.id:local-development}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<AdminSessionResponse> getAdminSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorId = authentication == null ? "anonymous" : authentication.getName();

        List<String> roles = isLocalDevelopment()
                ? List.of("ADMIN")
                : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(AdminOperationsController::normalizeRole)
                        .toList();

        List<AdminCapability> capabilities = isLocalDevelopment()
                ? Arrays.asList(AdminCapability.values())
                : capabilitiesForRoles(roles);

        AdminSessionResponse response = new AdminSessionResponse()
                .actorId(actorId)
                .displayName(actorId)
                .churchInstanceId(instanceId)
                .roles(roles)
                .capabilities(capabilities);

        return ResponseEntity.ok(response);
    }

    private boolean isLocalDevelopment() {
        return LOCAL_DEVELOPMENT_INSTANCE.equals(instanceId);
    }

    private static String normalizeRole(String authority) {
        return switch (authority) {
            case RbacAuthorities.ROLE_ADMIN -> "ADMIN";
            case RbacAuthorities.ROLE_WORSHIP_LEADER -> "WORSHIP_LEADER";
            case RbacAuthorities.ROLE_CATALOG_EDITOR -> "CATALOG_EDITOR";
            case RbacAuthorities.ROLE_DOCTRINAL_REVIEWER -> "DOCTRINAL_REVIEWER";
            case RbacAuthorities.ROLE_MUSICAL_REVIEWER -> "MUSICAL_REVIEWER";
            default -> authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority;
        };
    }

    private static List<AdminCapability> capabilitiesForRoles(List<String> roles) {
        if (roles.contains("ADMIN")) {
            return Arrays.asList(AdminCapability.values());
        }
        return List.of();
    }
}
