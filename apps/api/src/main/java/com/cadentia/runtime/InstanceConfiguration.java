package com.cadentia.runtime;

import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringProfileLifecycle;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

public record InstanceConfiguration(
        String instanceId,
        String packageVersion,
        Modules modules,
        RecommendationPolicy recommendationPolicy,
        ScoringProfile scoringProfile,
        List<IntegrationProvider> integrations,
        List<PluginDefinition> plugins,
        AssetStorage assetStorage,
        RuntimeNamespaces namespaces,
        TelemetryExport telemetryExport) {

    public InstanceConfiguration {
        integrations = integrations == null ? List.of() : List.copyOf(integrations);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }

    public static InstanceConfiguration localDevelopment(
            String instanceId,
            String assetProvider,
            String assetBucket,
            String assetNamespace,
            String assetEncryptionKeyRef,
            String cacheNamespace,
            String eventNamespace,
            List<String> eventStreams) {
        return new InstanceConfiguration(
                instanceId,
                "local-development",
                new Modules(true, false, false, false, false, false),
                new RecommendationPolicy(
                        10,
                        5,
                        new KeyPolicy(true, true, 2),
                        new TempoPolicy(12),
                        true,
                        true),
                new ScoringProfile(
                        "local-development",
                        Map.of("themeFit", 3.0, "scriptureFit", 3.0, "musicalFit", 2.0),
                        List.of("title", "arrangement_id"),
                        ScoringProfileLifecycle.active()),
                List.of(),
                List.of(),
                new AssetStorage(assetProvider, assetBucket, assetNamespace, assetEncryptionKeyRef),
                new RuntimeNamespaces(assetNamespace, cacheNamespace, eventNamespace, eventStreams),
                new TelemetryExport(true, instanceId, false, "none", null));
    }

    public static InstanceConfiguration fromPackage(JsonNode root) {
        JsonNode recommendationPolicy = root.path("policies").path("recommendationPolicy");
        JsonNode keyPolicy = recommendationPolicy.path("keyPolicy");
        JsonNode tempoPolicy = recommendationPolicy.path("tempoPolicy");
        JsonNode activeProfile = activeProfile(root.path("scoringProfiles"));
        JsonNode assetStorage = root.path("assetStorage");
        JsonNode observability = root.path("observability");
        return new InstanceConfiguration(
                root.path("instance").path("instanceId").asText(),
                root.path("package").path("packageVersion").asText(),
                modules(root.path("modules")),
                new RecommendationPolicy(
                        recommendationPolicy.path("counts").path("praise").asInt(10),
                        recommendationPolicy.path("counts").path("worship").asInt(5),
                        new KeyPolicy(
                                keyPolicy.path("preferSameKey").asBoolean(true),
                                keyPolicy.path("allowRelativeMajorMinor").asBoolean(true),
                                keyPolicy.path("maxKeyCenters").asInt(2)),
                        new TempoPolicy(tempoPolicy.path("maxJumpBpm").asInt(12)),
                        recommendationPolicy.path("requireApprovedOnly").asBoolean(false),
                        recommendationPolicy.path("requireDatasetReferences").asBoolean(false)),
                scoringProfile(activeProfile),
                integrations(root.path("integrations").path("providers")),
                plugins(root.path("pluginAllowList").path("plugins")),
                new AssetStorage(
                        assetStorage.path("provider").asText(),
                        assetStorage.path("bucketOrContainer").asText(),
                        assetStorage.path("namespacePrefix").asText(),
                        assetStorage.path("encryptionKeyRef").asText()),
                new RuntimeNamespaces(
                        assetStorage.path("namespacePrefix").asText(),
                        observability.path("metrics").path("namespace").asText(),
                        observability.path("metrics").path("namespace").asText(),
                        List.of(observability.path("metrics").path("namespace").asText() + ".audit-events",
                                observability.path("metrics").path("namespace").asText() + ".recommendation-events")),
                telemetryExport(observability));
    }

    public Optional<IntegrationProvider> enabledIntegration(String ref) {
        return integrations.stream().filter(provider -> provider.enabled() && provider.ref().equals(ref)).findFirst();
    }

    public Optional<PluginDefinition> enabledPlugin(String ref) {
        return plugins.stream().filter(plugin -> plugin.enabled() && plugin.ref().equals(ref)).findFirst();
    }

    private static Modules modules(JsonNode modules) {
        return new Modules(
                modules.path("recommendation").path("enabled").asBoolean(false),
                modules.path("catalogImport").path("enabled").asBoolean(false),
                modules.path("servicePlanning").path("enabled").asBoolean(false),
                modules.path("teamScheduling").path("enabled").asBoolean(false),
                modules.path("feedbackTuning").path("enabled").asBoolean(false),
                modules.path("externalMessaging").path("enabled").asBoolean(false));
    }

    private static JsonNode activeProfile(JsonNode scoringProfiles) {
        String activeProfileId = scoringProfiles.path("activeProfile").asText();
        return StreamSupport.stream(scoringProfiles.path("profiles").spliterator(), false)
                .filter(profile -> activeProfileId.equals(profile.path("id").asText()))
                .findFirst()
                .orElse(scoringProfiles.path("profiles").path(0));
    }

    private static ScoringProfile scoringProfile(JsonNode profile) {
        // JsonNode value spliterator does not expose field names, so iterate fields explicitly.
        java.util.LinkedHashMap<String, Double> orderedWeights = new java.util.LinkedHashMap<>();
        profile.path("weights").fields().forEachRemaining(entry -> orderedWeights.put(entry.getKey(), entry.getValue().asDouble()));
        List<String> tieBreakers = StreamSupport.stream(profile.path("deterministicTieBreakers").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        return new ScoringProfile(profile.path("id").asText(), orderedWeights, tieBreakers, ScoringProfileLifecycle.active());
    }

    private static List<IntegrationProvider> integrations(JsonNode providers) {
        return StreamSupport.stream(providers.spliterator(), false)
                .map(provider -> new IntegrationProvider(
                        provider.path("ref").asText(),
                        provider.path("type").asText(),
                        provider.path("enabled").asBoolean(false),
                        textOrNull(provider.path("secretRef")),
                        textOrNull(provider.path("endpoint"))))
                .toList();
    }

    private static List<PluginDefinition> plugins(JsonNode plugins) {
        return StreamSupport.stream(plugins.spliterator(), false)
                .map(plugin -> new PluginDefinition(
                        plugin.path("ref").asText(),
                        plugin.path("enabled").asBoolean(false),
                        StreamSupport.stream(plugin.path("permissions").spliterator(), false).map(JsonNode::asText).toList()))
                .toList();
    }

    private static TelemetryExport telemetryExport(JsonNode observability) {
        JsonNode exports = observability.path("exports");
        return new TelemetryExport(
                observability.path("metrics").path("enabled").asBoolean(false),
                observability.path("metrics").path("namespace").asText(),
                observability.path("traces").path("enabled").asBoolean(false),
                observability.path("traces").path("exporter").asText("none"),
                exports.path("enabled").asBoolean(false) ? textOrNull(exports.path("destinationRef")) : null);
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    public record Modules(
            boolean recommendation,
            boolean catalogImport,
            boolean servicePlanning,
            boolean teamScheduling,
            boolean feedbackTuning,
            boolean externalMessaging) {
    }

    public record RecommendationPolicy(
            int praiseCount,
            int worshipCount,
            KeyPolicy keyPolicy,
            TempoPolicy tempoPolicy,
            boolean requireApprovedOnly,
            boolean requireDatasetReferences) {
    }

    public record KeyPolicy(boolean preferSameKey, boolean allowRelativeMajorMinor, int maxKeyCenters) {
    }

    public record TempoPolicy(int maxJumpBpm) {
    }

    public record IntegrationProvider(String ref, String type, boolean enabled, String secretRef, String endpoint) {
    }

    public record PluginDefinition(String ref, boolean enabled, List<String> permissions) {
        public PluginDefinition {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    public record AssetStorage(String provider, String bucketOrContainer, String namespacePrefix, String encryptionKeyRef) {
    }

    public record RuntimeNamespaces(String assetNamespace, String cacheNamespace, String eventNamespace, List<String> eventStreams) {
        public RuntimeNamespaces {
            eventStreams = eventStreams == null ? List.of() : List.copyOf(eventStreams);
        }
    }

    public record TelemetryExport(
            boolean metricsEnabled,
            String metricsNamespace,
            boolean tracesEnabled,
            String traceExporter,
            String destinationRef) {
    }
}
