package com.cadentia.songimport;

import java.util.Map;

public record ConnectorConfiguration(
        String sourceSystem,
        String legalModeEvidence,
        Map<String, String> sourceIdentifiers,
        Map<String, String> operatorOptions) {

    public ConnectorConfiguration {
        sourceSystem = ImportConnectorValidation.requireText(sourceSystem, "sourceSystem");
        legalModeEvidence = ImportConnectorValidation.requireOptionalText(legalModeEvidence, "legalModeEvidence");
        sourceIdentifiers = Map.copyOf(ImportConnectorValidation.requireNonNull(
                sourceIdentifiers, "sourceIdentifiers"));
        operatorOptions = Map.copyOf(ImportConnectorValidation.requireNonNull(operatorOptions, "operatorOptions"));
    }
}
