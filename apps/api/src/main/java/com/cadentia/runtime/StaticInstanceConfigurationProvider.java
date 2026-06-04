package com.cadentia.runtime;

public class StaticInstanceConfigurationProvider implements InstanceConfigurationProvider {
    private final InstanceConfiguration configuration;

    public StaticInstanceConfigurationProvider(InstanceConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public InstanceConfiguration current() {
        return configuration;
    }
}
