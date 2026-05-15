package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.api.dto.GenerateSetlistRequest;
import com.cadentia.api.dto.SetlistProposalResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetlistServiceTest {

    private final SetlistService setlistService = new SetlistService();

    @Test
    void generateDoesNotInventSongsBeforeCatalogRetrievalExists() {
        // Arrange
        GenerateSetlistRequest request = new GenerateSetlistRequest(
                "Psalm 100",
                List.of("thanksgiving"),
                new GenerateSetlistRequest.Counts(10, 5),
                new GenerateSetlistRequest.KeyPolicy(true, true, 2),
                new GenerateSetlistRequest.TempoPolicy(12));

        // Act
        SetlistProposalResponse response = setlistService.generate(request);

        // Assert
        assertThat(response.status()).isEqualTo("PENDING_CATALOG_IMPLEMENTATION");
        assertThat(response.auditMessages())
                .contains("No songs were selected because catalog retrieval is not implemented yet.");
    }
}
