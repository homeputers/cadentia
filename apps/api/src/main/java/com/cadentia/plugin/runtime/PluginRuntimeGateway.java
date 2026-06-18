package com.cadentia.plugin.runtime;

import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeResult;

public interface PluginRuntimeGateway {
    PluginRuntimeResult execute(PluginRuntimeInvocation invocation);
}
