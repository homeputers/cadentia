package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.AdminOperationsApi;
import com.cadentia.generated.model.AdminCapability;
import com.cadentia.generated.model.AdminDiagnosticsResponse;
import com.cadentia.generated.model.AdminFeatureFlagChangePreviewResponse;
import com.cadentia.generated.model.AdminFeatureFlagListResponse;
import com.cadentia.generated.model.AdminFeatureFlagResponse;
import com.cadentia.generated.model.AdminInstanceConfigurationResponse;
import com.cadentia.generated.model.AdminSessionResponse;
import com.cadentia.generated.model.ConfirmAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.PreviewAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.UpdateAdminInstanceConfigurationRequest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final AdminOperationsService adminOperationsService;

    public AdminOperationsController(
            @Value("${cadentia.instance.id:local-development}") String instanceId,
            AdminOperationsService adminOperationsService) {
        this.instanceId = instanceId;
        this.adminOperationsService = adminOperationsService;
    }

    @Override
    public ResponseEntity<AdminDiagnosticsResponse> getAdminDiagnostics(String xChurchInstanceId) {
        return ResponseEntity.ok(adminOperationsService.diagnostics(xChurchInstanceId));
    }

    @Override
    public ResponseEntity<AdminInstanceConfigurationResponse> getAdminInstanceConfiguration(String xChurchInstanceId) {
        return ResponseEntity.ok(adminOperationsService.instanceConfiguration(xChurchInstanceId));
    }

    @Override
    public ResponseEntity<AdminSessionResponse> getAdminSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorId = authentication == null ? "anonymous" : authentication.getName();
        List<String> authorities = authentication == null
                ? List.of()
                : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();

        List<String> roles = isLocalDevelopment()
                ? List.of("ADMIN")
                : authorities.stream()
                        .map(AdminOperationsController::normalizeRole)
                        .toList();

        List<AdminCapability> capabilities = isLocalDevelopment()
                ? Arrays.asList(AdminCapability.values())
                : capabilitiesForAuthorities(authorities);

        AdminSessionResponse response = new AdminSessionResponse()
                .actorId(actorId)
                .displayName(actorId)
                .churchInstanceId(instanceId)
                .roles(roles)
                .capabilities(capabilities);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AdminFeatureFlagListResponse> listAdminFeatureFlags(String xChurchInstanceId) {
        return ResponseEntity.ok(adminOperationsService.featureFlags(xChurchInstanceId));
    }

    @Override
    public ResponseEntity<AdminFeatureFlagChangePreviewResponse> previewAdminFeatureFlagChange(
            String xChurchInstanceId,
            String flagKey,
            PreviewAdminFeatureFlagChangeRequest request) {
        return ResponseEntity.ok(adminOperationsService.previewFeatureFlagChange(flagKey, request));
    }

    @Override
    public ResponseEntity<AdminFeatureFlagResponse> confirmAdminFeatureFlagChange(
            String xChurchInstanceId,
            String flagKey,
            ConfirmAdminFeatureFlagChangeRequest request) {
        return ResponseEntity.ok(adminOperationsService.confirmFeatureFlagChange(flagKey, request));
    }

    @Override
    public ResponseEntity<AdminInstanceConfigurationResponse> updateAdminInstanceConfiguration(
            String xChurchInstanceId,
            UpdateAdminInstanceConfigurationRequest request) {
        return ResponseEntity.ok(adminOperationsService.updateInstanceConfiguration(xChurchInstanceId, request));
    }

    private boolean isLocalDevelopment() {
        return LOCAL_DEVELOPMENT_INSTANCE.equals(instanceId);
    }

    private static String normalizeRole(String authority) {
        return switch (authority) {
            case RbacAuthorities.ROLE_ADMIN, "ROLE_ADMIN" -> "ADMIN";
            case RbacAuthorities.ROLE_WORSHIP_LEADER, "ROLE_WORSHIP_LEADER" -> "WORSHIP_LEADER";
            case RbacAuthorities.ROLE_CATALOG_EDITOR, "ROLE_CATALOG_EDITOR" -> "CATALOG_EDITOR";
            case RbacAuthorities.ROLE_DOCTRINAL_REVIEWER, "ROLE_DOCTRINAL_REVIEWER" -> "DOCTRINAL_REVIEWER";
            case RbacAuthorities.ROLE_MUSICAL_REVIEWER, "ROLE_MUSICAL_REVIEWER" -> "MUSICAL_REVIEWER";
            default -> authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority;
        };
    }

    private static List<AdminCapability> capabilitiesForAuthorities(List<String> authorities) {
        if (hasAnyAuthority(authorities, RbacAuthorities.ROLE_ADMIN, "ROLE_ADMIN")) {
            return Arrays.asList(AdminCapability.values());
        }
        Set<AdminCapability> capabilities = new LinkedHashSet<>();
        if (hasAnyAuthority(
                authorities,
                RbacAuthorities.ROLE_CATALOG_EDITOR,
                "ROLE_CATALOG_EDITOR",
                "catalog.admin.review",
                "catalog.admin.approve")) {
            capabilities.add(AdminCapability.VIEW_IMPORT_QUEUE);
            capabilities.add(AdminCapability.REVIEW_CATALOG);
            capabilities.add(AdminCapability.MANAGE_MODERATION);
        }
        if (hasAnyAuthority(authorities, RbacAuthorities.ROLE_CATALOG_EDITOR, "ROLE_CATALOG_EDITOR")) {
            capabilities.add(AdminCapability.VIEW_AUDIT);
        }
        if (hasAnyAuthority(
                authorities,
                RbacAuthorities.ROLE_DOCTRINAL_REVIEWER,
                "ROLE_DOCTRINAL_REVIEWER",
                RbacAuthorities.ROLE_MUSICAL_REVIEWER,
                "ROLE_MUSICAL_REVIEWER")) {
            capabilities.add(AdminCapability.VIEW_IMPORT_QUEUE);
            capabilities.add(AdminCapability.REVIEW_CATALOG);
        }
        return List.copyOf(capabilities);
    }

    private static boolean hasAnyAuthority(List<String> authorities, String... expectedAuthorities) {
        return Arrays.stream(expectedAuthorities).anyMatch(authorities::contains);
    }

}
