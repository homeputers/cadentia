package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.intent.Counts;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatedSetlistRequestMapperTest {

    private final ValidatedSetlistRequestMapper mapper = new ValidatedSetlistRequestMapper();

    @Test
    void toGenerateSetlistRequestMapsOnlyValidatedIntentSlots() {
        // Arrange
        GenerateSetlistIntent intent = new GenerateSetlistIntent("v1", new GenerateSetlistSlots(
                "Psalm 100",
                List.of("Psalm 100"),
                List.of("thanksgiving"),
                new Counts(4, 2),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                "Spanish",
                "rising",
                List.of("Do Not Use This Song"),
                "opening"));

        // Act
        GenerateSetlistRequest request = mapper.toGenerateSetlistRequest(intent);

        // Assert
        assertThat(request.getVerseText()).isEqualTo("Psalm 100");
        assertThat(request.getScriptureReferences()).containsExactly("Psalm 100");
        assertThat(request.getThemeHints()).containsExactly("thanksgiving");
        assertThat(request.getCounts().getPraise()).isEqualTo(4);
        assertThat(request.getCounts().getWorship()).isEqualTo(2);
        assertThat(request.getKeyPolicy().getPreferSameKey()).isTrue();
        assertThat(request.getKeyPolicy().getAllowRelativeMajorMinor()).isTrue();
        assertThat(request.getKeyPolicy().getMaxKeyCenters()).isEqualTo(2);
        assertThat(request.getTempoPolicy().getMaxJumpBpm()).isEqualTo(12);
        assertThat(request.getLanguage()).isEqualTo("Spanish");
        assertThat(request.getEnergyArc()).isEqualTo(GenerateSetlistRequest.EnergyArcEnum.RISING);
        assertThat(request.getExcludedSongs()).containsExactly("Do Not Use This Song");
        assertThat(request.getServiceMoment()).isEqualTo(GenerateSetlistRequest.ServiceMomentEnum.OPENING);
    }

    @Test
    void toGenerateSetlistRequestPreservesNamedSongsOnlyAsExclusions() {
        // Arrange
        GenerateSetlistIntent intent = new GenerateSetlistIntent("v1", new GenerateSetlistSlots(
                "",
                List.of(),
                List.of("grace"),
                new Counts(10, 5),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                null,
                null,
                List.of("Amazing Grace"),
                null));

        // Act
        GenerateSetlistRequest request = mapper.toGenerateSetlistRequest(intent);

        // Assert
        assertThat(request.getExcludedSongs()).containsExactly("Amazing Grace");
        assertThat(request.toString()).doesNotContain("selectedSongs");
    }
}
