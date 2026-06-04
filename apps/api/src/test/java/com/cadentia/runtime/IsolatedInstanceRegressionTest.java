package com.cadentia.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import com.cadentia.config.ChurchConfigPackageValidator;
import com.cadentia.reng.ApprovalGateSummary;
import com.cadentia.reng.RecommendableArrangement;
import com.cadentia.reng.RecommendationTag;
import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class IsolatedInstanceRegressionTest {
    private static final Path FIXTURE_ROOT = Path.of(
            "..",
            "..",
            "packages",
            "intent-contracts",
            "fixtures",
            "church-config",
            "v1",
            "valid");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChurchConfigPackageValidator validator = new ChurchConfigPackageValidator();

    @Test
    void packageValidationAndStartupConfigurationStayDistinctForTwoIsolatedInstances() throws IOException {
        // Arrange
        JsonNode riverPackage = readPackage("river-city-isolation-package.json");
        JsonNode hillsidePackage = readPackage("hillside-isolation-package.json");

        // Act
        validator.validate(riverPackage, "0.1.0");
        validator.validate(hillsidePackage, "0.1.0");
        InstanceConfiguration river = InstanceConfiguration.fromPackage(riverPackage);
        InstanceConfiguration hillside = InstanceConfiguration.fromPackage(hillsidePackage);

        // Assert
        assertThat(river.instanceId()).isEqualTo("river-city-isolation");
        assertThat(hillside.instanceId()).isEqualTo("hillside-isolation");
        assertThat(river.recommendationPolicy().praiseCount()).isEqualTo(2);
        assertThat(hillside.recommendationPolicy().praiseCount()).isEqualTo(1);
        assertThat(river.recommendationPolicy().tempoPolicy().maxJumpBpm()).isEqualTo(12);
        assertThat(hillside.recommendationPolicy().tempoPolicy().maxJumpBpm()).isEqualTo(6);
        assertThat(river.scoringProfile().version()).isEqualTo("river-theme-first");
        assertThat(hillside.scoringProfile().version()).isEqualTo("hills-scripture-first");
        assertThat(riverPackage.path("branding").path("primaryColor").asText())
                .isNotEqualTo(hillsidePackage.path("branding").path("primaryColor").asText());
        assertThat(riverPackage.path("extensions").path("deterministicFixtureCatalog")).hasSize(2);
        assertThat(hillsidePackage.path("extensions").path("deterministicFixtureCatalog")).hasSize(2);
    }

    @Test
    void normalApplicationPathsCannotReadAnotherInstancesPrivateDataOrResourceHandles() throws IOException {
        // Arrange
        FixtureInstance river = FixtureInstance.from(readPackage("river-city-isolation-package.json"));
        FixtureInstance hillside = FixtureInstance.from(readPackage("hillside-isolation-package.json"));

        // Act / Assert
        assertThat(river.privateSongTitle("river-song-dawn")).isEqualTo("River Praise of Dawn");
        assertThat(hillside.privateSongTitle("hills-song-mercy")).isEqualTo("Hills Mercy Response");
        assertCrossInstanceReadFails(river, "hills-song-mercy", "hills-arrangement-mercy", hillside.privateServiceId(),
                hillside.privateAssetPath(), hillside.privatePersonId(), hillside.privateCredentialRef(),
                hillside.privateUsageHistoryId());
        assertCrossInstanceReadFails(hillside, "river-song-dawn", "river-arrangement-dawn", river.privateServiceId(),
                river.privateAssetPath(), river.privatePersonId(), river.privateCredentialRef(),
                river.privateUsageHistoryId());
    }

    @Test
    void recommendationsUseOnlyLocalApprovedCatalogPolicyScoringProfileAndServiceContext() throws IOException {
        // Arrange
        FixtureInstance river = FixtureInstance.from(readPackage("river-city-isolation-package.json"));
        FixtureInstance hillside = FixtureInstance.from(readPackage("hillside-isolation-package.json"));

        // Act
        List<String> riverRecommendations = river.recommend("Psalm 100", List.of("adoration"));
        List<String> hillsideRecommendations = hillside.recommend("Romans 8", List.of("mercy"));

        // Assert
        assertThat(riverRecommendations).containsExactly("River Praise of Dawn");
        assertThat(hillsideRecommendations).containsExactly("Hills Mercy Response");
        assertThat(riverRecommendations)
                .doesNotContain("River Starter Pending", "Hills Mercy Response", "Hills Starter Pending");
        assertThat(hillsideRecommendations)
                .doesNotContain("Hills Starter Pending", "River Praise of Dawn", "River Starter Pending");
        assertThat(river.lastRecommendationAudit()).contains(
                "instance=river-city-isolation",
                "profile=river-theme-first",
                "counts=2/1",
                "service=river-city-isolation-sunday-am");
        assertThat(hillside.lastRecommendationAudit()).contains(
                "instance=hillside-isolation",
                "profile=hills-scripture-first",
                "counts=1/2",
                "service=hillside-isolation-sunday-am");
    }

    @Test
    void pluginsIntegrationsAssetsCachesEventsAndTelemetryUseInstanceLocalSettings() throws IOException {
        // Arrange
        FixtureInstance river = FixtureInstance.from(readPackage("river-city-isolation-package.json"));
        FixtureInstance hillside = FixtureInstance.from(readPackage("hillside-isolation-package.json"));

        // Act / Assert
        assertThat(river.pluginRegistry().requireEnabledPlugin("@cadentia/export-csv@1.0.0").permissions())
                .containsExactly("read_catalog", "write_exports");
        assertThat(hillside.pluginRegistry().requireEnabledPlugin("@cadentia/planning-export@1.1.0").permissions())
                .containsExactly("read_service_plans", "write_exports");
        assertThatThrownBy(() -> river.pluginRegistry().requireEnabledPlugin("@cadentia/planning-export@1.1.0"))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("allow-list");
        assertThatThrownBy(() -> hillside.integrationRegistry().requireConfiguredIntegration("telegram.bot.v1"))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("disabled or missing");
        assertThat(river.integrationRegistry().requireConfiguredIntegration("telegram.bot.v1").secretRef())
                .isEqualTo("env:RIVER_TELEGRAM_TOKEN");
        assertThat(hillside.integrationRegistry().requireConfiguredIntegration("email.digest.v1").secretRef())
                .isEqualTo("env:HILLS_EMAIL_TOKEN");
        assertThat(river.resources().assetObjectKey("slides/opening.png"))
                .isEqualTo("river-city-isolation/slides/opening.png");
        assertThat(hillside.resources().assetObjectKey("slides/opening.png"))
                .isEqualTo("hillside-isolation/slides/opening.png");
        assertThat(river.resources().cacheKey("recommendation:latest"))
                .isEqualTo("river-city-isolation:recommendation:latest");
        assertThat(hillside.resources().eventStream("recommendation-events"))
                .isEqualTo("hillside-isolation.recommendation-events");
        assertThat(river.resources().backgroundJobQueue("imports"))
                .isEqualTo("river-city-isolation.jobs.imports");
        assertThat(hillside.telemetry().event("recommendation.generated", Map.of("module", "recommendation")).instanceId())
                .isEqualTo("hillside-isolation");
        assertThatThrownBy(() -> river.telemetry().event("unsafe", Map.of("privateSongTitle", "River Praise of Dawn")))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("low-cardinality");
    }

    private JsonNode readPackage(String fixtureName) throws IOException {
        return objectMapper.readTree(Files.readString(FIXTURE_ROOT.resolve(fixtureName)));
    }

    private static void assertCrossInstanceReadFails(
            FixtureInstance instance,
            String songId,
            String arrangementId,
            String serviceId,
            String assetPath,
            String personId,
            String credentialRef,
            String usageHistoryId) {
        assertThatThrownBy(() -> instance.privateSongTitle(songId))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("song is not available");
        assertThatThrownBy(() -> instance.privateArrangementTitle(arrangementId))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("arrangement is not available");
        assertThatThrownBy(() -> instance.privateServiceName(serviceId))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("service is not available");
        assertThatThrownBy(() -> instance.privateAsset(assetPath))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("asset is not available");
        assertThatThrownBy(() -> instance.privatePersonName(personId))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("person is not available");
        assertThatThrownBy(() -> instance.privateCredentialRef(credentialRef))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("credential is not available");
        assertThatThrownBy(() -> instance.privateUsageHistory(usageHistoryId))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("usage history is not available");
    }

    private record FixtureInstance(
            InstanceConfiguration configuration,
            List<FixtureSong> songs,
            Map<String, String> services,
            Map<String, String> assets,
            Map<String, String> people,
            Map<String, String> credentials,
            Map<String, String> usageHistory,
            PluginRegistry pluginRegistry,
            IntegrationRegistry integrationRegistry,
            RuntimeResourceIdentifiers resources,
            TelemetryEventFactory telemetry,
            CandidateFeatureScorer scorer,
            StringBuilder recommendationAudit) {

        static FixtureInstance from(JsonNode root) {
            InstanceConfiguration configuration = InstanceConfiguration.fromPackage(root);
            StaticInstanceConfigurationProvider provider = new StaticInstanceConfigurationProvider(configuration);
            List<FixtureSong> songs = readFixtureSongs(root.path("extensions").path("deterministicFixtureCatalog"));
            String serviceId = configuration.instanceId() + "-sunday-am";
            return new FixtureInstance(
                    configuration,
                    songs,
                    Map.of(serviceId, configuration.instanceId() + " Sunday AM Fixture Service"),
                    Map.of("assets/private/" + configuration.instanceId() + "/stage-layout.svg", "fixture svg asset"),
                    Map.of(configuration.instanceId() + "-leader", "Fixture Leader for " + configuration.instanceId()),
                    Map.of(
                            "env:" + configuration.instanceId().replace('-', '_').toUpperCase() + "_PRIVATE_TOKEN",
                            "configured"),
                    Map.of(configuration.instanceId() + "-usage-history", "recommended locally once"),
                    new PluginRegistry(provider),
                    new IntegrationRegistry(provider),
                    new RuntimeResourceIdentifiers(provider),
                    new TelemetryEventFactory(provider),
                    new CandidateFeatureScorer(),
                    new StringBuilder());
        }

        List<String> recommend(String verseText, List<String> themeHints) {
            ScoringRequest request = new ScoringRequest(
                    verseText,
                    themeHints,
                    configuration.recommendationPolicy().praiseCount(),
                    configuration.recommendationPolicy().worshipCount(),
                    new ScoringRequest.KeyPolicy(
                            configuration.recommendationPolicy().keyPolicy().preferSameKey(),
                            configuration.recommendationPolicy().keyPolicy().allowRelativeMajorMinor(),
                            configuration.recommendationPolicy().keyPolicy().maxKeyCenters()),
                    new ScoringRequest.TempoPolicy(configuration.recommendationPolicy().tempoPolicy().maxJumpBpm()),
                    null,
                    "en",
                    List.of(),
                    false,
                    new ScoringRequest.DefaultsApplied(false, false, false, false));
            recommendationAudit.setLength(0);
            recommendationAudit.append("instance=").append(configuration.instanceId())
                    .append(" profile=").append(configuration.scoringProfile().version())
                    .append(" counts=").append(request.praiseCount()).append("/").append(request.worshipCount())
                    .append(" service=").append(privateServiceId());
            return scorer.scoreCandidates(
                            songs.stream()
                                    .filter(FixtureSong::approved)
                                    .map(FixtureSong::toArrangement)
                                    .toList(),
                            request,
                            scoringProfileForRecommendation())
                    .stream()
                    .map(score -> score.candidate().title())
                    .toList();
        }

        String lastRecommendationAudit() {
            return recommendationAudit.toString();
        }

        String privateSongTitle(String id) {
            return songs.stream()
                    .filter(song -> song.songId().equals(id))
                    .findFirst()
                    .map(FixtureSong::title)
                    .orElseThrow(() -> new RuntimeModuleAccessException("Private song is not available to this instance: " + id));
        }

        String privateArrangementTitle(String id) {
            return songs.stream()
                    .filter(song -> song.arrangementId().equals(id))
                    .findFirst()
                    .map(FixtureSong::title)
                    .orElseThrow(() -> new RuntimeModuleAccessException(
                            "Private arrangement is not available to this instance: " + id));
        }

        String privateServiceName(String id) {
            return readLocal(services, id, "service");
        }

        String privateAsset(String path) {
            return readLocal(assets, path, "asset");
        }

        String privatePersonName(String id) {
            return readLocal(people, id, "person");
        }

        String privateCredentialRef(String ref) {
            return readLocal(credentials, ref, "credential");
        }

        String privateUsageHistory(String id) {
            return readLocal(usageHistory, id, "usage history");
        }

        String privateServiceId() {
            return services.keySet().iterator().next();
        }

        String privateAssetPath() {
            return assets.keySet().iterator().next();
        }

        String privatePersonId() {
            return people.keySet().iterator().next();
        }

        String privateCredentialRef() {
            return credentials.keySet().iterator().next();
        }

        String privateUsageHistoryId() {
            return usageHistory.keySet().iterator().next();
        }

        private ScoringProfile scoringProfileForRecommendation() {
            Map<String, Double> packageWeights = configuration.scoringProfile().componentWeights();
            return new ScoringProfile(
                    configuration.scoringProfile().version(),
                    Map.of(
                            CandidateFeatureScorer.THEME_MATCH, packageWeights.getOrDefault("themeFit", 0.0d),
                            CandidateFeatureScorer.SCRIPTURE_MATCH, packageWeights.getOrDefault("scriptureFit", 0.0d),
                            CandidateFeatureScorer.MUSICAL_FIT, packageWeights.getOrDefault("musicalFit", 0.0d),
                            CandidateFeatureScorer.ENERGY_FIT, packageWeights.getOrDefault("energyFit", 0.0d),
                            CandidateFeatureScorer.METADATA_CONFIDENCE, packageWeights.getOrDefault("familiarity", 0.0d)),
                    configuration.scoringProfile().deterministicTieBreakOrder());
        }

        private static String readLocal(Map<String, String> values, String key, String type) {
            String value = values.get(key);
            if (value == null) {
                throw new RuntimeModuleAccessException(
                        "Private " + type + " is not available to this instance: " + key);
            }
            return value;
        }
    }

    private record FixtureSong(
            String songId,
            String arrangementId,
            String title,
            boolean approved,
            boolean seeded,
            List<String> themes,
            List<String> scriptureTags,
            String key,
            int bpm,
            int energy) {

        RecommendableArrangement toArrangement() {
            List<RecommendationTag> matchedTags = matchedTags();
            return new RecommendableArrangement(
                    stableUuid(arrangementId),
                    stableUuid(songId),
                    stableUuid(songId + "-lyrics"),
                    title,
                    "en",
                    key,
                    KeyMode.MAJOR,
                    bpm,
                    "4/4",
                    energy,
                    themes,
                    matchedTags,
                    matchedTags,
                    approvalSummary());
        }

        private List<RecommendationTag> matchedTags() {
            List<RecommendationTag> themeTags = themes.stream()
                    .map(slug -> new RecommendationTag(
                            stableUuid(arrangementId + ":theme:" + slug), TagType.THEME, slug, slug))
                    .toList();
            List<RecommendationTag> scripture = scriptureTags.stream()
                    .map(slug -> new RecommendationTag(
                            stableUuid(arrangementId + ":scripture:" + slug), TagType.SCRIPTURE, slug, slug))
                    .toList();
            return Stream.concat(themeTags.stream(), scripture.stream()).toList();
        }

        private ApprovalGateSummary approvalSummary() {
            ApprovalStatus status = approved ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING;
            return new ApprovalGateSummary(status, status, status, status, status, status, status, status);
        }
    }

    private static List<FixtureSong> readFixtureSongs(JsonNode catalog) {
        return StreamSupport.stream(catalog.spliterator(), false)
                .map(node -> new FixtureSong(
                        node.path("songId").asText(),
                        node.path("arrangementId").asText(),
                        node.path("title").asText(),
                        node.path("approved").asBoolean(),
                        node.path("seeded").asBoolean(),
                        strings(node.path("themes")),
                        strings(node.path("scriptureTags")),
                        node.path("key").asText(),
                        node.path("bpm").asInt(),
                        node.path("energy").asInt()))
                .toList();
    }

    private static List<String> strings(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
