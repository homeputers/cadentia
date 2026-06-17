package com.cadentia.plugin.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PluginAdapterRegistry {
    private final Map<UUID, PluginAdapter> adapters = new ConcurrentHashMap<>();

    public void register(UUID pluginVersionId, PluginAdapter adapter) {
        adapters.put(pluginVersionId, adapter);
    }

    public Optional<PluginAdapter> find(UUID pluginVersionId) {
        return Optional.ofNullable(adapters.get(pluginVersionId));
    }
}
