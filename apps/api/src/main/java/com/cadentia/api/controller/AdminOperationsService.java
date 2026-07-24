package com.cadentia.api.controller;

import com.cadentia.generated.model.AdminAllowedAction;
import com.cadentia.generated.model.AdminDiagnosticStatus;
import com.cadentia.generated.model.AdminDiagnosticsComponent;
import com.cadentia.generated.model.AdminDiagnosticsResponse;
import com.cadentia.generated.model.AdminFeatureFlagChangePreviewResponse;
import com.cadentia.generated.model.AdminFeatureFlagListResponse;
import com.cadentia.generated.model.AdminFeatureFlagResponse;
import com.cadentia.generated.model.AdminInstanceConfigurationResponse;
import com.cadentia.generated.model.AdminOptimisticConcurrency;
import com.cadentia.generated.model.ConfirmAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.PreviewAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.UpdateAdminInstanceConfigurationRequest;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminOperationsService {

    private static final String LOCAL_DEVELOPMENT_INSTANCE = "local-development";

    private final InstanceConfigurationProvider configurationProvider;
    private final Map<String, MutableFeatureFlag> localFeatureFlags = new ConcurrentHashMap<>();
    private final Map<UUID, FeatureFlagPreview> localFeatureFlagPreviews = new ConcurrentHashMap<>();
    private volatile LocalConfigurationOverride localConfigurationOverride;

    public AdminOperationsService(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
        localFeatureFlags.put("admin-diagnostics", new MutableFeatureFlag("admin-diagnostics", "Admin diagnostics", true, 1L));
        localFeatureFlags.put("admin-feature-flags", new MutableFeatureFlag("admin-feature-flags", "Admin feature-flag operations", true, 1L));
    }

    public AdminDiagnosticsResponse diagnostics(String churchInstanceId) {
        OffsetDateTime generatedAt = OffsetDateTime.now();
        boolean diagnosticsEnabled = diagnosticsEnabled();
        AdminDiagnosticsComponent operations = new AdminDiagnosticsComponent()
                .name("admin-operations")
                .status(AdminDiagnosticStatus.OK)
                .summary("Admin operations endpoints are available with redacted responses.")
                .redactionApplied(true)
                .lastCheckedAt(generatedAt);
        AdminDiagnosticsComponent featureFlags = new AdminDiagnosticsComponent()
                .name("feature-flags")
                .status(localDevelopment() ? AdminDiagnosticStatus.OK : AdminDiagnosticStatus.UNKNOWN)
                .summary(localDevelopment()
                        ? "Local feature flag previews require explicit confirmation before mutation."
                        : "Persistent feature flag storage is not configured for this instance.")
                .redactionApplied(true)
                .lastCheckedAt(generatedAt);
        return new AdminDiagnosticsResponse()
                .churchInstanceId(churchInstanceId)
                .generatedAt(generatedAt)
                .capabilityEnabled(diagnosticsEnabled)
                .recommendations(List.of())
                .components(List.of(operations, featureFlags));
    }

    public AdminInstanceConfigurationResponse instanceConfiguration(String churchInstanceId) {
        InstanceConfiguration configuration = configurationProvider.current();
        LocalConfigurationOverride override = localConfigurationOverride;
        String displayName = override == null ? configuration.instanceId() : override.displayName();
        String defaultLocale = override == null ? "en-US" : override.defaultLocale();
        String timeZone = override == null ? "UTC" : override.timeZone();
        boolean diagnosticsEnabled = override == null || override.diagnosticsEnabled();
        boolean botChannelsEnabled = override != null && override.botChannelsEnabled();
        long version = override == null ? 1L : override.version();

        return new AdminInstanceConfigurationResponse()
                .churchInstanceId(churchInstanceId)
                .displayName(displayName)
                .defaultLocale(defaultLocale)
                .timeZone(timeZone)
                .diagnosticsEnabled(diagnosticsEnabled)
                .botChannelsEnabled(botChannelsEnabled)
                .allowedActions(localDevelopment()
                        ? List.of(AdminAllowedAction.VIEW, AdminAllowedAction.UPDATE)
                        : List.of(AdminAllowedAction.VIEW))
                .concurrency(new AdminOptimisticConcurrency()
                        .version(version)
                        .etag("instance-config-" + configuration.instanceId() + "-" + version));
    }

    public AdminInstanceConfigurationResponse updateInstanceConfiguration(
            String churchInstanceId,
            UpdateAdminInstanceConfigurationRequest request) {
        requireLocalDevelopment();
        long currentVersion = localConfigurationOverride == null ? 1L : localConfigurationOverride.version();
        if (!Long.valueOf(currentVersion).equals(request.getExpectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instance configuration version has changed.");
        }
        localConfigurationOverride = new LocalConfigurationOverride(
                request.getDisplayName(),
                request.getDefaultLocale(),
                request.getTimeZone(),
                Boolean.TRUE.equals(request.getDiagnosticsEnabled()),
                Boolean.TRUE.equals(request.getBotChannelsEnabled()),
                currentVersion + 1);
        return instanceConfiguration(churchInstanceId);
    }

    public AdminFeatureFlagListResponse featureFlags(String churchInstanceId) {
        return new AdminFeatureFlagListResponse()
                .churchInstanceId(churchInstanceId)
                .flags(localDevelopment()
                        ? localFeatureFlags.values().stream()
                                .map(AdminOperationsService::toFeatureFlagResponse)
                                .sorted((left, right) -> left.getFlagKey().compareTo(right.getFlagKey()))
                                .toList()
                        : List.of());
    }

    public AdminFeatureFlagChangePreviewResponse previewFeatureFlagChange(
            String flagKey,
            PreviewAdminFeatureFlagChangeRequest request) {
        requireLocalDevelopment();
        MutableFeatureFlag flag = featureFlag(flagKey);
        if (!flag.version().equals(request.getExpectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature flag version has changed.");
        }
        UUID previewId = UUID.randomUUID();
        localFeatureFlagPreviews.put(previewId, new FeatureFlagPreview(flagKey, request.getEnabled()));
        return new AdminFeatureFlagChangePreviewResponse()
                .previewId(previewId)
                .flagKey(flagKey)
                .requestedEnabled(request.getEnabled())
                .confirmationRequired(true)
                .impactSummary("Backend preview only: " + flag.description() + " will be "
                        + (Boolean.TRUE.equals(request.getEnabled()) ? "enabled." : "disabled."))
                .blockers(List.of());
    }

    public AdminFeatureFlagResponse confirmFeatureFlagChange(
            String flagKey,
            ConfirmAdminFeatureFlagChangeRequest request) {
        requireLocalDevelopment();
        FeatureFlagPreview preview = localFeatureFlagPreviews.get(request.getPreviewId());
        if (preview == null || !preview.flagKey().equals(flagKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feature flag preview is stale.");
        }
        if (!request.getPreviewId().toString().equals(request.getConfirmationText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation text must match the preview ID.");
        }
        MutableFeatureFlag flag = featureFlag(flagKey);
        flag.setEnabled(preview.enabled());
        flag.setVersion(flag.version() + 1);
        localFeatureFlagPreviews.remove(request.getPreviewId());
        return toFeatureFlagResponse(flag);
    }

    private boolean localDevelopment() {
        return LOCAL_DEVELOPMENT_INSTANCE.equals(configurationProvider.current().instanceId());
    }

    private boolean diagnosticsEnabled() {
        LocalConfigurationOverride override = localConfigurationOverride;
        return override == null || override.diagnosticsEnabled();
    }

    private void requireLocalDevelopment() {
        if (!localDevelopment()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Persistent admin operations storage is not configured for this instance.");
        }
    }

    private MutableFeatureFlag featureFlag(String flagKey) {
        MutableFeatureFlag flag = localFeatureFlags.get(flagKey);
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

    private record LocalConfigurationOverride(
            String displayName,
            String defaultLocale,
            String timeZone,
            boolean diagnosticsEnabled,
            boolean botChannelsEnabled,
            Long version) {}

    private record FeatureFlagPreview(String flagKey, Boolean enabled) {}

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
