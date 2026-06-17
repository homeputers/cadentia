package com.cadentia.plugin;

import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("plugin-in-memory")
public class InMemoryPluginRegistryRepository implements PluginRegistryRepository {
    private final Map<UUID, PluginPackage> packages = new ConcurrentHashMap<>();
    private final Map<UUID, PluginConfigurationSnapshot> configurations = new ConcurrentHashMap<>();
    private final Map<String, PluginEnablement> enablements = new ConcurrentHashMap<>();

    @Override
    public PluginPackage savePackage(PluginPackage pluginPackage) {
        packages.put(pluginPackage.pluginVersionId(), pluginPackage);
        return pluginPackage;
    }

    @Override
    public Optional<PluginPackage> findPackage(UUID pluginVersionId) {
        return Optional.ofNullable(packages.get(pluginVersionId));
    }

    @Override
    public List<PluginPackage> findByStablePluginId(String stablePluginId) {
        return packages.values().stream()
                .filter(pluginPackage -> pluginPackage.stablePluginId().equals(stablePluginId))
                .sorted(Comparator.comparing(PluginPackage::semanticVersion))
                .toList();
    }

    @Override
    public List<PluginPackage> findAllPackages() {
        return packages.values().stream().sorted(Comparator.comparing(PluginPackage::semanticVersion)).toList();
    }

    @Override
    public PluginConfigurationSnapshot saveConfiguration(PluginConfigurationSnapshot snapshot) {
        configurations.put(snapshot.configurationVersionId(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<PluginConfigurationSnapshot> findConfiguration(UUID configurationVersionId) {
        return Optional.ofNullable(configurations.get(configurationVersionId));
    }

    @Override
    public PluginEnablement saveEnablement(PluginEnablement enablement) {
        enablements.put(key(enablement.churchInstanceId(), enablement.environment(), enablement.extensionPoint()), enablement);
        return enablement;
    }

    @Override
    public Optional<PluginEnablement> findEnablement(String churchInstanceId, PluginModels.Environment environment, String extensionPoint) {
        return Optional.ofNullable(enablements.get(key(churchInstanceId, environment, extensionPoint)));
    }

    @Override
    public List<PluginEnablement> findEnablements(UUID pluginVersionId) {
        return new ArrayList<>(enablements.values()).stream()
                .filter(enablement -> enablement.pluginVersionId().equals(pluginVersionId))
                .toList();
    }

    private static String key(String churchInstanceId, PluginModels.Environment environment, String extensionPoint) {
        return churchInstanceId + "|" + environment + "|" + extensionPoint;
    }
}
