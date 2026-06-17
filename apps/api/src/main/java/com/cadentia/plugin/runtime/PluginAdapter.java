package com.cadentia.plugin.runtime;

import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginAdapterOutput;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginInvocationEnvelope;

public interface PluginAdapter {
    PluginAdapterOutput execute(PluginInvocationEnvelope envelope);
}
