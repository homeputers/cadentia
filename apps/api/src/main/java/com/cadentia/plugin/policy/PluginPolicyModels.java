package com.cadentia.plugin.policy;

import com.cadentia.plugin.PluginModels.Environment;
import com.cadentia.plugin.PluginModels.TrustTier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PluginPolicyModels {
    private PluginPolicyModels() {
    }

    public record PluginInvocationRequest(
            UUID pluginVersionId,
            UUID configurationVersionId,
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            String requiredSpiVersion,
            String actorId,
            Set<String> actorRoles,
            String packageName,
            Set<String> licenseScopes,
            Map<String, Object> requestedInput) {
    }

    public record CanonicalPolicySnapshot(
            Set<String> visibleSongIds,
            Set<String> approvedSongIds,
            Set<String> recommendableSongIds,
            Set<String> arrangementIds,
            Set<String> assetIds,
            Set<String> peopleIds,
            Set<String> servicePlanIds,
            Set<String> licenseScopes,
            Set<String> permittedRoles,
            Set<String> readableReviewNoteIds,
            Set<String> visibleInstanceIds,
            Map<String, Set<String>> packageLicenseScopes) {
    }

    public record PluginExecutionPolicy(
            UUID pluginVersionId,
            UUID configurationVersionId,
            String churchInstanceId,
            Environment environment,
            String extensionPoint,
            TrustTier trustTier,
            String policySnapshotId,
            Map<String, Object> inputView) {
    }

    public record PluginOutput(
            List<String> songIds,
            List<String> recommendableSongIds,
            List<String> arrangementIds,
            List<String> assetIds,
            List<String> peopleIds,
            List<String> servicePlanIds,
            List<String> reviewNoteIds,
            List<String> licenseScopes,
            List<String> instanceIds,
            Map<String, Object> attributes) {
    }

    public record SanitizedPluginOutput(
            List<String> songIds,
            List<String> recommendableSongIds,
            List<String> arrangementIds,
            List<String> assetIds,
            List<String> peopleIds,
            List<String> servicePlanIds,
            List<String> reviewNoteIds,
            List<String> licenseScopes,
            List<String> instanceIds,
            Map<String, Object> attributes,
            List<String> strippedFields) {
    }
}
