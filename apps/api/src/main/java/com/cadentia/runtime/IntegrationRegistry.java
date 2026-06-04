package com.cadentia.runtime;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IntegrationRegistry {
    private final InstanceConfigurationProvider configurationProvider;

    public IntegrationRegistry(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public InstanceConfiguration.IntegrationProvider requireConfiguredIntegration(String integrationRef) {
        Optional<InstanceConfiguration.IntegrationProvider> integration = configurationProvider.current()
                .enabledIntegration(integrationRef)
                .filter(provider -> StringUtils.hasText(provider.secretRef()));
        return integration.orElseThrow(() -> new RuntimeModuleAccessException(
                "Integration is disabled or missing configured credential reference: " + integrationRef));
    }
}
