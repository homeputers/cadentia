package com.cadentia.runtime;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PluginRegistry {
    private final InstanceConfigurationProvider configurationProvider;

    public PluginRegistry(InstanceConfigurationProvider configurationProvider) {
        this.configurationProvider = configurationProvider;
    }

    public InstanceConfiguration.PluginDefinition requireEnabledPlugin(String pluginRef) {
        Optional<InstanceConfiguration.PluginDefinition> plugin = configurationProvider.current().enabledPlugin(pluginRef);
        return plugin.orElseThrow(() -> new RuntimeModuleAccessException(
                "Plugin is not enabled by this instance allow-list: " + pluginRef));
    }
}
