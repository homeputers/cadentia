package com.cadentia.asset;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean(AssetStorageAdapter.class)
    AssetStorageAdapter unsupportedConfiguredAssetStorageAdapter(AssetStorageProperties properties) {
        return new UnsupportedConfiguredAssetStorageAdapter(properties);
    }
}
