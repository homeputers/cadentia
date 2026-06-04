package com.cadentia.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cadentia.team.PersonnelDataRedactor;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class PersonnelAuthorizationPolicyTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PersonnelAuthorizationPolicy policy =
            new PersonnelAuthorizationPolicy(new SecurityObservabilityRecorder(meterRegistry));
    private final PersonnelDataRedactor redactor = new PersonnelDataRedactor(policy);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rosterReadAllowsSchedulerButDeniesAssignedMusicianBroadRosterAccess() {
        // Arrange
        authenticate("scheduler", RbacAuthorities.ROLE_TEAM_SCHEDULER);

        // Act / Assert
        policy.requireRosterRead();
        assertThat(meterRegistry.get("cadentia_authz_decisions_total")
                .tag("operation_class", "personnel.roster.read")
                .tag("decision", "allow")
                .tag("role", "TEAM_SCHEDULER")
                .counter()
                .count()).isEqualTo(1.0d);

        // Arrange
        authenticate("assigned@example.test", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act / Assert
        assertThatThrownBy(policy::requireRosterRead)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied.");
    }

    @Test
    void readOnlyReportingRosterViewRedactsPrivateContactAndSensitiveRangeFields() {
        // Arrange
        authenticate("reporter", RbacAuthorities.ROLE_REPORTING_VIEWER);
        MusicianRecord musician = musician("Jordan Lee", "jordan@example.test");

        // Act
        MusicianRecord redacted = redactor.redact(musician);

        // Assert
        assertThat(policy.canReadRoster()).isTrue();
        assertThat(redacted.displayName()).isEqualTo("Jordan Lee");
        assertThat(redacted.email()).isNull();
        assertThat(redacted.phone()).isNull();
        assertThat(redacted.primaryVocalRangeCode()).isNull();
        assertThat(redacted.comfortableLowMidiNote()).isNull();
        assertThat(redacted.servingPreferenceCode()).isNull();
    }

    @Test
    void assignedMusicianCanReadOwnProfileAndRespondWithoutRosterPermission() {
        // Arrange
        MusicianRecord musician = musician("Avery Rivera", "avery@example.test");
        authenticate("avery@example.test", RbacAuthorities.ROLE_ASSIGNED_MUSICIAN);

        // Act / Assert
        policy.requireMusicianProfileRead(musician);
        policy.requireSelfServiceAssignmentResponse(musician);
        assertThat(policy.canReadRoster()).isFalse();
        assertThat(redactor.redact(musician).email()).isEqualTo("avery@example.test");
    }

    @Test
    void reviewerCanSeeRosterButNotPrivatePersonnelDetailsOrMutateAssignments() {
        // Arrange
        MusicianRecord musician = musician("Casey Morgan", "casey@example.test");
        authenticate("reviewer", RbacAuthorities.ROLE_DOCTRINAL_REVIEWER);

        // Act / Assert
        policy.requireRosterRead();
        assertThat(policy.canReadPrivateContactData(musician)).isFalse();
        assertThat(policy.canReadReadinessNotes()).isFalse();
        assertThatThrownBy(policy::requireAssignmentManagement).isInstanceOf(AccessDeniedException.class);
    }

    private void authenticate(String principal, String authority) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(principal, "n/a", List.of(() -> authority)));
    }

    private MusicianRecord musician(String displayName, String accountPrincipal) {
        return new MusicianRecord(
                UUID.randomUUID(),
                displayName,
                accountPrincipal,
                accountPrincipal,
                "555-0100",
                VocalRangeCode.MEDIUM,
                48,
                72,
                ServingPreferenceCode.PREFERRED,
                true);
    }
}
