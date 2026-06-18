package com.cadentia.plugin.runtime;

import com.cadentia.plugin.PluginModels.PluginPackage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Process-local catalog of executable adapter beans. Durable plugin package, configuration,
 * enablement, and revocation state remains in {@link com.cadentia.plugin.PluginRegistryRepository};
 * this registry is rebuilt from Spring-managed {@link PluginAdapterRegistration} beans on restart
 * and is only an execution allowlist for code that is present in the current deployment.
 */
@Component
public class PluginAdapterRegistry {
    private final Map<AdapterKey, PluginAdapter> adapters = new ConcurrentHashMap<>();

    @Autowired
    public PluginAdapterRegistry(List<PluginAdapterRegistration> registrations) {
        registrations.forEach(this::register);
    }

    public PluginAdapterRegistry() {
        this(List.of());
    }

    public void register(PluginAdapterRegistration registration) {
        adapters.put(AdapterKey.from(registration), registration.adapter());
    }

    public void register(UUID pluginVersionId, PluginAdapter adapter) {
        adapters.put(AdapterKey.legacy(pluginVersionId), adapter);
    }

    public Optional<PluginAdapter> find(PluginPackage pluginPackage, String extensionPoint) {
        return Optional.ofNullable(adapters.get(AdapterKey.from(pluginPackage, extensionPoint)))
                .or(() -> Optional.ofNullable(adapters.get(AdapterKey.legacy(pluginPackage.pluginVersionId()))));
    }

    private record AdapterKey(
            String stablePluginId,
            String semanticVersion,
            String extensionPoint,
            UUID pluginVersionId) {
        private static AdapterKey from(PluginAdapterRegistration registration) {
            return new AdapterKey(registration.stablePluginId(), registration.semanticVersion(), registration.extensionPoint(), null);
        }

        private static AdapterKey from(PluginPackage pluginPackage, String extensionPoint) {
            return new AdapterKey(pluginPackage.stablePluginId(), pluginPackage.semanticVersion(), extensionPoint, null);
        }

        private static AdapterKey legacy(UUID pluginVersionId) {
            return new AdapterKey(null, null, null, pluginVersionId);
        }
    }
}
