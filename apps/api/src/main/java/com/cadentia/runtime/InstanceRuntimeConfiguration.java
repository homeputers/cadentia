package com.cadentia.runtime;

import com.cadentia.config.ChurchConfigPackageValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class InstanceRuntimeConfiguration {

    @Bean
    InstanceConfigurationProvider instanceConfigurationProvider(
            ObjectMapper objectMapper,
            @Value("${cadentia.church-config.path:}") String packagePath,
            @Value("${cadentia.application.version:0.1.0}") String applicationVersion,
            @Value("${cadentia.instance.id:local-development}") String instanceId,
            @Value("${cadentia.asset-storage.provider:local}") String assetProvider,
            @Value("${cadentia.asset-storage.bucket:cadentia-local-assets}") String assetBucket,
            @Value("${cadentia.asset-storage.namespace:local-development}") String assetNamespace,
            @Value("${cadentia.asset-storage.encryption-key-ref:env:CADENTIA_LOCAL_ASSET_KEY_REF}") String assetEncryptionKeyRef,
            @Value("${cadentia.cache.namespace:cadentia:local:development}") String cacheNamespace,
            @Value("${cadentia.events.namespace:local.development}") String eventNamespace,
            @Value("${cadentia.events.streams:local.development.audit-events,local.development.recommendation-events}") String eventStreams)
            throws Exception {
        if (StringUtils.hasText(packagePath)) {
            JsonNode root = objectMapper.readTree(Files.readString(Path.of(packagePath)));
            new ChurchConfigPackageValidator().validate(root, applicationVersion);
            return new StaticInstanceConfigurationProvider(InstanceConfiguration.fromPackage(root));
        }
        List<String> streamList = Arrays.stream(eventStreams.split(","))
                .map(String::trim)
                .filter(stream -> !stream.isBlank())
                .toList();
        return new StaticInstanceConfigurationProvider(InstanceConfiguration.localDevelopment(
                instanceId,
                assetProvider,
                assetBucket,
                assetNamespace,
                assetEncryptionKeyRef,
                cacheNamespace,
                eventNamespace,
                streamList));
    }
}
