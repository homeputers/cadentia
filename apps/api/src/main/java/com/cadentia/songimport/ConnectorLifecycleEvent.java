package com.cadentia.songimport;

import java.time.Instant;
import java.util.Map;

public record ConnectorLifecycleEvent(
        ConnectorLifecycleStage stage,
        String message,
        Instant occurredAt,
        Map<String, String> attributes) {

    public ConnectorLifecycleEvent {
        stage = ImportConnectorValidation.requireNonNull(stage, "stage");
        message = ImportConnectorValidation.requireText(message, "message");
        occurredAt = ImportConnectorValidation.requireNonNull(occurredAt, "occurredAt");
        attributes = Map.copyOf(ImportConnectorValidation.requireNonNull(attributes, "attributes"));
    }

    public static ConnectorLifecycleEvent of(ConnectorLifecycleStage stage, String message) {
        return new ConnectorLifecycleEvent(stage, message, Instant.now(), Map.of());
    }
}
