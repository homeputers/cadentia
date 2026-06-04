package com.cadentia.runtime;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuntimeResourceIdentifiers {
    private final InstanceConfigurationProvider configurationProvider;

    public RuntimeResourceIdentifiers(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public String assetObjectKey(String relativePath) {
        InstanceConfiguration configuration = configurationProvider.current();
        String cleanPath = relativePath == null ? "" : relativePath.replaceFirst("^/+", "");
        return configuration.assetStorage().namespacePrefix() + "/" + cleanPath;
    }

    public String cacheKey(String key) {
        return configurationProvider.current().namespaces().cacheNamespace() + ":" + key;
    }

    public String eventStream(String streamName) {
        InstanceConfiguration.RuntimeNamespaces namespaces = configurationProvider.current().namespaces();
        String fullName = namespaces.eventNamespace() + "." + streamName;
        if (!namespaces.eventStreams().contains(fullName)) {
            throw new RuntimeModuleAccessException("Event stream is not configured for this instance: " + fullName);
        }
        return fullName;
    }

    public String backgroundJobQueue(String queueName) {
        return configurationProvider.current().namespaces().eventNamespace() + ".jobs." + queueName;
    }

    public List<String> configuredEventStreams() {
        return configurationProvider.current().namespaces().eventStreams();
    }
}
