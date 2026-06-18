package com.cadentia.plugin.spi;

import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.policy.PluginPolicyModels.SanitizedPluginOutput;
import com.cadentia.plugin.runtime.PluginRuntimeGateway;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExecutionStatus;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginExecutionMetadata;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeResult;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.ExportArtifact;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.ExportJobResult;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.ExportRequest;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.IntegrationAttempt;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.IntegrationFailureKind;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.JobStatus;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.OutboundPublishRequest;
import com.cadentia.plugin.spi.ExportOutboundExtensionModels.OutboundPublishResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ExportOutboundExtensionService {
    private final PluginRuntimeGateway runtimeService;
    private final PluginRegistryAuditRecorder auditRecorder;
    private final Set<String> completedIdempotencyKeys = ConcurrentHashMap.newKeySet();

    public ExportOutboundExtensionService(PluginRuntimeGateway runtimeService, PluginRegistryAuditRecorder auditRecorder) {
        this.runtimeService = runtimeService;
        this.auditRecorder = auditRecorder;
    }

    public ExportJobResult renderExport(PluginRuntimeInvocation invocation, ExportRequest request) {
        PluginRuntimeResult result = runtimeService.execute(invocation);
        if (result.status() != ExecutionStatus.SUCCEEDED) {
            auditRecorder.record("PLUGIN_EXPORT_FAILED", null, invocation.actorId());
            return new ExportJobResult(status(result.status()), null, result.safeErrors(), List.of());
        }
        SanitizedPluginOutput output = firstOutput(result);
        PluginExecutionMetadata metadata = firstMetadata(result);
        List<String> errors = validateExport(output, request);
        if (!errors.isEmpty()) {
            auditRecorder.record("PLUGIN_EXPORT_REJECTED", metadata.pluginVersionId(), invocation.actorId());
            return new ExportJobResult(JobStatus.DEGRADED, null, errors, List.of());
        }
        Map<String, Object> attributes = output.attributes();
        ExportArtifact artifact = new ExportArtifact(UUID.randomUUID(), request.sourceSnapshotId(), request.sourceType(),
                string(attributes, "artifactRef"), string(attributes, "mimeType"), string(attributes, "filename"),
                string(attributes, "checksumSha256"), metadata.stablePluginId(), metadata.semanticVersion(), metadata.pluginVersionId(),
                metadata.configurationVersionId(), request.generationTimestamp(), invocation.actorId(), provenance(attributes),
                output.songIds(), output.assetIds(), output.reviewNoteIds(), output.licenseScopes(), true);
        auditRecorder.record("PLUGIN_EXPORT_ARTIFACT_CREATED", metadata.pluginVersionId(), invocation.actorId());
        return new ExportJobResult(JobStatus.SUCCEEDED, artifact, List.of(), warnings(output));
    }

    public OutboundPublishResult publishOutbound(PluginRuntimeInvocation invocation, OutboundPublishRequest request) {
        List<String> requestErrors = validateOutboundRequest(request);
        if (!requestErrors.isEmpty()) {
            auditRecorder.record("PLUGIN_OUTBOUND_REJECTED", null, invocation.actorId());
            return new OutboundPublishResult(JobStatus.FAILED, failedAttempt(invocation, request, null, requestErrors,
                    IntegrationFailureKind.NON_RETRYABLE));
        }
        if (completedIdempotencyKeys.contains(request.idempotencyKey())) {
            auditRecorder.record("PLUGIN_OUTBOUND_DUPLICATE_SUPPRESSED", null, invocation.actorId());
            return new OutboundPublishResult(JobStatus.DUPLICATE_SUPPRESSED, failedAttempt(invocation, request, null,
                    List.of("PLUGIN_OUTBOUND_DUPLICATE_SUPPRESSED"), IntegrationFailureKind.NONE));
        }
        PluginRuntimeResult result = runtimeService.execute(invocation);
        PluginExecutionMetadata metadata = result.metadata().isEmpty() ? null : result.metadata().get(0);
        if (result.status() != ExecutionStatus.SUCCEEDED) {
            IntegrationFailureKind kind = retryable(result.status()) ? IntegrationFailureKind.RETRYABLE : IntegrationFailureKind.NON_RETRYABLE;
            auditRecorder.record(retryable(result.status()) ? "PLUGIN_OUTBOUND_RETRY_SCHEDULED" : "PLUGIN_OUTBOUND_FAILED",
                    metadata == null ? null : metadata.pluginVersionId(), invocation.actorId());
            return new OutboundPublishResult(status(result.status()), failedAttempt(invocation, request, metadata, result.safeErrors(), kind));
        }
        SanitizedPluginOutput output = firstOutput(result);
        List<String> outputErrors = validateOutboundOutput(output);
        if (!outputErrors.isEmpty()) {
            auditRecorder.record("PLUGIN_OUTBOUND_REJECTED", metadata.pluginVersionId(), invocation.actorId());
            return new OutboundPublishResult(JobStatus.FAILED, failedAttempt(invocation, request, metadata, outputErrors,
                    IntegrationFailureKind.NON_RETRYABLE));
        }
        IntegrationAttempt attempt = new IntegrationAttempt(UUID.randomUUID(), request.sourceSnapshotId(), request.sourceType(),
                request.eventSchemaId(), request.idempotencyKey(), request.correlationId(), request.credentialRef(),
                string(output.attributes(), "externalReference"), string(output.attributes(), "reconciliationStatus"),
                metadata.stablePluginId(), metadata.semanticVersion(), metadata.pluginVersionId(), metadata.configurationVersionId(),
                Instant.now(), intValue(output.attributes(), "attemptNumber", 1), IntegrationFailureKind.NONE, List.of());
        completedIdempotencyKeys.add(request.idempotencyKey());
        auditRecorder.record("PLUGIN_OUTBOUND_ATTEMPT_RECORDED", metadata.pluginVersionId(), invocation.actorId());
        return new OutboundPublishResult(JobStatus.SUCCEEDED, attempt);
    }

    private List<String> validateExport(SanitizedPluginOutput output, ExportRequest request) {
        List<String> errors = new ArrayList<>();
        if (output == null) {
            errors.add("PLUGIN_EXPORT_ARTIFACT_INVALID");
            return errors;
        }
        if (blank(string(output.attributes(), "artifactRef")) || blank(string(output.attributes(), "checksumSha256"))) {
            errors.add("PLUGIN_EXPORT_ARTIFACT_INVALID");
        }
        if (!request.licenseScopes().containsAll(output.licenseScopes())) {
            errors.add("PLUGIN_EXPORT_LICENSE_SCOPE_DENIED");
        }
        if (!request.authorizedAssetIds().containsAll(output.assetIds()) || !request.authorizedReviewNoteIds().containsAll(output.reviewNoteIds())) {
            errors.add("PLUGIN_EXPORT_UNAUTHORIZED_CONTENT");
        }
        return errors;
    }

    private List<String> validateOutboundRequest(OutboundPublishRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null || blank(request.idempotencyKey()) || blank(request.correlationId())) {
            errors.add("PLUGIN_OUTBOUND_GOVERNANCE_MISSING");
        }
        if (request != null && (blank(request.credentialRef()) || request.credentialRef().startsWith("secret:")
                || request.credentialRef().contains("-----BEGIN") || request.credentialRef().contains("password="))) {
            errors.add("PLUGIN_OUTBOUND_CREDENTIAL_REF_INVALID");
        }
        if (request != null && request.retryPolicy() == null) {
            errors.add("PLUGIN_OUTBOUND_RETRY_POLICY_MISSING");
        }
        return errors;
    }

    private List<String> validateOutboundOutput(SanitizedPluginOutput output) {
        if (output == null || output.attributes().containsKey("rawSecret") || output.attributes().containsKey("accessToken")) {
            return List.of("PLUGIN_OUTBOUND_SECRET_LEAK");
        }
        return List.of();
    }

    private IntegrationAttempt failedAttempt(PluginRuntimeInvocation invocation, OutboundPublishRequest request,
            PluginExecutionMetadata metadata, List<String> errors, IntegrationFailureKind kind) {
        return new IntegrationAttempt(UUID.randomUUID(), request == null ? null : request.sourceSnapshotId(),
                request == null ? null : request.sourceType(), request == null ? null : request.eventSchemaId(),
                request == null ? invocation.idempotencyKey() : request.idempotencyKey(), request == null ? null : request.correlationId(),
                request == null ? null : request.credentialRef(), null, "FAILED", metadata == null ? null : metadata.stablePluginId(),
                metadata == null ? null : metadata.semanticVersion(), metadata == null ? null : metadata.pluginVersionId(),
                metadata == null ? null : metadata.configurationVersionId(), Instant.now(), 1, kind, errors);
    }

    private SanitizedPluginOutput firstOutput(PluginRuntimeResult result) {
        return result.outputs().isEmpty() ? null : result.outputs().get(0);
    }

    private PluginExecutionMetadata firstMetadata(PluginRuntimeResult result) {
        return result.metadata().get(0);
    }

    private JobStatus status(ExecutionStatus status) {
        return switch (status) {
            case SUCCEEDED -> JobStatus.SUCCEEDED;
            case RETRY_SCHEDULED -> JobStatus.RETRY_SCHEDULED;
            case DEGRADED -> JobStatus.DEGRADED;
            case FAILED_CLOSED, JOB_FAILED, ADMIN_INTERVENTION_REQUIRED -> JobStatus.FAILED;
        };
    }

    private boolean retryable(ExecutionStatus status) {
        return status == ExecutionStatus.RETRY_SCHEDULED || status == ExecutionStatus.JOB_FAILED;
    }

    private Map<String, String> provenance(Map<String, Object> attributes) {
        Map<String, String> provenance = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key.startsWith("provenance.") && value instanceof String stringValue) {
                provenance.put(key.substring("provenance.".length()), stringValue);
            }
        });
        return Map.copyOf(provenance);
    }

    private List<String> warnings(SanitizedPluginOutput output) {
        Object value = output.attributes().get("warnings");
        if (value instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private String string(Map<String, Object> attributes, String key) {
        Object value = attributes == null ? null : attributes.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    private int intValue(Map<String, Object> attributes, String key, int fallback) {
        Object value = attributes.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
