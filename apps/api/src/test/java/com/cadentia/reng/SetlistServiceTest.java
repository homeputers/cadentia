package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.cadentia.catalog.model.ApprovalStatus;
import com.cadentia.catalog.model.KeyMode;
import com.cadentia.catalog.model.TagType;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.RecommendationServiceTeamContext;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummaryAudience;
import com.cadentia.rehearsal.RehearsalWorkflowSummaryProvider;
import com.cadentia.reng.scoring.CandidateFeatureScorer;
import com.cadentia.reng.scoring.HardConstraintFilter;
import com.cadentia.reng.scoring.ScoringRequestFactory;
import com.cadentia.reng.scoring.ScoringProfile;
import com.cadentia.reng.scoring.ScoringProfileLifecycle;
import com.cadentia.reng.setlist.SetlistProposalPersistenceService;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistBaselineCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.CreateSetlistVersionCommand;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionItemSnapshot;
import com.cadentia.reng.setlist.SetlistVersionModels.SetlistVersionSnapshot;
import com.cadentia.reng.setlist.SetlistVersionRepository;
import com.cadentia.reng.setlist.SetlistVersionService;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.RuntimeModuleAccessException;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SetlistServiceTest {

    private final InstanceConfiguration configuration = InstanceConfiguration.localDevelopment(
            "river-city-worship",
            "local",
            "cadentia-local-assets",
            "river-city-worship",
            "env:CADENTIA_LOCAL_ASSET_KEY_REF",
            "cadentia:river-city-worship",
            "river-city-worship",
            List.of("river-city-worship.audit-events", "river-city-worship.recommendation-events"));
    private final InstanceConfiguration recommendationConfiguration = recommendationConfiguration();
    private final StaticInstanceConfigurationProvider configurationProvider = new StaticInstanceConfigurationProvider(recommendationConfiguration);

    @Test
    void generateRetrievesScoresOrdersAndExplainsApprovedCatalogCandidates() {
        // Arrange
        CapturingCandidateRetriever retriever = new CapturingCandidateRetriever(List.of(
                candidate("1", "Living Thanksgiving", "praise", 82, "thanksgiving", "psalm-100"),
                candidate("2", "Quiet Response", "worship", 68, "gratitude", "psalm-100"),
                candidate("3", "Sending Song", "praise", 95, "sending", "romans-8")));
        SetlistService service = service(retriever, null);
        GenerateSetlistRequest request = validRequest()
                .counts(new SetlistCounts(1, 1))
                .scriptureReferences(List.of("Psalm 100:1-5"));

        // Act
        SetlistProposalResponse response = service.generate(request);

        // Assert
        assertThat(retriever.lastCriteria.requiredApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.getStatus()).isEqualTo("PROPOSED");
        assertThat(response.getRecommendationResultId()).startsWith("rec-");
        assertThat(response.getAuditMessages().get(0)).contains("approved catalog candidates");
        assertThat(response.getExplanation().getGeneratedBy().getValue()).isEqualTo("RecommendationEngine");
        assertThat(response.getExplanation().getCatalogSnapshotVersion()).startsWith("approved-catalog:river-city-worship");
        assertThat(response.getExplanation().getSelectedSongs())
                .extracting(entry -> entry.getDefaultText())
                .containsExactly("Living Thanksgiving", "Quiet Response");
        assertThat(response.getExplanation().getSelectedSongs().get(0).getEvidence())
                .extracting(evidence -> evidence.getRef())
                .contains(
                        "catalog:arrangement:" + id("arrangement-1"),
                        "approval:approval_gate_summary");
        assertThat(response.getExplanation().getWarnings()).isEmpty();
    }

    @Test
    void generatePersistsGeneratedBaselineWhenPersistenceBoundaryIsConfigured() {
        // Arrange
        CapturingCandidateRetriever retriever = new CapturingCandidateRetriever(List.of(
                candidate("1", "Living Thanksgiving", "praise", 82, "thanksgiving", "psalm-100"),
                candidate("2", "Quiet Response", "worship", 68, "gratitude", "psalm-100")));
        CapturingSetlistVersionRepository repository = new CapturingSetlistVersionRepository();
        SetlistProposalPersistenceService persistenceService = new SetlistProposalPersistenceService(
                new SetlistVersionService(repository),
                new ObjectMapper());
        SetlistService service = service(retriever, null, persistenceService);

        // Act
        SetlistProposalResponse response = service.generate(validRequest().counts(new SetlistCounts(1, 1)));

        // Assert
        assertThat(response.getExplanation().getSetlistId()).isEqualTo(repository.snapshot.setlistId().toString());
        assertThat(repository.baselineCommand.scoringProfileVersion()).isEqualTo("test-recommendation-profile");
        assertThat(repository.baselineCommand.engineVersion()).isEqualTo("RecommendationEngine:recommendation_explanation.v1");
        assertThat(repository.baselineCommand.requestPayload()).contains("\"verseText\":\"Psalm 100\"");
        assertThat(repository.baselineCommand.parsedIntentPayload()).contains("\"intent\":\"GENERATE_SETLIST\"");
        assertThat(repository.baselineCommand.explanationFactsPayload()).contains("\"recommendationResultId\":\"");
        assertThat(repository.baselineCommand.items())
                .extracting(item -> item.positionIndex(), item -> item.catalogArrangementId(), item -> item.itemProvenance())
                .containsExactly(
                        tuple(0, id("arrangement-1"), "GENERATED"),
                        tuple(1, id("arrangement-2"), "GENERATED"));
    }

    @Test
    void generateCanAttachOperationalWorkflowDiagnosticsWithoutChangingRecommendationResult() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        RehearsalWorkflowSummaryProvider workflowSummaryService = (requestedServicePlanId, audience) ->
                new RehearsalWorkflowSummary(requestedServicePlanId, ReadinessStateCode.PLANNED, ReadinessStateCode.PLANNED, false,
                        "planned", null, null, List.of(), 0, 0, List.of(), 0, 0, 0, false, List.of(), true);
        SetlistService diagnosticService = service(new CapturingCandidateRetriever(List.of()), workflowSummaryService);
        GenerateSetlistRequest baselineRequest = validRequest();
        GenerateSetlistRequest diagnosticRequest = validRequest()
                .serviceTeamContext(new RecommendationServiceTeamContext().servicePlanId(servicePlanId))
                .explanationAudience(GenerateSetlistRequest.ExplanationAudienceEnum.PUBLIC);

        // Act
        SetlistProposalResponse baselineResponse = diagnosticService.generate(baselineRequest);
        SetlistProposalResponse diagnosticResponse = diagnosticService.generate(diagnosticRequest);

        // Assert
        assertThat(diagnosticResponse.getOperationalWorkflowSummary()).isNotNull();
        assertThat(diagnosticResponse.getOperationalWorkflowSummary().getDerivedStateCode().getValue()).isEqualTo("planned");
        assertThat(diagnosticResponse.getStatus()).isEqualTo(baselineResponse.getStatus());
        assertThat(diagnosticResponse.getAuditMessages()).isEqualTo(baselineResponse.getAuditMessages());
    }

    @Test
    void generateReturnsSafeEmptyOutcomeWithoutInventingSongs() {
        // Arrange
        CapturingCandidateRetriever retriever = new CapturingCandidateRetriever(List.of());
        SetlistService service = service(retriever, null);

        // Act
        SetlistProposalResponse response = service.generate(validRequest());

        // Assert
        assertThat(response.getStatus()).isEqualTo("NO_APPROVED_CANDIDATES");
        assertThat(response.getExplanation().getSelectedSongs()).isEmpty();
        assertThat(response.getExplanation().getWarnings()).hasSize(1);
        assertThat(response.getAuditMessages()).anySatisfy(message ->
                assertThat(message).contains("no songs were fabricated"));
    }

    @Test
    void generateReturnsPartialOutcomeWhenApprovedCandidatesAreInsufficient() {
        // Arrange
        CapturingCandidateRetriever retriever = new CapturingCandidateRetriever(List.of(
                candidate("1", "Living Thanksgiving", "praise", 82, "thanksgiving", "psalm-100")));
        SetlistService service = service(retriever, null);

        // Act
        SetlistProposalResponse response = service.generate(validRequest().counts(new SetlistCounts(1, 1)));

        // Assert
        assertThat(response.getStatus()).isEqualTo("INSUFFICIENT_APPROVED_CANDIDATES");
        assertThat(response.getExplanation().getSelectedSongs())
                .extracting(entry -> entry.getDefaultText())
                .containsExactly("Living Thanksgiving");
        assertThat(response.getExplanation().getWarnings()).hasSize(1);
    }

    @Test
    void generateRejectsDisabledRecommendationModuleInsteadOfDefaultActivatingIt() {
        // Arrange
        InstanceConfiguration disabledConfiguration = new InstanceConfiguration(
                "river-city-worship",
                configuration.packageVersion(),
                new InstanceConfiguration.Modules(false, false, false, false, false, false),
                configuration.recommendationPolicy(),
                configuration.scoringProfile(),
                configuration.integrations(),
                configuration.plugins(),
                configuration.assetStorage(),
                configuration.namespaces(),
                configuration.telemetryExport());
        StaticInstanceConfigurationProvider disabledProvider = new StaticInstanceConfigurationProvider(disabledConfiguration);
        SetlistService disabledService = new SetlistService(disabledProvider, new ScoringRequestFactory(disabledProvider));

        // Act / Assert
        assertThatThrownBy(() -> disabledService.generate(new GenerateSetlistRequest().verseText("Psalm 1")))
                .isInstanceOf(RuntimeModuleAccessException.class)
                .hasMessageContaining("disabled");
    }

    private GenerateSetlistRequest validRequest() {
        return new GenerateSetlistRequest()
                .verseText("Psalm 100")
                .themeHints(List.of("thanksgiving"))
                .counts(new SetlistCounts().praise(10).worship(5))
                .keyPolicy(new KeyPolicy()
                        .preferSameKey(true)
                        .allowRelativeMajorMinor(true)
                        .maxKeyCenters(2))
                .tempoPolicy(new TempoPolicy().maxJumpBpm(12));
    }

    private SetlistService service(
            CandidateRetriever retriever,
            RehearsalWorkflowSummaryProvider workflowSummaryProvider) {
        return service(retriever, workflowSummaryProvider, null);
    }

    private SetlistService service(
            CandidateRetriever retriever,
            RehearsalWorkflowSummaryProvider workflowSummaryProvider,
            SetlistProposalPersistenceService persistenceService) {
        return new SetlistService(
                configurationProvider,
                new ScoringRequestFactory(configurationProvider),
                retriever,
                new HardConstraintFilter(),
                new CandidateFeatureScorer(),
                new DeterministicSetOrderer(),
                workflowSummaryProvider,
                persistenceService);
    }

    private InstanceConfiguration recommendationConfiguration() {
        return new InstanceConfiguration(
                configuration.instanceId(),
                configuration.packageVersion(),
                configuration.modules(),
                configuration.recommendationPolicy(),
                new ScoringProfile(
                        "test-recommendation-profile",
                        Map.of(
                                CandidateFeatureScorer.THEME_MATCH, 6.0d,
                                CandidateFeatureScorer.SCRIPTURE_MATCH, 3.0d,
                                CandidateFeatureScorer.ROLE_FIT, 1.0d,
                                CandidateFeatureScorer.MUSICAL_FIT, 1.0d,
                                CandidateFeatureScorer.ENERGY_FIT, 1.0d,
                                CandidateFeatureScorer.METADATA_CONFIDENCE, 1.0d),
                        List.of("title", "arrangement_id"),
                        ScoringProfileLifecycle.active()),
                configuration.integrations(),
                configuration.plugins(),
                configuration.assetStorage(),
                configuration.namespaces(),
                configuration.telemetryExport());
    }

    private RecommendableArrangement candidate(
            String suffix,
            String title,
            String role,
            int bpm,
            String themeSlug,
            String scriptureSlug) {
        RecommendationTag theme = new RecommendationTag(id("tag-theme-" + suffix), TagType.THEME, themeSlug, themeSlug);
        RecommendationTag scripture = new RecommendationTag(id("tag-scripture-" + suffix), TagType.SCRIPTURE, scriptureSlug, scriptureSlug);
        return new RecommendableArrangement(
                id("arrangement-" + suffix),
                id("song-" + suffix),
                id("lyrics-" + suffix),
                title,
                "en",
                "G",
                KeyMode.MAJOR,
                bpm,
                "4/4",
                75,
                List.of(role),
                List.of(theme, scripture),
                List.of(theme, scripture),
                approvedSummary());
    }

    private ApprovalGateSummary approvedSummary() {
        return new ApprovalGateSummary(
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED,
                ApprovalStatus.APPROVED);
    }

    private UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static class CapturingCandidateRetriever implements CandidateRetriever {
        private final List<RecommendableArrangement> candidates;
        private CandidateSearchCriteria lastCriteria;

        private CapturingCandidateRetriever(List<RecommendableArrangement> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<RecommendableArrangement> findCandidates(CandidateSearchCriteria criteria) {
            lastCriteria = criteria;
            return candidates;
        }
    }

    private static class CapturingSetlistVersionRepository implements SetlistVersionRepository {
        private CreateSetlistBaselineCommand baselineCommand;
        private SetlistVersionSnapshot snapshot;

        @Override
        public SetlistVersionSnapshot createBaseline(CreateSetlistBaselineCommand command) {
            baselineCommand = command;
            UUID setlistId = UUID.randomUUID();
            snapshot = new SetlistVersionSnapshot(
                    setlistId,
                    UUID.randomUUID(),
                    null,
                    1,
                    "GENERATED_BASELINE",
                    command.scoringProfileVersion(),
                    command.engineVersion(),
                    Instant.now(),
                    command.createdBy(),
                    command.items().stream()
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
            return snapshot;
        }

        @Override
        public SetlistVersionSnapshot createEditedVersion(CreateSetlistVersionCommand command) {
            throw new UnsupportedOperationException("Edits are not used by this test.");
        }

        @Override
        public Optional<SetlistVersionSnapshot> findVersion(UUID setlistId, UUID versionId) {
            return Optional.empty();
        }

        @Override
        public List<SetlistVersionSnapshot> findVersions(UUID setlistId) {
            return List.of();
        }
    }
}
