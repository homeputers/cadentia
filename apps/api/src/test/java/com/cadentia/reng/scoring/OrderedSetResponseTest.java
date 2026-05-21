package com.cadentia.reng.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderedSetResponseTest {

    @Test
    void ofPropagatesProfileVersionAndDeterministicOrderingRules() {
        // Arrange
        ScoringProfile profile = new ScoringProfile(
                "v1.0.0",
                Map.of("theme_match", 0.4),
                List.of("total_score", "theme_priority", "arrangement_id"));
        OrderedSetItem item = new OrderedSetItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                List.of(),
                List.of(new ScoringComponentScore("theme_match", 0.9, 0.36)),
                0.36,
                null);

        // Act
        OrderedSetResponse response = OrderedSetResponse.of(profile, "snapshot-2026-05-17", List.of(item), 0.36);

        // Assert
        assertThat(response.scoringProfileVersion()).isEqualTo("v1.0.0");
        assertThat(response.candidateSnapshotVersion()).isEqualTo("snapshot-2026-05-17");
        assertThat(response.deterministicOrderingRules())
                .containsExactly("total_score", "theme_priority", "arrangement_id");
        assertThat(response.items()).hasSize(1);
    }
}
