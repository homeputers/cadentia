package com.cadentia.songimport;

import java.util.Map;

public record DiscoveredSource(
        String sourceRecordId,
        PayloadType payloadType,
        String sourceReference,
        Map<String, String> metadata) {

    public DiscoveredSource {
        sourceRecordId = ImportConnectorValidation.requireText(sourceRecordId, "sourceRecordId");
        payloadType = ImportConnectorValidation.requireNonNull(payloadType, "payloadType");
        sourceReference = ImportConnectorValidation.requireText(sourceReference, "sourceReference");
        metadata = Map.copyOf(ImportConnectorValidation.requireNonNull(metadata, "metadata"));
    }
}
