package com.cadentia.plugin;

import com.cadentia.api.security.PluginRegistryAuthorizationPolicy;
import com.cadentia.plugin.PluginModels.ConfigurePluginCommand;
import com.cadentia.plugin.PluginModels.EnablePluginCommand;
import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import com.cadentia.plugin.PluginModels.RegisterPluginCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PluginRegistryService {
    private final PluginRegistryRepository repository;
    private final PluginConfigurationValidator validator;
    private final PluginRegistryAuthorizationPolicy authorizationPolicy;
    private final PluginRegistryAuditRecorder auditRecorder;
    private final Clock clock;

    @Autowired
    public PluginRegistryService(PluginRegistryRepository repository, PluginConfigurationValidator validator,
            PluginRegistryAuthorizationPolicy authorizationPolicy, PluginRegistryAuditRecorder auditRecorder) {
        this(repository, validator, authorizationPolicy, auditRecorder, Clock.systemUTC());
    }

    public PluginRegistryService(PluginRegistryRepository repository, PluginConfigurationValidator validator,
            PluginRegistryAuthorizationPolicy authorizationPolicy, PluginRegistryAuditRecorder auditRecorder, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.authorizationPolicy = authorizationPolicy;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    public PluginPackage registerApprovedPackage(RegisterPluginCommand command) {
        authorizationPolicy.requireManageIntegrations();
        requireSemver(command.semanticVersion());
        Instant now = clock.instant();
        PluginPackage pluginPackage = new PluginPackage(UUID.randomUUID(), command.stablePluginId(), command.packageName(),
                command.provider(), command.semanticVersion(), List.copyOf(command.supportedSpiVersions()),
                List.copyOf(command.extensionPoints()), command.trustTier(), command.checksumSha256(), command.signatureRef(),
                command.certificationStatus(), command.installationSource(), LifecycleStatus.APPROVED,
                PluginModels.DeprecationStatus.ACTIVE, command.configurationSchema(), now, now, command.actor(), command.actor());
        PluginPackage saved = repository.savePackage(pluginPackage);
        auditRecorder.record("PLUGIN_REGISTERED", saved.pluginVersionId(), command.actor());
        return saved;
    }

    public PluginConfigurationSnapshot updateConfiguration(ConfigurePluginCommand command) {
        authorizationPolicy.requireManageIntegrations();
        PluginPackage pluginPackage = packageOrThrow(command.pluginVersionId());
        requireExtension(pluginPackage, command.extensionPoint());
        validator.validate(pluginPackage.configurationSchema(), command.configurationValues(), command.secretRefs());
        PluginConfigurationSnapshot snapshot = new PluginConfigurationSnapshot(UUID.randomUUID(), command.pluginVersionId(),
                command.churchInstanceId(), command.environment(), command.extensionPoint(), command.configurationValues(),
                command.secretRefs(), clock.instant(), command.actor());
        PluginConfigurationSnapshot saved = repository.saveConfiguration(snapshot);
        auditRecorder.record("PLUGIN_CONFIGURATION_CHANGED", saved.pluginVersionId(), command.actor());
        return saved;
    }

    public PluginEnablement enable(EnablePluginCommand command) {
        authorizationPolicy.requireManageIntegrations();
        PluginPackage pluginPackage = packageOrThrow(command.pluginVersionId());
        requireExtension(pluginPackage, command.extensionPoint());
        PluginConfigurationSnapshot snapshot = repository.findConfiguration(command.configurationVersionId())
                .orElseThrow(() -> new PluginRegistryException(List.of("configuration snapshot not found")));
        if (!snapshot.pluginVersionId().equals(command.pluginVersionId())
                || !snapshot.churchInstanceId().equals(command.churchInstanceId())
                || snapshot.environment() != command.environment()
                || !snapshot.extensionPoint().equals(command.extensionPoint())) {
            throw new PluginRegistryException(List.of("configuration snapshot scope does not match enablement scope"));
        }
        PluginEnablement enablement = new PluginEnablement(UUID.randomUUID(), command.pluginVersionId(),
                command.configurationVersionId(), command.churchInstanceId(), command.environment(), command.extensionPoint(),
                LifecycleStatus.ENABLED, clock.instant(), null, command.actor(), null);
        PluginEnablement saved = repository.saveEnablement(enablement);
        auditRecorder.record("PLUGIN_ENABLED", saved.pluginVersionId(), command.actor());
        return saved;
    }

    public PluginEnablement disable(String churchInstanceId, PluginModels.Environment environment, String extensionPoint, String actor) {
        authorizationPolicy.requireManageIntegrations();
        PluginEnablement current = repository.findEnablement(churchInstanceId, environment, extensionPoint)
                .orElseThrow(() -> new PluginRegistryException(List.of("enablement not found")));
        PluginEnablement disabled = new PluginEnablement(current.enablementId(), current.pluginVersionId(),
                current.configurationVersionId(), churchInstanceId, environment, extensionPoint, LifecycleStatus.DISABLED,
                current.enabledAt(), clock.instant(), current.enabledBy(), actor);
        PluginEnablement saved = repository.saveEnablement(disabled);
        auditRecorder.record("PLUGIN_DISABLED", saved.pluginVersionId(), actor);
        return saved;
    }

    public boolean canExecute(String churchInstanceId, PluginModels.Environment environment, String extensionPoint, UUID pluginVersionId) {
        return repository.findEnablement(churchInstanceId, environment, extensionPoint)
                .filter(enablement -> enablement.status() == LifecycleStatus.ENABLED)
                .filter(enablement -> enablement.pluginVersionId().equals(pluginVersionId))
                .isPresent();
    }

    public List<PluginPackage> versionHistory(String stablePluginId) {
        return repository.findByStablePluginId(stablePluginId);
    }

    public PluginPackage revoke(UUID pluginVersionId, String actor) {
        authorizationPolicy.requireManageIntegrations();
        PluginPackage current = packageOrThrow(pluginVersionId);
        PluginPackage revoked = new PluginPackage(current.pluginVersionId(), current.stablePluginId(), current.packageName(),
                current.provider(), current.semanticVersion(), current.supportedSpiVersions(), current.extensionPoints(),
                current.trustTier(), current.checksumSha256(), current.signatureRef(), current.certificationStatus(),
                current.installationSource(), LifecycleStatus.REVOKED, current.deprecationStatus(), current.configurationSchema(),
                current.createdAt(), clock.instant(), current.createdBy(), actor);
        PluginPackage saved = repository.savePackage(revoked);
        auditRecorder.record("PLUGIN_REVOKED", saved.pluginVersionId(), actor);
        return saved;
    }

    private PluginPackage packageOrThrow(UUID pluginVersionId) {
        return repository.findPackage(pluginVersionId).orElseThrow(() -> new PluginRegistryException(List.of("plugin version not found")));
    }

    private static void requireExtension(PluginPackage pluginPackage, String extensionPoint) {
        if (!pluginPackage.extensionPoints().contains(extensionPoint)) {
            throw new PluginRegistryException(List.of("plugin does not implement extension point"));
        }
    }

    private static void requireSemver(String value) {
        if (value == null || !value.matches("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$")) {
            throw new PluginRegistryException(List.of("semantic version must be semver"));
        }
    }
}
