package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringRequestFactoryTest {

    private final ScoringRequestFactory factory = new ScoringRequestFactory();

    @Test
    void fromValidatedRequestAppliesInstanceDefaults() {
        // Arrange
        InstanceConfiguration configuration = new InstanceConfiguration(
                "river-city-worship",
                "1.0.0",
                new InstanceConfiguration.Modules(true, false, false, false, false, false),
                new InstanceConfiguration.RecommendationPolicy(
                        4,
                        3,
                        new InstanceConfiguration.KeyPolicy(false, true, 1),
                        new InstanceConfiguration.TempoPolicy(9),
                        true,
                        true),
                InstanceConfiguration.localDevelopment(
                                "river-city-worship",
                                "local",
                                "bucket",
                                "river-city-worship",
                                "env:KEY_REF",
                                "cache",
                                "events",
                                List.of("events.audit-events"))
                        .scoringProfile(),
                List.of(),
                List.of(),
                new InstanceConfiguration.AssetStorage("local", "bucket", "river-city-worship", "env:KEY_REF"),
                new InstanceConfiguration.RuntimeNamespaces("river-city-worship", "cache", "events", List.of("events.audit-events")),
                new InstanceConfiguration.TelemetryExport(true, "river-city-worship", false, "none", null));
        ScoringRequestFactory instanceFactory = new ScoringRequestFactory(new StaticInstanceConfigurationProvider(configuration));
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 24")
                .themeHints(List.of("holiness"));

        // Act
        ScoringRequest scoringRequest = instanceFactory.fromValidatedRequest(request);

        // Assert
        assertThat(scoringRequest.praiseCount()).isEqualTo(4);
        assertThat(scoringRequest.worshipCount()).isEqualTo(3);
        assertThat(scoringRequest.keyPolicy()).isEqualTo(new ScoringRequest.KeyPolicy(false, true, 1));
        assertThat(scoringRequest.tempoPolicy()).isEqualTo(new ScoringRequest.TempoPolicy(9));
        assertThat(scoringRequest.energyArc()).isNull();
        assertThat(scoringRequest.language()).isEqualTo("en");
        assertThat(scoringRequest.defaultsApplied())
                .isEqualTo(new ScoringRequest.DefaultsApplied(true, true, true, true));
    }

    @Test
    void fromValidatedRequestMapsExplicitPolicies() {
        // Arrange
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Romans 12")
                .themeHints(List.of("surrender"))
                .counts(new SetlistCounts().praise(6).worship(4))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(false)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(3))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(8))
                .energyArc(GenerateSetlistRequest.EnergyArcEnum.RISING)
                .language("pt")
                .excludedSongs(List.of("song-1", "song-2"));

        // Act
        ScoringRequest scoringRequest = factory.fromValidatedRequest(request);

        // Assert
        assertThat(scoringRequest.praiseCount()).isEqualTo(6);
        assertThat(scoringRequest.worshipCount()).isEqualTo(4);
        assertThat(scoringRequest.keyPolicy()).isEqualTo(new ScoringRequest.KeyPolicy(false, true, 3));
        assertThat(scoringRequest.tempoPolicy()).isEqualTo(new ScoringRequest.TempoPolicy(8));
        assertThat(scoringRequest.energyArc()).isEqualTo("rising");
        assertThat(scoringRequest.language()).isEqualTo("pt");
        assertThat(scoringRequest.excludedSongIds()).containsExactly("song-1", "song-2");
        assertThat(scoringRequest.defaultsApplied())
                .isEqualTo(new ScoringRequest.DefaultsApplied(false, false, false, false));
    }

    @Test
    void fromValidatedRequestCarriesScriptureReferencesIntoScoringRequest() {
        // Arrange
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Philippians 4:13")
                .scriptureReferences(List.of("Philippians 4:13", "Isaiah 41:10"));

        // Act
        ScoringRequest scoringRequest = factory.fromValidatedRequest(request);

        // Assert
        assertThat(scoringRequest.verseText()).isEqualTo("Philippians 4:13");
        assertThat(scoringRequest.scriptureReferences())
                .containsExactly("Philippians 4:13", "Isaiah 41:10");
    }

    @Test
    void fromValidatedRequestDefaultsMissingScriptureReferencesToEmptyList() {
        // Arrange
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 24");

        // Act
        ScoringRequest scoringRequest = factory.fromValidatedRequest(request);

        // Assert
        assertThat(scoringRequest.scriptureReferences()).isEmpty();
    }
}
