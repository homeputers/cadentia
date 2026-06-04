package com.cadentia.reng;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.reng.scoring.ScoringRequest;
import com.cadentia.reng.scoring.ScoringRequestFactory;
import com.cadentia.runtime.InstanceConfiguration;
import com.cadentia.runtime.InstanceConfigurationProvider;
import com.cadentia.runtime.RuntimeModuleAccessException;
import com.cadentia.runtime.StaticInstanceConfigurationProvider;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SetlistService {
    private final InstanceConfigurationProvider configurationProvider;
    private final ScoringRequestFactory scoringRequestFactory;

    public SetlistService() {
        this(new StaticInstanceConfigurationProvider(InstanceConfiguration.localDevelopment(
                "local-development",
                "local",
                "cadentia-local-assets",
                "local-development",
                "env:CADENTIA_LOCAL_ASSET_KEY_REF",
                "cadentia:local:development",
                "local.development",
                List.of("local.development.audit-events", "local.development.recommendation-events"))),
                new ScoringRequestFactory());
    }

    public SetlistService(InstanceConfigurationProvider configurationProvider, ScoringRequestFactory scoringRequestFactory) {
        this.configurationProvider = configurationProvider;
        this.scoringRequestFactory = scoringRequestFactory;
    }

    public SetlistProposalResponse generate(GenerateSetlistRequest request) {
        InstanceConfiguration configuration = configurationProvider.current();
        if (!configuration.modules().recommendation()) {
            throw new RuntimeModuleAccessException("Recommendation module is disabled for this instance.");
        }
        if (!configuration.recommendationPolicy().requireApprovedOnly()
                || !configuration.recommendationPolicy().requireDatasetReferences()) {
            throw new RuntimeModuleAccessException(
                    "Recommendation policy must require approved catalog records and dataset references.");
        }

        ScoringRequest scoringRequest = scoringRequestFactory.fromValidatedRequest(request);
        return new SetlistProposalResponse()
                .status("PENDING_CATALOG_IMPLEMENTATION")
                .auditMessages(List.of(
                        "Recommendation Engine scaffold accepted the structured request for instance "
                                + configuration.instanceId() + ".",
                        "Using local approved catalog policy, scoring profile "
                                + configuration.scoringProfile().version()
                                + ", praise count "
                                + scoringRequest.praiseCount()
                                + ", worship count "
                                + scoringRequest.worshipCount()
                                + ".",
                        "No songs were selected because catalog retrieval is not implemented yet."));
    }
}
