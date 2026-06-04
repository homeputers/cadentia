package com.cadentia.runtime;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TelemetryEventFactory {
    private static final Set<String> ALLOWED_LABELS = Set.of(
            "module", "operation", "status", "profile", "environment", "resource_namespace");

    private final InstanceConfigurationProvider configurationProvider;

    public TelemetryEventFactory(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public TelemetryEvent event(String name, Map<String, String> labels) {
        labels.keySet().forEach(label -> {
            if (!ALLOWED_LABELS.contains(label)) {
                throw new RuntimeModuleAccessException("Telemetry label is not approved for low-cardinality export: " + label);
            }
        });
        return new TelemetryEvent(name, configurationProvider.current().instanceId(), labels);
    }
}
