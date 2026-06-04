package com.cadentia.runtime;

import java.util.Map;

public record TelemetryEvent(String name, String instanceId, Map<String, String> labels) {
    public TelemetryEvent {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }
}
