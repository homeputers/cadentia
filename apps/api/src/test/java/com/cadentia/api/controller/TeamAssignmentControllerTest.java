package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadentia.generated.model.CreateTeamMusicianRequest;
import com.cadentia.generated.model.TeamAssignmentStatusCode;
import com.cadentia.generated.model.TeamAvailabilityWindowRequest;
import com.cadentia.generated.model.TeamAvailabilityWindowResponse;
import com.cadentia.generated.model.TeamMusicianResponse;
import com.cadentia.generated.model.TeamRehearsalEventRequest;
import com.cadentia.generated.model.TeamRehearsalEventResponse;
import com.cadentia.generated.model.TeamServingPreferenceCode;
import com.cadentia.generated.model.TeamVocalRangeCode;
import com.cadentia.team.AuthorizedTeamPlanningService;
import com.cadentia.team.TeamPlanningModels.AssignmentStatusCode;
import com.cadentia.team.TeamPlanningModels.AvailabilityWindowRecord;
import com.cadentia.team.TeamPlanningModels.CreateMusicianCommand;
import com.cadentia.team.TeamPlanningModels.MusicianRecord;
import com.cadentia.team.TeamPlanningModels.RehearsalEventRecord;
import com.cadentia.team.TeamPlanningModels.ServingPreferenceCode;
import com.cadentia.team.TeamPlanningModels.VocalRangeCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TeamAssignmentControllerTest {

    private FakeTeamPlanningService service;
    private TeamAssignmentController controller;

    @BeforeEach
    void setUp() {
        service = new FakeTeamPlanningService();
        controller = new TeamAssignmentController(service);
    }

    @Test
    void listTeamMusiciansMapsDirectoryRecords() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        service.musicians = List.of(new MusicianRecord(
                musicianId,
                "Avery Rivera",
                "avery@example.test",
                "avery@example.test",
                "555-0100",
                VocalRangeCode.MEDIUM,
                48,
                72,
                ServingPreferenceCode.PREFERRED,
                true));

        // Act
        var response = controller.listTeamMusicians();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).singleElement().satisfies(musician -> {
            assertThat(musician.getMusicianId()).isEqualTo(musicianId);
            assertThat(musician.getDisplayName()).isEqualTo("Avery Rivera");
            assertThat(musician.getEmail()).isEqualTo("avery@example.test");
            assertThat(musician.getPrimaryVocalRangeCode()).isEqualTo(TeamVocalRangeCode.MEDIUM);
            assertThat(musician.getServingPreferenceCode()).isEqualTo(TeamServingPreferenceCode.PREFERRED);
            assertThat(musician.getActive()).isTrue();
        });
    }

    @Test
    void createTeamMusicianDelegatesAndReturnsCreated() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        service.createdMusician = new MusicianRecord(
                musicianId, "Jordan Lee", null, null, null,
                VocalRangeCode.HIGH, null, null, ServingPreferenceCode.AVAILABLE, true);
        CreateTeamMusicianRequest request = new CreateTeamMusicianRequest("Jordan Lee");
        request.setPrimaryVocalRangeCode(TeamVocalRangeCode.HIGH);
        request.setServingPreferenceCode(TeamServingPreferenceCode.AVAILABLE);
        request.setReasonCode("roster_onboarding");

        // Act
        var response = controller.createTeamMusician(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMusicianId()).isEqualTo(musicianId);
        assertThat(response.getBody().getPrimaryVocalRangeCode()).isEqualTo(TeamVocalRangeCode.HIGH);
        assertThat(service.createMusicianCommands).singleElement().satisfies(command -> {
            assertThat(command.displayName()).isEqualTo("Jordan Lee");
            assertThat(command.primaryVocalRangeCode()).isEqualTo(VocalRangeCode.HIGH);
            assertThat(command.servingPreferenceCode()).isEqualTo(ServingPreferenceCode.AVAILABLE);
            assertThat(command.createdBy()).isNull();
        });
        assertThat(service.createMusicianReasonCodes).containsExactly("roster_onboarding");
    }

    @Test
    void getTeamMusicianReturnsNotFoundForUnknownMusician() {
        // Act
        var response = controller.getTeamMusician(UUID.randomUUID());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void getTeamMusicianReturnsMappedProfile() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        service.musicianProfile = new MusicianRecord(
                musicianId, "Avery Rivera", null, null, null, null, null, null, null, true);

        // Act
        var response = controller.getTeamMusician(musicianId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TeamMusicianResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMusicianId()).isEqualTo(musicianId);
        assertThat(body.getDisplayName()).isEqualTo("Avery Rivera");
    }

    @Test
    void createTeamAvailabilityWindowReturnsCreatedWindow() {
        // Arrange
        UUID musicianId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        UUID servicePlanId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-06-07T08:00:00Z");
        Instant endsAt = Instant.parse("2026-06-07T12:00:00Z");
        service.createdAvailabilityWindow = new AvailabilityWindowRecord(
                windowId, musicianId, startsAt, endsAt, AssignmentStatusCode.UNAVAILABLE, servicePlanId);
        TeamAvailabilityWindowRequest request = new TeamAvailabilityWindowRequest(
                OffsetDateTime.parse("2026-06-07T08:00:00Z"),
                OffsetDateTime.parse("2026-06-07T12:00:00Z"),
                TeamAssignmentStatusCode.UNAVAILABLE);
        request.setServicePlanId(servicePlanId);

        // Act
        var response = controller.createTeamAvailabilityWindow(musicianId, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TeamAvailabilityWindowResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getAvailabilityWindowId()).isEqualTo(windowId);
        assertThat(body.getMusicianId()).isEqualTo(musicianId);
        assertThat(body.getStatusCode()).isEqualTo(TeamAssignmentStatusCode.UNAVAILABLE);
        assertThat(body.getStartsAt().toInstant()).isEqualTo(startsAt);
        assertThat(body.getServicePlanId()).isEqualTo(servicePlanId);
        assertThat(service.availabilityRequests).singleElement().satisfies(recorded -> {
            assertThat(recorded.musicianId()).isEqualTo(musicianId);
            assertThat(recorded.startsAt()).isEqualTo(startsAt);
            assertThat(recorded.endsAt()).isEqualTo(endsAt);
            assertThat(recorded.statusCode()).isEqualTo(AssignmentStatusCode.UNAVAILABLE);
            assertThat(recorded.servicePlanId()).isEqualTo(servicePlanId);
        });
    }

    @Test
    void createTeamRehearsalEventReturnsCreatedEvent() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        UUID rehearsalEventId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-06-04T23:00:00Z");
        Instant endsAt = Instant.parse("2026-06-05T01:00:00Z");
        service.createdRehearsalEvent = new RehearsalEventRecord(
                rehearsalEventId, servicePlanId, startsAt, endsAt, "Sanctuary");
        TeamRehearsalEventRequest request = new TeamRehearsalEventRequest(
                servicePlanId,
                OffsetDateTime.parse("2026-06-04T23:00:00Z"),
                OffsetDateTime.parse("2026-06-05T01:00:00Z"));
        request.setLocation("Sanctuary");

        // Act
        var response = controller.createTeamRehearsalEvent(request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TeamRehearsalEventResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRehearsalEventId()).isEqualTo(rehearsalEventId);
        assertThat(body.getServicePlanId()).isEqualTo(servicePlanId);
        assertThat(body.getLocation()).isEqualTo("Sanctuary");
        assertThat(body.getStartsAt().toInstant()).isEqualTo(startsAt);
    }

    @Test
    void listTeamRehearsalEventsMapsEvents() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        UUID rehearsalEventId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-06-04T23:00:00Z");
        Instant endsAt = Instant.parse("2026-06-05T01:00:00Z");
        service.rehearsalEvents = List.of(
                new RehearsalEventRecord(rehearsalEventId, servicePlanId, startsAt, endsAt, null));

        // Act
        var response = controller.listTeamRehearsalEvents(servicePlanId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).singleElement().satisfies(event -> {
            assertThat(event.getRehearsalEventId()).isEqualTo(rehearsalEventId);
            assertThat(event.getEndsAt().toInstant()).isEqualTo(endsAt);
            assertThat(event.getLocation()).isNull();
        });
    }

    private record RecordedAvailabilityRequest(
            UUID musicianId,
            Instant startsAt,
            Instant endsAt,
            AssignmentStatusCode statusCode,
            UUID servicePlanId) {
    }

    private static final class FakeTeamPlanningService extends AuthorizedTeamPlanningService {

        private List<MusicianRecord> musicians = List.of();
        private MusicianRecord musicianProfile;
        private MusicianRecord createdMusician;
        private AvailabilityWindowRecord createdAvailabilityWindow;
        private RehearsalEventRecord createdRehearsalEvent;
        private List<RehearsalEventRecord> rehearsalEvents = List.of();
        private final List<CreateMusicianCommand> createMusicianCommands = new ArrayList<>();
        private final List<String> createMusicianReasonCodes = new ArrayList<>();
        private final List<RecordedAvailabilityRequest> availabilityRequests = new ArrayList<>();

        private FakeTeamPlanningService() {
            super(null, null, null, null);
        }

        @Override
        public List<MusicianRecord> listMusiciansForRoster() {
            return musicians;
        }

        @Override
        public Optional<MusicianRecord> findMusicianProfile(UUID musicianId) {
            return Optional.ofNullable(musicianProfile);
        }

        @Override
        public MusicianRecord createMusician(CreateMusicianCommand command, String reasonCode, String reference) {
            createMusicianCommands.add(command);
            createMusicianReasonCodes.add(reasonCode);
            return createdMusician;
        }

        @Override
        public AvailabilityWindowRecord createAvailabilityWindow(
                UUID musicianId,
                Instant startsAt,
                Instant endsAt,
                AssignmentStatusCode statusCode,
                UUID servicePlanId,
                String reasonCode,
                String reference) {
            availabilityRequests.add(new RecordedAvailabilityRequest(
                    musicianId, startsAt, endsAt, statusCode, servicePlanId));
            return createdAvailabilityWindow;
        }

        @Override
        public RehearsalEventRecord createRehearsalEvent(
                UUID servicePlanId,
                Instant startsAt,
                Instant endsAt,
                String location,
                String reasonCode,
                String reference) {
            return createdRehearsalEvent;
        }

        @Override
        public List<RehearsalEventRecord> listRehearsalEvents(UUID servicePlanId) {
            return rehearsalEvents;
        }
    }
}
