package com.cadentia.reng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.KeyPolicy;
import com.cadentia.generated.model.SetlistCounts;
import com.cadentia.generated.model.RecommendationServiceTeamContext;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.generated.model.TempoPolicy;
import com.cadentia.rehearsal.RehearsalWorkflowModels.ReadinessStateCode;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummary;
import com.cadentia.rehearsal.RehearsalWorkflowModels.RehearsalWorkflowSummaryAudience;
import com.cadentia.rehearsal.RehearsalWorkflowSummaryProvider;
import com.cadentia.reng.scoring.ScoringRequestFactory;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.RuntimeModuleAccessException;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import java.util.List;
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
    private final StaticInstanceConfigurationProvider configurationProvider = new StaticInstanceConfigurationProvider(configuration);
    private final SetlistService setlistService = new SetlistService(
            configurationProvider,
            new ScoringRequestFactory(configurationProvider));

    @Test
    void generateUsesInstanceRecommendationConfigurationWithoutInventingSongs() {
        // Arrange
        GenerateSetlistRequest request = validRequest();

        // Act
        SetlistProposalResponse response = setlistService.generate(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("PENDING_CATALOG_IMPLEMENTATION");
        assertThat(response.getAuditMessages())
                .contains(
                        "Recommendation Engine scaffold accepted the structured request for instance river-city-worship.",
                        "No songs were selected because catalog retrieval is not implemented yet.");
        assertThat(response.getAuditMessages().get(1))
                .contains("local approved catalog policy", "scoring profile local-development");
    }

    @Test
    void generateCanAttachOperationalWorkflowDiagnosticsWithoutChangingRecommendationResult() {
        // Arrange
        UUID servicePlanId = UUID.randomUUID();
        RehearsalWorkflowSummaryProvider workflowSummaryService = (requestedServicePlanId, audience) ->
                new RehearsalWorkflowSummary(requestedServicePlanId, ReadinessStateCode.PLANNED, ReadinessStateCode.PLANNED, false,
                        "planned", null, null, List.of(), 0, 0, List.of(), 0, 0, 0, false, List.of(), true);
        SetlistService diagnosticService = new SetlistService(
                configurationProvider,
                new ScoringRequestFactory(configurationProvider),
                workflowSummaryService);
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
}
