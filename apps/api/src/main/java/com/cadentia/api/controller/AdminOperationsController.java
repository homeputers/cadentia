package com.cadentia.api.controller;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.api.AdminOperationsApi;
import com.cadentia.generated.model.AdminAllowedAction;
import com.cadentia.generated.model.AdminCapability;
import com.cadentia.generated.model.AdminDiagnosticStatus;
import com.cadentia.generated.model.AdminDiagnosticsComponent;
import com.cadentia.generated.model.AdminDiagnosticsResponse;
import com.cadentia.generated.model.AdminFeatureFlagChangePreviewResponse;
import com.cadentia.generated.model.AdminFeatureFlagListResponse;
import com.cadentia.generated.model.AdminFeatureFlagResponse;
import com.cadentia.generated.model.AdminInstanceConfigurationResponse;
import com.cadentia.generated.model.AdminOptimisticConcurrency;
import com.cadentia.generated.model.AdminSessionResponse;
import com.cadentia.generated.model.ConfirmAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.PreviewAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.UpdateAdminInstanceConfigurationRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOperationsController implements AdminOperationsApi {

    private static final String LOCAL_DEVELOPMENT_INSTANCE = "local-development";

    private final String instanceId;
    private final Map<String, MutableFeatureFlag> featureFlags = new ConcurrentHashMap<>();
    private final Map<UUID, FeatureFlagPreview> featureFlagPreviews = new ConcurrentHashMap<>();
    private volatile InstanceConfigurationState configurationState;

    public AdminOperationsController(@Value("${cadentia.instance.id:local-development}") String instanceId) {
        this.instanceId = instanceId;
        this.configurationState = new InstanceConfigurationState(
                instanceId,
                "Cadentia local development",
                "en-US",
                "America/Guatemala",
                true,
                true,
                1L);
        featureFlags.put("admin-diagnostics", new MutableFeatureFlag("admin-diagnostics", "Admin diagnostics", true, 1L));
        featureFlags.put("admin-feature-flags", new MutableFeatureFlag("admin-feature-flags", "Admin feature-flag operations", true, 1L));
    }

    @Override
    public ResponseEntity<AdminDiagnosticsResponse> getAdminDiagnostics(String xChurchInstanceId) {
        return ResponseEntity.ok(new AdminDiagnosticsResponse()
                .churchInstanceId(xChurchInstanceId)
                .generatedAt(OffsetDateTime.now())
                .components(List.of(
                        new AdminDiagnosticsComponent()
                                .name("admin-operations")
                                .status(AdminDiagnosticStatus.OK)
                                .summary("Admin operations endpoints are available with redacted responses.")
                                .redactionApplied(true)
                                .lastCheckedAt(OffsetDateTime.now()),
                        new AdminDiagnosticsComponent()
                                .name("feature-flags")
                                .status(AdminDiagnosticStatus.OK)
                                .summary("Feature flag previews require explicit confirmation before mutation.")
                                .redactionApplied(true)
                                .lastCheckedAt(OffsetDateTime.now()))));
    }

    @Override
    public ResponseEntity<AdminInstanceConfigurationResponse> getAdminInstanceConfiguration(String xChurchInstanceId) {
        return ResponseEntity.ok(toConfigurationResponse(configurationState, xChurchInstanceId));
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

    @Override
    public ResponseEntity<AdminFeatureFlagListResponse> listAdminFeatureFlags(String xChurchInstanceId) {
        return ResponseEntity.ok(new AdminFeatureFlagListResponse()
                .churchInstanceId(xChurchInstanceId)
                .flags(featureFlags.values().stream()
                        .map(AdminOperationsController::toFeatureFlagResponse)
                        .sorted((left, right) -> left.getFlagKey().compareTo(right.getFlagKey()))
                        .toList()));
    }

    @Override
    public ResponseEntity<AdminFeatureFlagChangePreviewResponse> previewAdminFeatureFlagChange(
            String xChurchInstanceId,
            String flagKey,
            PreviewAdminFeatureFlagChangeRequest request) {
        MutableFeatureFlag flag = featureFlag(flagKey);
        if (!flag.version().equals(request.getExpectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature flag version has changed.");
        }
        UUID previewId = UUID.randomUUID();
        FeatureFlagPreview preview = new FeatureFlagPreview(previewId, flagKey, request.getEnabled());
        featureFlagPreviews.put(previewId, preview);
        return ResponseEntity.ok(new AdminFeatureFlagChangePreviewResponse()
                .previewId(previewId)
                .flagKey(flagKey)
                .requestedEnabled(request.getEnabled())
                .confirmationRequired(true)
                .impactSummary("Backend preview only: " + flag.description() + " will be "
                        + (Boolean.TRUE.equals(request.getEnabled()) ? "enabled." : "disabled."))
                .blockers(List.of()));
    }

    @Override
    public ResponseEntity<AdminFeatureFlagResponse> confirmAdminFeatureFlagChange(
            String xChurchInstanceId,
            String flagKey,
            ConfirmAdminFeatureFlagChangeRequest request) {
        FeatureFlagPreview preview = featureFlagPreviews.get(request.getPreviewId());
        if (preview == null || !preview.flagKey().equals(flagKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature flag preview is stale.");
        }
        if (!request.getPreviewId().toString().equals(request.getConfirmationText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation text must match the preview ID.");
        }
        MutableFeatureFlag flag = featureFlag(flagKey);
        flag.setEnabled(preview.enabled());
        flag.setVersion(flag.version() + 1);
        featureFlagPreviews.remove(request.getPreviewId());
        return ResponseEntity.ok(toFeatureFlagResponse(flag));
    }

    @Override
    public ResponseEntity<AdminInstanceConfigurationResponse> updateAdminInstanceConfiguration(
            String xChurchInstanceId,
            UpdateAdminInstanceConfigurationRequest request) {
        InstanceConfigurationState current = configurationState;
        if (!current.version().equals(request.getExpectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instance configuration version has changed.");
        }
        configurationState = new InstanceConfigurationState(
                xChurchInstanceId,
                request.getDisplayName(),
                request.getDefaultLocale(),
                request.getTimeZone(),
                Boolean.TRUE.equals(request.getDiagnosticsEnabled()),
                Boolean.TRUE.equals(request.getBotChannelsEnabled()),
                current.version() + 1);
        return ResponseEntity.ok(toConfigurationResponse(configurationState, xChurchInstanceId));
    }

    private boolean isLocalDevelopment() {
        return LOCAL_DEVELOPMENT_INSTANCE.equals(instanceId);
    }

    private static AdminInstanceConfigurationResponse toConfigurationResponse(
            InstanceConfigurationState state,
            String churchInstanceId) {
        return new AdminInstanceConfigurationResponse()
                .churchInstanceId(churchInstanceId)
                .displayName(state.displayName())
                .defaultLocale(state.defaultLocale())
                .timeZone(state.timeZone())
                .diagnosticsEnabled(state.diagnosticsEnabled())
                .botChannelsEnabled(state.botChannelsEnabled())
                .allowedActions(List.of(AdminAllowedAction.VIEW, AdminAllowedAction.UPDATE))
                .concurrency(new AdminOptimisticConcurrency()
                        .version(state.version())
                        .etag("instance-config-" + state.version()));
    }

    private MutableFeatureFlag featureFlag(String flagKey) {
        MutableFeatureFlag flag = featureFlags.get(flagKey);
        if (flag == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature flag not found.");
        }
        return flag;
    }

    private static AdminFeatureFlagResponse toFeatureFlagResponse(MutableFeatureFlag flag) {
        return new AdminFeatureFlagResponse()
                .flagKey(flag.flagKey())
                .description(flag.description())
                .enabled(flag.enabled())
                .allowedActions(List.of(AdminAllowedAction.VIEW, AdminAllowedAction.PREVIEW, AdminAllowedAction.CONFIRM))
                .concurrency(new AdminOptimisticConcurrency()
                        .version(flag.version())
                        .etag(flag.flagKey() + "-" + flag.version()));
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

    private record InstanceConfigurationState(
            String churchInstanceId,
            String displayName,
            String defaultLocale,
            String timeZone,
            boolean diagnosticsEnabled,
            boolean botChannelsEnabled,
            Long version) {}

    private record FeatureFlagPreview(UUID previewId, String flagKey, Boolean enabled) {}

    private static final class MutableFeatureFlag {
        private final String flagKey;
        private final String description;
        private Boolean enabled;
        private Long version;

        private MutableFeatureFlag(String flagKey, String description, Boolean enabled, Long version) {
            this.flagKey = flagKey;
            this.description = description;
            this.enabled = enabled;
            this.version = version;
        }

        private String flagKey() {
            return flagKey;
        }

        private String description() {
            return description;
        }

        private Boolean enabled() {
            return enabled;
        }

        private void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        private Long version() {
            return version;
        }

        private void setVersion(Long version) {
            this.version = version;
        }
    }
}
