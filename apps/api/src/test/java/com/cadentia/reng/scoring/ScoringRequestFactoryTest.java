package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.TempoPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringRequestFactoryTest {

    private final ScoringRequestFactory factory = new ScoringRequestFactory();

    @Test
    void fromValidatedRequestAppliesDefaults() {
        // Arrange
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 24")
                .themeHints(List.of("holiness"));

        // Act
        ScoringRequest scoringRequest = factory.fromValidatedRequest(request);

        // Assert
        assertThat(scoringRequest.praiseCount()).isEqualTo(10);
        assertThat(scoringRequest.worshipCount()).isEqualTo(5);
        assertThat(scoringRequest.keyPolicy()).isEqualTo(new ScoringRequest.KeyPolicy(true, true, 2));
        assertThat(scoringRequest.tempoPolicy()).isEqualTo(new ScoringRequest.TempoPolicy(12));
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
}
