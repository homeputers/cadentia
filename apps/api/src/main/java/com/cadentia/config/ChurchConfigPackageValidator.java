package com.cadentia.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ChurchConfigPackageValidator {
    public static final String SUPPORTED_SCHEMA_VERSION = "church-config.v1";
    public static final String DEFAULT_APPLICATION_VERSION = "0.1.0";

    private static final Set<String> REQUIRED_SECTIONS = Set.of(
            "package",
            "instance",
            "modules",
            "policies",
            "scoringProfiles",
            "vocabularies",
            "approvalGates",
            "workflowDefaults",
            "branding",
            "integrations",
            "pluginAllowList",
            "assetStorage",
            "featureFlags",
            "observability");
    private static final Set<String> OPTIONAL_SECTIONS = Set.of("moduleSpecific", "extensions");
    private static final Pattern INTEGRATION_REF = Pattern.compile("^[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*(?:\\.v\\d+)?$");
    private static final Pattern PLUGIN_REF = Pattern.compile("^@[a-z0-9-]+/[a-z][a-z0-9-]*(?:@\\d+\\.\\d+\\.\\d+)?$");
    private static final Pattern SECRET_REF = Pattern.compile("^(secret-manager|env|vault|aws-sm|gcp-sm|azure-kv):[A-Za-z0-9_./:@-]+$");
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$");

    public void validate(JsonNode root, String applicationVersion) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            throw new ChurchConfigValidationException(List.of("/: package must be a JSON object"));
        }

        for (String section : REQUIRED_SECTIONS) {
            if (!root.has(section)) {
                errors.add("/" + section + ": required section is missing");
            }
        }
        root.fieldNames().forEachRemaining(field -> {
            if (!REQUIRED_SECTIONS.contains(field) && !OPTIONAL_SECTIONS.contains(field)) {
                errors.add("/" + field + ": unknown critical section is not allowed");
            }
        });

        JsonNode packageNode = root.path("package");
        requireText(packageNode, "schemaVersion", "/package/schemaVersion", errors);
        if (packageNode.has("schemaVersion") && !SUPPORTED_SCHEMA_VERSION.equals(packageNode.path("schemaVersion").asText())) {
            errors.add("/package/schemaVersion: unsupported schema version");
        }
        String minVersion = textAt(packageNode, "applicationCompatibility", "minVersion");
        String maxExclusiveVersion = textAt(packageNode, "applicationCompatibility", "maxExclusiveVersion");
        if (!SEMVER.matcher(applicationVersion).matches()) {
            errors.add("/applicationVersion: application version must be semver");
        } else if (minVersion != null && maxExclusiveVersion != null) {
            if (compareSemver(applicationVersion, minVersion) < 0 || compareSemver(applicationVersion, maxExclusiveVersion) >= 0) {
                errors.add("/package/applicationCompatibility: application " + applicationVersion + " is outside supported range [" + minVersion + ", " + maxExclusiveVersion + ")");
            }
        }

        validateRequiredApprovalAndPolicy(root, errors);
        validateIntegrationRefs(root.path("integrations").path("providers"), errors);
        validatePluginRefs(root.path("pluginAllowList").path("plugins"), errors);
        validateSecretRef(root.path("assetStorage").path("encryptionKeyRef"), "/assetStorage/encryptionKeyRef", errors);

        if (!errors.isEmpty()) {
            throw new ChurchConfigValidationException(errors);
        }
    }

    private static void validateRequiredApprovalAndPolicy(JsonNode root, List<String> errors) {
        if (!root.path("policies").path("recommendationPolicy").path("requireApprovedOnly").asBoolean(false)) {
            errors.add("/policies/recommendationPolicy/requireApprovedOnly: approved-only recommendations are mandatory");
        }
        if (!root.path("policies").path("recommendationPolicy").path("requireDatasetReferences").asBoolean(false)) {
            errors.add("/policies/recommendationPolicy/requireDatasetReferences: dataset references are mandatory");
        }
        if (!root.path("approvalGates").path("requireLyricsProvenance").asBoolean(false)) {
            errors.add("/approvalGates/requireLyricsProvenance: lyrics provenance approval gate is mandatory");
        }
        if (!root.path("approvalGates").path("requireDoctrinalReview").asBoolean(false)) {
            errors.add("/approvalGates/requireDoctrinalReview: doctrinal review approval gate is mandatory");
        }
    }

    private static void validateIntegrationRefs(JsonNode providers, List<String> errors) {
        if (!providers.isArray()) {
            errors.add("/integrations/providers: providers must be an array");
            return;
        }
        for (int index = 0; index < providers.size(); index++) {
            JsonNode provider = providers.get(index);
            String ref = provider.path("ref").asText("");
            if (!INTEGRATION_REF.matcher(ref).matches()) {
                errors.add("/integrations/providers/" + index + "/ref: malformed integration reference");
            }
            JsonNode secretRef = provider.path("secretRef");
            if (!secretRef.isMissingNode()) {
                validateSecretRef(secretRef, "/integrations/providers/" + index + "/secretRef", errors);
            }
        }
    }

    private static void validatePluginRefs(JsonNode plugins, List<String> errors) {
        if (!plugins.isArray()) {
            errors.add("/pluginAllowList/plugins: plugins must be an array");
            return;
        }
        for (int index = 0; index < plugins.size(); index++) {
            String ref = plugins.get(index).path("ref").asText("");
            if (!PLUGIN_REF.matcher(ref).matches()) {
                errors.add("/pluginAllowList/plugins/" + index + "/ref: malformed plugin reference");
            }
        }
    }

    private static void validateSecretRef(JsonNode secretRef, String path, List<String> errors) {
        if (!secretRef.isTextual() || !SECRET_REF.matcher(secretRef.asText()).matches()) {
            errors.add(path + ": must be a secret reference, not a plaintext secret");
        }
    }

    private static void requireText(JsonNode node, String field, String path, List<String> errors) {
        if (!node.path(field).isTextual()) {
            errors.add(path + ": required text field is missing");
        }
    }

    private static String textAt(JsonNode node, String objectField, String field) {
        JsonNode value = node.path(objectField).path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static int compareSemver(String left, String right) {
        int[] leftParts = semverCore(left);
        int[] rightParts = semverCore(right);
        for (int index = 0; index < leftParts.length; index++) {
            int comparison = Integer.compare(leftParts[index], rightParts[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int[] semverCore(String value) {
        String[] parts = value.split("[-+]", 2)[0].split("\\.");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
