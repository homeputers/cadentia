package com.cadentia.plugin.policy;

import com.cadentia.plugin.PluginModels.LifecycleStatus;
import com.cadentia.plugin.PluginModels.PluginConfigurationSnapshot;
import com.cadentia.plugin.PluginModels.PluginEnablement;
import com.cadentia.plugin.PluginModels.PluginPackage;
import com.cadentia.plugin.PluginModels.TrustTier;
import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.PluginRegistryRepository;
import com.cadentia.plugin.policy.PluginPolicyModels.CanonicalPolicySnapshot;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginExecutionPolicy;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginInvocationRequest;
import com.cadentia.plugin.policy.PluginPolicyModels.PluginOutput;
import com.cadentia.plugin.policy.PluginPolicyModels.SanitizedPluginOutput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PluginPolicyEnforcementService {
    private static final Set<String> BASE_FIELDS = Set.of("requestId", "locale", "candidateIds", "format", "options");
    private static final Set<String> VERIFIED_FIELDS = Set.of("requestId", "locale", "candidateIds", "format", "options",
            "servicePlanId", "assetIds", "arrangementIds");
    private static final Set<String> CORE_FIELDS = Set.of("requestId", "locale", "candidateIds", "format", "options",
            "servicePlanId", "assetIds", "arrangementIds", "reviewNoteIds");

    private final PluginRegistryRepository repository;
    private final PluginRegistryAuditRecorder auditRecorder;

    public PluginPolicyEnforcementService(PluginRegistryRepository repository, PluginRegistryAuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    public PluginExecutionPolicy authorize(PluginInvocationRequest request, CanonicalPolicySnapshot canonical) {
        if (request == null || canonical == null || request.pluginVersionId() == null
                || request.configurationVersionId() == null || request.churchInstanceId() == null
                || request.environment() == null || request.extensionPoint() == null || request.requiredSpiVersion() == null) {
            deny(null, request == null ? null : request.actorId(), "PLUGIN_POLICY_CONTEXT_MISSING");
        }
        PluginPackage pluginPackage = repository.findPackage(request.pluginVersionId())
                .orElseThrow(() -> denied(request.pluginVersionId(), request.actorId(), "PLUGIN_NOT_ALLOWED"));
        PluginConfigurationSnapshot configuration = repository.findConfiguration(request.configurationVersionId())
                .orElseThrow(() -> denied(request.pluginVersionId(), request.actorId(), "PLUGIN_NOT_ALLOWED"));
        PluginEnablement enablement = repository.findEnablement(request.churchInstanceId(), request.environment(), request.extensionPoint())
                .orElseThrow(() -> denied(request.pluginVersionId(), request.actorId(), "PLUGIN_DISABLED"));

        require(enablement.status() == LifecycleStatus.ENABLED, request, "PLUGIN_DISABLED");
        require(pluginPackage.lifecycleStatus() == LifecycleStatus.ENABLED || pluginPackage.lifecycleStatus() == LifecycleStatus.APPROVED,
                request, "PLUGIN_NOT_ALLOWED");
        require(pluginPackage.pluginVersionId().equals(request.pluginVersionId()), request, "PLUGIN_NOT_ALLOWED");
        require(enablement.pluginVersionId().equals(request.pluginVersionId()), request, "PLUGIN_NOT_ALLOWED");
        require(enablement.configurationVersionId().equals(request.configurationVersionId()), request, "PLUGIN_NOT_ALLOWED");
        require(configuration.pluginVersionId().equals(request.pluginVersionId()), request, "PLUGIN_NOT_ALLOWED");
        require(configuration.churchInstanceId().equals(request.churchInstanceId()), request, "PLUGIN_SCOPE_DENIED");
        require(configuration.environment() == request.environment(), request, "PLUGIN_SCOPE_DENIED");
        require(configuration.extensionPoint().equals(request.extensionPoint()), request, "PLUGIN_SCOPE_DENIED");
        require(pluginPackage.extensionPoints().contains(request.extensionPoint()), request, "PLUGIN_EXTENSION_DENIED");
        require(pluginPackage.supportedSpiVersions().contains(request.requiredSpiVersion()), request, "PLUGIN_VERSION_DENIED");
        require(canonical.visibleInstanceIds().contains(request.churchInstanceId()), request, "PLUGIN_SCOPE_DENIED");
        require(canonical.permittedRoles().stream().anyMatch(request.actorRoles()::contains), request, "PLUGIN_AUTHORITY_DENIED");
        require(licensesAllowed(request, canonical), request, "PLUGIN_LICENSE_DENIED");

        return new PluginExecutionPolicy(request.pluginVersionId(), request.configurationVersionId(), request.churchInstanceId(),
                request.environment(), request.extensionPoint(), pluginPackage.trustTier(), UUID.randomUUID().toString(),
                minimalInputView(request.requestedInput(), pluginPackage.trustTier(), canonical));
    }

    public SanitizedPluginOutput sanitizeOutput(PluginExecutionPolicy policy, PluginOutput output, CanonicalPolicySnapshot canonical) {
        if (policy == null || output == null || canonical == null) {
            deny(policy == null ? null : policy.pluginVersionId(), null, "PLUGIN_OUTPUT_INVALID");
        }
        List<String> stripped = new ArrayList<>();
        List<String> songs = retain(output.songIds(), canonical.visibleSongIds(), "songIds", stripped);
        List<String> recommendable = retain(output.recommendableSongIds(), canonical.recommendableSongIds(), "recommendableSongIds", stripped);
        List<String> arrangements = retain(output.arrangementIds(), canonical.arrangementIds(), "arrangementIds", stripped);
        List<String> assets = retain(output.assetIds(), canonical.assetIds(), "assetIds", stripped);
        List<String> people = retain(output.peopleIds(), canonical.peopleIds(), "peopleIds", stripped);
        List<String> plans = retain(output.servicePlanIds(), canonical.servicePlanIds(), "servicePlanIds", stripped);
        List<String> notes = retain(output.reviewNoteIds(), canonical.readableReviewNoteIds(), "reviewNoteIds", stripped);
        List<String> licenses = retain(output.licenseScopes(), canonical.licenseScopes(), "licenseScopes", stripped);
        List<String> instances = retain(output.instanceIds(), Set.of(policy.churchInstanceId()), "instanceIds", stripped);
        Map<String, Object> attributes = new LinkedHashMap<>(output.attributes() == null ? Map.of() : output.attributes());
        attributes.remove("approvalStatus");
        attributes.remove("licenseOverride");
        attributes.remove("instanceId");
        return new SanitizedPluginOutput(songs, recommendable, arrangements, assets, people, plans, notes, licenses,
                instances, attributes, List.copyOf(stripped));
    }

    private Map<String, Object> minimalInputView(Map<String, Object> requested, TrustTier trustTier, CanonicalPolicySnapshot canonical) {
        Set<String> allowed = trustTier == TrustTier.CORE ? CORE_FIELDS : trustTier == TrustTier.VERIFIED ? VERIFIED_FIELDS : BASE_FIELDS;
        Map<String, Object> view = new LinkedHashMap<>();
        (requested == null ? Map.<String, Object>of() : requested).forEach((key, value) -> {
            if (allowed.contains(key)) {
                view.put(key, filterValue(key, value, canonical));
            }
        });
        return Map.copyOf(view);
    }

    private Object filterValue(String key, Object value, CanonicalPolicySnapshot canonical) {
        if ("candidateIds".equals(key) && value instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(canonical.approvedSongIds()::contains).filter(canonical.visibleSongIds()::contains).toList();
        }
        if ("reviewNoteIds".equals(key) && value instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(canonical.readableReviewNoteIds()::contains).toList();
        }
        return value;
    }

    private boolean licensesAllowed(PluginInvocationRequest request, CanonicalPolicySnapshot canonical) {
        Set<String> packageScopes = canonical.packageLicenseScopes().get(request.packageName());
        return packageScopes != null && canonical.licenseScopes().containsAll(request.licenseScopes())
                && packageScopes.containsAll(request.licenseScopes());
    }

    private static List<String> retain(List<String> values, Set<String> allowed, String field, List<String> stripped) {
        if (values == null) {
            return List.of();
        }
        List<String> retained = values.stream().filter(allowed::contains).toList();
        if (retained.size() != values.size()) {
            stripped.add(field);
        }
        return retained;
    }

    private void require(boolean condition, PluginInvocationRequest request, String code) {
        if (!condition) {
            deny(request.pluginVersionId(), request.actorId(), code);
        }
    }

    private PluginPolicyException denied(UUID pluginVersionId, String actor, String code) {
        auditRecorder.record("PLUGIN_POLICY_DENIED", pluginVersionId, actor);
        return new PluginPolicyException(code, "Plugin execution is not permitted by policy.");
    }

    private void deny(UUID pluginVersionId, String actor, String code) {
        throw denied(pluginVersionId, actor, code);
    }
}
