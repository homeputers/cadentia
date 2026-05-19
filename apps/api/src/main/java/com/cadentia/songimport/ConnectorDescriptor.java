package com.cadentia.songimport;

import com.cadentia.catalog.model.ImportMethod;
import java.util.Set;

public record ConnectorDescriptor(
        String connectorId,
        String providerName,
        String displayName,
        ImportMethod importMethod,
        LegalMode legalMode,
        CredentialRequirement credentialRequirement,
        Set<PayloadType> supportedPayloadTypes,
        RateLimitPolicy rateLimitPolicy,
        AutomationLevel automationLevel,
        Set<ConnectorCapability> capabilities) {

    public ConnectorDescriptor {
        connectorId = ImportConnectorValidation.requireText(connectorId, "connectorId");
        providerName = ImportConnectorValidation.requireText(providerName, "providerName");
        displayName = ImportConnectorValidation.requireText(displayName, "displayName");
        importMethod = ImportConnectorValidation.requireNonNull(importMethod, "importMethod");
        legalMode = ImportConnectorValidation.requireNonNull(legalMode, "legalMode");
        credentialRequirement = ImportConnectorValidation.requireNonNull(credentialRequirement, "credentialRequirement");
        supportedPayloadTypes = Set.copyOf(ImportConnectorValidation.requireNonEmpty(
                supportedPayloadTypes, "supportedPayloadTypes"));
        rateLimitPolicy = ImportConnectorValidation.requireNonNull(rateLimitPolicy, "rateLimitPolicy");
        automationLevel = ImportConnectorValidation.requireNonNull(automationLevel, "automationLevel");
        capabilities = Set.copyOf(ImportConnectorValidation.requireNonEmpty(capabilities, "capabilities"));
    }
}
