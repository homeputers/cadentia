package com.cadentia.plugin.runtime;

/**
 * Deployment-time binding between a durable plugin package identity and the adapter implementation
 * shipped with the application or approved sidecar bridge.
 */
public record PluginAdapterRegistration(
        String stablePluginId,
        String semanticVersion,
        String extensionPoint,
        PluginAdapter adapter) {
}
