package com.cadentia.songimport;

import java.time.Instant;

public record SourcePayload(
        DiscoveredSource source,
        String rawContent,
        String rawContentHash,
        Instant retrievedAt) {

    public SourcePayload {
        source = ImportConnectorValidation.requireNonNull(source, "source");
        rawContent = ImportConnectorValidation.requireText(rawContent, "rawContent");
        rawContentHash = ImportConnectorValidation.requireText(rawContentHash, "rawContentHash");
        retrievedAt = ImportConnectorValidation.requireNonNull(retrievedAt, "retrievedAt");
    }
}
