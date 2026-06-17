package com.cadentia.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PluginModels {
    private PluginModels() {
    }

    public enum TrustTier { CORE, VERIFIED, COMMUNITY, LOCAL }
    public enum CertificationStatus { PENDING, CERTIFIED, REJECTED, EXPIRED }
    public enum LifecycleStatus { REGISTERED, APPROVED, ENABLED, DISABLED, REVOKED, DELETED }
    public enum DeprecationStatus { ACTIVE, DEPRECATED, END_OF_LIFE }
    public enum Environment { DEVELOPMENT, STAGING, PRODUCTION }

    public record PluginPackage(
            UUID pluginVersionId,
            String stablePluginId,
            String packageName,
            String provider,
            String semanticVersion,
            List<String> supportedSpiVersions,
            List<String> extensionPoints,
            TrustTier trustTier,
            String checksumSha256,
            String signatureRef,
            CertificationStatus certificationStatus,
            String installationSource,
            LifecycleStatus lifecycleStatus,
            DeprecationStatus deprecationStatus,
            JsonNode configurationSchema,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy) {
    }

    public record PluginConfigurationSnapshot(
            UUID configurationVersionId,
            UUID pluginVersionId,
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            JsonNode configurationValues,
            Map<String, String> secretRefs,
            Instant createdAt,
            String createdBy) {
    }

    public record PluginEnablement(
            UUID enablementId,
            UUID pluginVersionId,
            UUID configurationVersionId,
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            LifecycleStatus status,
            Instant enabledAt,
            Instant disabledAt,
            String enabledBy,
            String disabledBy) {
    }

    public record RegisterPluginCommand(
            String stablePluginId,
            String packageName,
            String provider,
            String semanticVersion,
            List<String> supportedSpiVersions,
            List<String> extensionPoints,
            TrustTier trustTier,
            String checksumSha256,
            String signatureRef,
            CertificationStatus certificationStatus,
            String installationSource,
            JsonNode configurationSchema,
            String actor) {
    }

    public record ConfigurePluginCommand(
            UUID pluginVersionId,
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            JsonNode configurationValues,
            Map<String, String> secretRefs,
            String actor) {
    }

    public record EnablePluginCommand(
            UUID pluginVersionId,
            UUID configurationVersionId,
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            String actor) {
    }
}
