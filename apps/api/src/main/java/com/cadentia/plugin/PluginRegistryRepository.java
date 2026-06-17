package com.cadentia.plugin;

import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PluginRegistryRepository {

    PluginPackage savePackage(PluginPackage pluginPackage);

    Optional<PluginPackage> findPackage(UUID pluginVersionId);

    List<PluginPackage> findByStablePluginId(String stablePluginId);

    List<PluginPackage> findAllPackages();

    PluginConfigurationSnapshot saveConfiguration(PluginConfigurationSnapshot snapshot);

    Optional<PluginConfigurationSnapshot> findConfiguration(UUID configurationVersionId);

    PluginEnablement saveEnablement(PluginEnablement enablement);

    Optional<PluginEnablement> findEnablement(String churchInstanceId, PluginModels.Environment environment, String extensionPoint);

    List<PluginEnablement> findEnablements(UUID pluginVersionId);
}
