package com.cadentia.songimport;

import java.util.Map;

public record ConnectorNativeRecord(
        SourcePayload payload,
        Map<String, String> fields,
        Map<String, String> warnings) {

    public ConnectorNativeRecord {
        payload = ImportConnectorValidation.requireNonNull(payload, "payload");
        fields = Map.copyOf(ImportConnectorValidation.requireNonNull(fields, "fields"));
        warnings = Map.copyOf(ImportConnectorValidation.requireNonNull(warnings, "warnings"));
    }
}
