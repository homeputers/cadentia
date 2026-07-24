package com.cadentia.api.controller;

import com.cadentia.generated.model.AdminAllowedAction;
import com.cadentia.generated.model.AdminBotChannelSummary;
import com.cadentia.generated.model.AdminConnectorSummary;
import com.cadentia.generated.model.AdminDiagnosticStatus;
import com.cadentia.generated.model.AdminDiagnosticsComponent;
import com.cadentia.generated.model.AdminDiagnosticsResponse;
import com.cadentia.generated.model.AdminFeatureFlagChangePreviewResponse;
import com.cadentia.generated.model.AdminFeatureFlagListResponse;
import com.cadentia.generated.model.AdminFeatureFlagResponse;
import com.cadentia.generated.model.AdminInstanceConfigurationResponse;
import com.cadentia.generated.model.AdminOptimisticConcurrency;
import com.cadentia.generated.model.AdminOperationalSettingSummary;
import com.cadentia.generated.model.AdminScoringProfileSummary;
import com.cadentia.generated.model.ConfirmAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.PreviewAdminFeatureFlagChangeRequest;
import com.cadentia.generated.model.UpdateAdminInstanceConfigurationRequest;
import com.cadentia.reng.scoring.ScoringProfileLifecycle;
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
        InstanceConfiguration configuration = configurationProvider.current();
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
        AdminDiagnosticsComponent runtimeConfiguration = new AdminDiagnosticsComponent()
                .name("runtime-configuration")
                .status(configuration.instanceId().equals(churchInstanceId)
                        ? AdminDiagnosticStatus.OK
                        : AdminDiagnosticStatus.DEGRADED)
                .summary("Runtime package "
                        + configuration.packageVersion()
                        + " exposes "
                        + configuration.integrations().size()
                        + " integration providers and "
                        + configuration.plugins().size()
                        + " plugin definitions.")
                .redactionApplied(true)
                .lastCheckedAt(generatedAt);
        return new AdminDiagnosticsResponse()
                .churchInstanceId(churchInstanceId)
                .generatedAt(generatedAt)
                .capabilityEnabled(diagnosticsEnabled)
                .recommendations(List.of())
                .components(List.of(operations, featureFlags, runtimeConfiguration));
    }

    public AdminInstanceConfigurationResponse instanceConfiguration(String churchInstanceId) {
        InstanceConfiguration configuration = configurationProvider.current();
        LocalConfigurationOverride override = localConfigurationOverride;
        String displayName = override == null ? configuration.instanceId() : override.displayName();
        String defaultLocale = override == null ? "en-US" : override.defaultLocale();
        String timeZone = override == null ? "UTC" : override.timeZone();
        boolean diagnosticsEnabled = override == null || override.diagnosticsEnabled();
        boolean botChannelsEnabled = override == null ? configuration.modules().externalMessaging() : override.botChannelsEnabled();
        long version = override == null ? 1L : override.version();

        return new AdminInstanceConfigurationResponse()
                .churchInstanceId(churchInstanceId)
                .displayName(displayName)
                .defaultLocale(defaultLocale)
                .timeZone(timeZone)
                .diagnosticsEnabled(diagnosticsEnabled)
                .botChannelsEnabled(botChannelsEnabled)
                .connectors(connectorSummaries(configuration))
                .botChannels(botChannelSummaries(configuration, botChannelsEnabled))
                .scoringProfiles(scoringProfileSummaries(configuration))
                .operationalSettings(operationalSettingSummaries(configuration))
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
        validateInstanceConfigurationRequest(request);
        long currentVersion = localConfigurationOverride == null ? 1L : localConfigurationOverride.version();
        if (!Long.valueOf(currentVersion).equals(request.getExpectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instance configuration version has changed.");
        }
        localConfigurationOverride = new LocalConfigurationOverride(
                request.getDisplayName().trim(),
                request.getDefaultLocale().trim(),
                request.getTimeZone().trim(),
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
        validatePreviewRequest(request);
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
        validateConfirmRequest(request);
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

    private static void validateInstanceConfigurationRequest(UpdateAdminInstanceConfigurationRequest request) {
        if (request == null
                || blank(request.getDisplayName())
                || blank(request.getDefaultLocale())
                || blank(request.getTimeZone())
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 1
                || blank(request.getActorId())
                || blank(request.getReason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instance configuration update is incomplete.");
        }
    }

    private static void validatePreviewRequest(PreviewAdminFeatureFlagChangeRequest request) {
        if (request == null
                || request.getEnabled() == null
                || request.getExpectedVersion() == null
                || request.getExpectedVersion() < 1
                || blank(request.getActorId())
                || blank(request.getReason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature flag preview request is incomplete.");
        }
    }

    private static void validateConfirmRequest(ConfirmAdminFeatureFlagChangeRequest request) {
        if (request == null
                || request.getPreviewId() == null
                || blank(request.getActorId())
                || blank(request.getConfirmationText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature flag confirmation request is incomplete.");
        }
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

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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

    private static List<AdminConnectorSummary> connectorSummaries(InstanceConfiguration configuration) {
        return configuration.integrations().stream()
                .map(provider -> new AdminConnectorSummary()
                        .key(provider.ref())
                        .label(labelFor(provider.ref()))
                        .enabled(provider.enabled())
                        .status(provider.enabled() ? "CONFIGURED" : "DISABLED")
                        .credentialState(provider.secretRef() == null || provider.secretRef().isBlank()
                                ? "No credential reference configured"
                                : "Configured; secret redacted"))
                .toList();
    }

    private static List<AdminBotChannelSummary> botChannelSummaries(
            InstanceConfiguration configuration,
            boolean botChannelsEnabled) {
        if (!configuration.modules().externalMessaging()) {
            return List.of();
        }
        return List.of(new AdminBotChannelSummary()
                .channelId("external-messaging")
                .label("External messaging")
                .enabled(botChannelsEnabled)
                .status(botChannelsEnabled ? "ENABLED" : "DISABLED"));
    }

    private static List<AdminScoringProfileSummary> scoringProfileSummaries(InstanceConfiguration configuration) {
        return List.of(new AdminScoringProfileSummary()
                .profileKey(configuration.scoringProfile().version())
                .label(labelFor(configuration.scoringProfile().version()))
                .active(configuration.scoringProfile().lifecycle().state() == ScoringProfileLifecycle.ProfileState.ACTIVE)
                .policyVersion(configuration.packageVersion()));
    }

    private static List<AdminOperationalSettingSummary> operationalSettingSummaries(InstanceConfiguration configuration) {
        return List.of(
                new AdminOperationalSettingSummary()
                        .key("cacheNamespace")
                        .label("Cache namespace")
                        .value(configuration.namespaces().cacheNamespace())
                        .editable(false),
                new AdminOperationalSettingSummary()
                        .key("eventNamespace")
                        .label("Event namespace")
                        .value(configuration.namespaces().eventNamespace())
                        .editable(false),
                new AdminOperationalSettingSummary()
                        .key("telemetryMetrics")
                        .label("Telemetry metrics")
                        .value(configuration.telemetryExport().metricsEnabled() ? "Enabled" : "Disabled")
                        .editable(false));
    }

    private static String labelFor(String value) {
        if (value == null || value.isBlank()) {
            return "Unnamed";
        }
        String normalized = value.replace('-', ' ').replace('_', ' ').trim();
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
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
