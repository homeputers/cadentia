package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetlistServiceTest {

    private final SetlistService setlistService = new SetlistService();

    @Test
    void generateDoesNotInventSongsBeforeCatalogRetrievalExists() {
        // Arrange
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .themeHints(List.of("thanksgiving"))
                .counts(new SetlistCounts().praise(10).worship(5))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(true)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(2))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(12));

        // Act
        SetlistProposalResponse response = setlistService.generate(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("PENDING_CATALOG_IMPLEMENTATION");
        assertThat(response.getAuditMessages())
                .contains("No songs were selected because catalog retrieval is not implemented yet.");
    }
}
