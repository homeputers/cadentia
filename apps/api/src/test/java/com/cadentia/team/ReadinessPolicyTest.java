package com.cadentia.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.serviceplan.ServicePlanModels.ReadinessStatus;
import com.cadentia.team.ReadinessModels.ReadinessAudience;
import com.cadentia.team.ReadinessModels.ReadinessNoteRecord;
import com.cadentia.team.ReadinessModels.ReadinessPrivacyClassification;
import com.cadentia.team.ReadinessModels.ReadinessScopeType;
import com.cadentia.team.ReadinessModels.RehearsalResponseState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadinessPolicyTest {

    private final ReadinessPolicy policy = new ReadinessPolicy();

    @Test
    void redactsPrivateHumanNotesButKeepsStructuredBlockersForMusicians() {
        // Arrange
        ReadinessNoteRecord note = new ReadinessNoteRecord(
                UUID.randomUUID(),
                ReadinessScopeType.SERVICE_TEAM,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReadinessStatus.AT_RISK,
                List.of("drummer has not confirmed chart"),
                List.of("bass"),
                List.of("acoustic capo conflict"),
                RehearsalResponseState.FOLLOW_UP_REQUIRED,
                "Pastoral/private scheduling detail",
                ReadinessPrivacyClassification.TEAM_PRIVATE,
                false,
                "leader",
                Instant.parse("2026-06-01T10:00:00Z"));

        // Act
        ReadinessNoteRecord redacted = policy.redact(note, ReadinessAudience.ASSIGNED_MUSICIAN);

        // Assert
        assertThat(redacted.humanNote()).isNull();
        assertThat(redacted.objectiveBlockers()).containsExactly("drummer has not confirmed chart");
        assertThat(redacted.missingPeople()).containsExactly("bass");
        assertThat(redacted.unresolvedArrangementConflicts()).containsExactly("acoustic capo conflict");
    }

    @Test
    void allowsTeamLeadersToReadTeamPrivateReadinessNotes() {
        // Arrange
        ReadinessNoteRecord note = new ReadinessNoteRecord(
                UUID.randomUUID(),
                ReadinessScopeType.REHEARSAL,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReadinessStatus.READY,
                List.of(),
                List.of(),
                List.of(),
                RehearsalResponseState.ATTENDED,
                "Ready after rehearsal",
                ReadinessPrivacyClassification.TEAM_PRIVATE,
                false,
                "leader",
                Instant.parse("2026-06-01T10:00:00Z"));

        // Act
        ReadinessNoteRecord visible = policy.redact(note, ReadinessAudience.TEAM_LEADER);

        // Assert
        assertThat(visible.humanNote()).isEqualTo("Ready after rehearsal");
    }
}
