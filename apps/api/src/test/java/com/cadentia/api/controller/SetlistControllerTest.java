package com.cadentia.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.cadentia.api.security.RbacAuthorities;
import com.cadentia.generated.model.CommitSetlistEditsRequest;
import com.cadentia.generated.model.CreateSetlistBaselineRequest;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.NaturalLanguageSetlistRequest;
import com.cadentia.generated.model.SetlistEditOperation;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.SetlistProvenanceType;
import com.cadentia.generated.model.SetlistVersionEnvelope;
import com.cadentia.generated.model.SetlistVersionItem;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.intent.ClarifyRequestIntent;
import com.cadentia.intent.Counts;
import com.cadentia.intent.DefaultSessionMergeService;
import com.cadentia.intent.GenerateSetlistIntent;
import com.cadentia.intent.GenerateSetlistSlots;
import com.cadentia.intent.IntentKeyPolicy;
import com.cadentia.intent.IntentTempoPolicy;
import com.cadentia.intent.UnsupportedRequestIntent;
import com.cadentia.llm.IntentParseResult;
import com.cadentia.llm.IntentService;
import com.cadentia.reng.SetlistService;
import com.cadentia.reng.setlist.SetlistVersionDiffService;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistItemCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionItemSnapshot;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import com.cadentia.reng.setlist.SetlistVersionRepository;
import com.cadentia.reng.setlist.SetlistVersionService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SetlistControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void naturalLanguageRequestInvokesRecommendationOnlyForValidGenerateSetlistIntent() {
        // Arrange
        GenerateSetlistIntent intent = new GenerateSetlistIntent("v1", new GenerateSetlistSlots(
                "Psalm 100",
                List.of("Psalm 100"),
                List.of("thanksgiving"),
                new Counts(4, 2),
                new IntentKeyPolicy(true, true, 2),
                new IntentTempoPolicy(12),
                null,
                "rising",
                List.of("Song To Exclude"),
                "opening"));
        StubIntentService intentService = new StubIntentService(IntentParseResult.accepted(intent, false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                intentService,
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("Psalm 100 thanksgiving, exclude Song To Exclude."));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING_CATALOG_IMPLEMENTATION");
        assertThat(intentService.lastInput).isEqualTo("Psalm 100 thanksgiving, exclude Song To Exclude.");
        assertThat(setlistService.invocationCount).isEqualTo(1);
        assertThat(setlistService.lastRequest.getVerseText()).isEqualTo("Psalm 100");
        assertThat(setlistService.lastRequest.getScriptureReferences()).containsExactly("Psalm 100");
        assertThat(setlistService.lastRequest.getExcludedSongs()).containsExactly("Song To Exclude");
        assertThat(setlistService.lastRequest.toString()).doesNotContain("selectedSongs");
    }

    @Test
    void naturalLanguageClarificationDoesNotInvokeRecommendation() {
        // Arrange
        ClarifyRequestIntent intent = new ClarifyRequestIntent(
                "v1",
                "MISSING_REQUIRED_INFORMATION",
                "Which scripture or theme should the setlist focus on?",
                List.of("verseText", "scriptureReferences"));
        StubIntentService intentService = new StubIntentService(IntentParseResult.accepted(intent, false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                intentService,
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("Make me a setlist."));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(response.getBody().getAuditMessages())
                .contains("Intent extraction requested clarification before recommendation.")
                .contains("Which scripture or theme should the setlist focus on?")
                .contains("Missing slots: verseText, scriptureReferences.");
        assertThat(setlistService.invocationCount).isZero();
    }

    @Test
    void naturalLanguageUnsupportedRequestDoesNotInvokeRecommendation() {
        // Arrange
        UnsupportedRequestIntent intent = new UnsupportedRequestIntent(
                "v1",
                "UNSUPPORTED_ACTION",
                "I cannot approve songs or update catalog records.");
        StubIntentService intentService = new StubIntentService(IntentParseResult.accepted(intent, false));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                intentService,
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposalFromNaturalLanguage(
                new NaturalLanguageSetlistRequest().text("Approve this new catalog song."));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("UNSUPPORTED_REQUEST");
        assertThat(response.getBody().getAuditMessages())
                .contains("Intent extraction rejected the request before recommendation.")
                .contains("I cannot approve songs or update catalog records.");
        assertThat(setlistService.invocationCount).isZero();
    }

    @Test
    void structuredRequestRejectsUnauthorizedAdminDiagnostics() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "leader",
                "n/a",
                List.of(new SimpleGrantedAuthority(RbacAuthorities.ROLE_WORSHIP_LEADER))));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                new StubIntentService(IntentParseResult.accepted(new UnsupportedRequestIntent("v1", "unused", "unused"), false)),
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .counts(new SetlistCounts().praise(1).worship(0))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(true)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(2))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(12))
                .explanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN)
                .includeAdminDiagnostics(true);

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposal(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(setlistService.invocationCount).isZero();
        assertThat(request.getIncludeAdminDiagnostics()).isFalse();
        assertThat(request.getExplanationAudience()).isEqualTo(GenerateSetlistRequest.ExplanationAudienceEnum.PUBLIC);
    }

    @Test
    void structuredRequestAllowsAuthorizedAdminDiagnosticsWithoutChangingServicePath() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority(RbacAuthorities.ROLE_ADMIN))));
        CapturingSetlistService setlistService = new CapturingSetlistService();
        SetlistController controller = new SetlistController(
                setlistService,
                new StubIntentService(IntentParseResult.accepted(new UnsupportedRequestIntent("v1", "unused", "unused"), false)),
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService(),
                new SetlistVersionDiffService());
        GenerateSetlistRequest request = new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .counts(new SetlistCounts().praise(1).worship(0))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(true)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(2))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(12))
                .explanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN)
                .includeAdminDiagnostics(true);

        // Act
        ResponseEntity<SetlistProposalResponse> response = controller.generateSetlistProposal(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(setlistService.invocationCount).isEqualTo(1);
        assertThat(setlistService.lastRequest.getExplanationAudience()).isEqualTo(GenerateSetlistRequest.ExplanationAudienceEnum.ADMIN);
        assertThat(setlistService.lastRequest.getIncludeAdminDiagnostics()).isTrue();
    }

    @Test
    void createSetlistBaselineVersionPersistsSharedVersionBoundary() {
        // Arrange
        InMemorySetlistVersionRepository repository = new InMemorySetlistVersionRepository();
        SetlistController controller = controller(new CapturingSetlistService(), setlistVersionService(repository));
        UUID arrangementId = UUID.randomUUID();
        CreateSetlistBaselineRequest request = new CreateSetlistBaselineRequest()
                .request(validRequest())
                .parsedIntent(Map.of("intent", "GENERATE_SETLIST"))
                .engineProfileVersion("profile-v1")
                .explanationFacts(List.of("approval:fixture"))
                .items(List.of(new SetlistVersionItem(UUID.randomUUID(), 1, arrangementId, SetlistProvenanceType.GENERATED_BASELINE)));

        // Act
        ResponseEntity<SetlistVersionEnvelope> response = controller.createSetlistBaselineVersion(request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getVersion().getVersionNumber()).isEqualTo(1);
        assertThat(response.getBody().getVersion().getEngineProfileVersion()).isEqualTo("profile-v1");
        assertThat(response.getBody().getVersion().getItems())
                .extracting(SetlistVersionItem::getPosition, SetlistVersionItem::getCatalogArrangementId,
                        SetlistVersionItem::getProvenance)
                .containsExactly(tuple(1, arrangementId, SetlistProvenanceType.GENERATED_BASELINE));
        assertThat(repository.baselineCommand.requestPayload()).contains("\"verseText\":\"Psalm 100\"");
        assertThat(repository.baselineCommand.parsedIntentPayload()).contains("\"intent\":\"GENERATE_SETLIST\"");
    }

    @Test
    void commitSetlistEditsAppliesOperationsAndCreatesImmutableVersion() {
        // Arrange
        InMemorySetlistVersionRepository repository = new InMemorySetlistVersionRepository();
        SetlistController controller = controller(new CapturingSetlistService(), setlistVersionService(repository));
        UUID firstArrangement = UUID.randomUUID();
        UUID secondArrangement = UUID.randomUUID();
        UUID replacementArrangement = UUID.randomUUID();
        SetlistVersionSnapshot baseline = repository.createBaseline(new CreateSetlistBaselineCommand(
                "planner",
                "profile-v1",
                "profile-v1",
                "{}",
                "{}",
                "[]",
                List.of(
                        new CreateSetlistItemCommand(0, firstArrangement, null, null, null, "GENERATED", null),
                        new CreateSetlistItemCommand(1, secondArrangement, null, null, null, "GENERATED", null)),
                "LINEAR"));
        UUID firstItemId = baseline.items().get(0).id();
        UUID secondItemId = baseline.items().get(1).id();
        CommitSetlistEditsRequest request = new CommitSetlistEditsRequest()
                .baseVersionId(baseline.versionId())
                .actorId("leader")
                .operations(List.of(
                        new SetlistEditOperation(SetlistEditOperation.ActionEnum.REPLACE)
                                .itemId(firstItemId)
                                .replacementArrangementId(replacementArrangement),
                        new SetlistEditOperation(SetlistEditOperation.ActionEnum.TRANSPOSE)
                                .itemId(firstItemId)
                                .semitoneDelta(2),
                        new SetlistEditOperation(SetlistEditOperation.ActionEnum.REORDER)
                                .itemId(secondItemId)
                                .toPosition(1)));

        // Act
        ResponseEntity<SetlistVersionEnvelope> response = controller.commitSetlistEdits(baseline.setlistId(), request);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getVersion().getParentVersionId()).isEqualTo(baseline.versionId());
        assertThat(response.getBody().getVersion().getVersionNumber()).isEqualTo(2);
        assertThat(response.getBody().getVersion().getItems())
                .extracting(SetlistVersionItem::getPosition, SetlistVersionItem::getCatalogArrangementId,
                        SetlistVersionItem::getTransposeSemitones, SetlistVersionItem::getProvenance)
                .containsExactly(
                        tuple(1, secondArrangement, 0, SetlistProvenanceType.MANUAL_EDIT),
                        tuple(2, replacementArrangement, 2, SetlistProvenanceType.MANUAL_EDIT));
        assertThat(repository.editCommand.createdBy()).isEqualTo("leader");
        assertThat(repository.editCommand.editEvents()).hasSize(3);
    }

    @Test
    void commitSetlistEditsRejectsStaleBaseVersion() {
        // Arrange
        InMemorySetlistVersionRepository repository = new InMemorySetlistVersionRepository();
        SetlistController controller = controller(new CapturingSetlistService(), setlistVersionService(repository));
        SetlistVersionSnapshot baseline = repository.createBaseline(new CreateSetlistBaselineCommand(
                "planner", "profile-v1", "profile-v1", "{}", "{}", "[]",
                List.of(new CreateSetlistItemCommand(0, UUID.randomUUID(), null, null, null, "GENERATED", null)),
                "LINEAR"));
        repository.createEditedVersion(new CreateSetlistVersionCommand(
                baseline.setlistId(), baseline.versionId(), "planner", "profile-v1", "profile-v1", "{}", "{}", "[]",
                "edit", baseline.items().stream()
                        .map(item -> new CreateSetlistItemCommand(
                                item.positionIndex(), item.catalogArrangementId(), null, null, item.id(), "GENERATED", null))
                        .toList(),
                List.of()));

        // Act
        ResponseEntity<SetlistVersionEnvelope> response = controller.commitSetlistEdits(
                baseline.setlistId(),
                new CommitSetlistEditsRequest()
                        .baseVersionId(baseline.versionId())
                        .operations(List.of(new SetlistEditOperation(SetlistEditOperation.ActionEnum.REMOVE)
                                .itemId(baseline.items().get(0).id()))));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void setlistVersionWriteEndpointsRejectInvalidRequests() {
        // Arrange
        SetlistController controller = controller(new CapturingSetlistService(), setlistVersionService(new InMemorySetlistVersionRepository()));

        // Act
        ResponseEntity<SetlistVersionEnvelope> baselineResponse = controller.createSetlistBaselineVersion(null);
        ResponseEntity<SetlistVersionEnvelope> editResponse = controller.commitSetlistEdits(UUID.randomUUID(), null);

        // Assert
        assertThat(baselineResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(editResponse.getStatusCode().value()).isEqualTo(400);
    }

    private static SetlistVersionService setlistVersionService() {
        return new SetlistVersionService(new SetlistVersionRepository() {
            @Override
            public SetlistVersionSnapshot createBaseline(CreateSetlistBaselineCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SetlistVersionSnapshot createEditedVersion(CreateSetlistVersionCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<SetlistVersionSnapshot> findVersion(UUID setlistId, UUID versionId) {
                return Optional.empty();
            }

            @Override
            public List<SetlistVersionSnapshot> findVersions(UUID setlistId) {
                return List.of();
            }
        });
    }

    private static SetlistVersionService setlistVersionService(SetlistVersionRepository repository) {
        return new SetlistVersionService(repository);
    }

    private static SetlistController controller(
            SetlistService setlistService,
            SetlistVersionService setlistVersionService) {
        return new SetlistController(
                setlistService,
                new StubIntentService(IntentParseResult.accepted(new UnsupportedRequestIntent("v1", "unused", "unused"), false)),
                new ValidatedSetlistRequestMapper(),
                new ConversationSessionFacade(new DefaultSessionMergeService(), new ValidatedSetlistRequestMapper(), Duration.ofMinutes(30), Duration.ofHours(4)),
                setlistVersionService,
                new SetlistVersionDiffService());
    }

    private GenerateSetlistRequest validRequest() {
        return new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .counts(new SetlistCounts().praise(1).worship(1))
                .keyPolicy(new KeyPolicy(true, true, 2))
                .tempoPolicy(new TempoPolicy(12));
    }

    private static class StubIntentService implements IntentService {

        private final IntentParseResult result;
        private String lastInput;

        StubIntentService(IntentParseResult result) {
            this.result = result;
        }

        @Override
        public IntentParseResult parse(String input) {
            lastInput = input;
            return result;
        }
    }

    private static class CapturingSetlistService extends SetlistService {

        private int invocationCount;
        private GenerateSetlistRequest lastRequest;

        @Override
        public SetlistProposalResponse generate(GenerateSetlistRequest request) {
            invocationCount++;
            lastRequest = request;
            return new SetlistProposalResponse()
                    .status("PENDING_CATALOG_IMPLEMENTATION")
                    .auditMessages(List.of("Recommendation Engine scaffold accepted the structured request."));
        }
    }

    private static class InMemorySetlistVersionRepository implements SetlistVersionRepository {
        private final Map<UUID, List<SetlistVersionSnapshot>> snapshots = new HashMap<>();
        private CreateSetlistBaselineCommand baselineCommand;
        private CreateSetlistVersionCommand editCommand;

        @Override
        public SetlistVersionSnapshot createBaseline(CreateSetlistBaselineCommand command) {
            baselineCommand = command;
            UUID setlistId = UUID.randomUUID();
            SetlistVersionSnapshot snapshot = snapshot(
                    setlistId,
                    null,
                    1,
                    "GENERATED_BASELINE",
                    command.scoringProfileVersion(),
                    command.engineVersion(),
                    command.createdBy(),
                    command.items());
            snapshots.put(setlistId, new ArrayList<>(List.of(snapshot)));
            return snapshot;
        }

        @Override
        public SetlistVersionSnapshot createEditedVersion(CreateSetlistVersionCommand command) {
            editCommand = command;
            List<SetlistVersionSnapshot> versions = snapshots.get(command.setlistId());
            int versionNumber = versions.stream()
                    .mapToInt(SetlistVersionSnapshot::versionNumber)
                    .max()
                    .orElse(0) + 1;
            SetlistVersionSnapshot snapshot = snapshot(
                    command.setlistId(),
                    command.parentVersionId(),
                    versionNumber,
                    "MANUAL_EDIT",
                    command.scoringProfileVersion(),
                    command.engineVersion(),
                    command.createdBy(),
                    command.items());
            versions.add(snapshot);
            return snapshot;
        }

        @Override
        public Optional<SetlistVersionSnapshot> findVersion(UUID setlistId, UUID versionId) {
            return snapshots.getOrDefault(setlistId, List.of()).stream()
                    .filter(snapshot -> snapshot.versionId().equals(versionId))
                    .findFirst();
        }

        @Override
        public List<SetlistVersionSnapshot> findVersions(UUID setlistId) {
            return snapshots.getOrDefault(setlistId, List.of());
        }

        private SetlistVersionSnapshot snapshot(
                UUID setlistId,
                UUID parentVersionId,
                int versionNumber,
                String provenanceType,
                String scoringProfileVersion,
                String engineVersion,
                String createdBy,
                List<CreateSetlistItemCommand> items) {
            return new SetlistVersionSnapshot(
                    setlistId,
                    UUID.randomUUID(),
                    parentVersionId,
                    versionNumber,
                    provenanceType,
                    scoringProfileVersion,
                    engineVersion,
                    Instant.now(),
                    createdBy,
                    items.stream()
                            .sorted(Comparator.comparing(CreateSetlistItemCommand::positionIndex))
                            .map(item -> new SetlistVersionItemSnapshot(
                                    UUID.randomUUID(),
                                    item.positionIndex(),
                                    item.catalogArrangementId(),
                                    item.transposedKey(),
                                    item.transposedMode(),
                                    item.sourceItemId(),
                                    item.itemProvenance(),
                                    item.notes()))
                            .toList());
        }
    }
}
