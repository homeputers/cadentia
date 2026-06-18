package com.cadentia.plugin.spi;

import com.cadentia.catalog.model.ImportCandidateStatus;
import com.cadentia.catalog.model.LicenseType;
import com.cadentia.plugin.PluginRegistryAuditRecorder;
import com.cadentia.plugin.runtime.PluginRuntimeModels.ExecutionStatus;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeInvocation;
import com.cadentia.plugin.runtime.PluginRuntimeModels.PluginRuntimeResult;
import com.cadentia.plugin.runtime.PluginRuntimeGateway;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.ImportConnectorCandidateV1;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.ImportConnectorJobResult;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.JobStatus;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.MetadataTransformChangeSet;
import com.cadentia.plugin.spi.ImportTransformExtensionModels.MetadataTransformJobResult;
import com.cadentia.songimport.CandidateValidationResult;
import com.cadentia.songimport.ConnectorExecutionContext;
import com.cadentia.songimport.NormalizedImportCandidate;
import com.cadentia.songimport.SharedImportPipeline;
import com.cadentia.songimport.StagedImportCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ImportTransformExtensionService {
    private static final Set<String> RESERVED_TRANSFORM_FIELDS = Set.of(
            "approvalStatus", "approved", "recommendable", "recommendationEligible", "canonicalSongId");
    private static final Set<String> GOVERNED_TAG_PREFIXES = Set.of("theme:", "scripture:", "function:", "season:");
    private static final Set<LicenseType> ACCEPTED_LICENSE_TYPES = Set.of(
            LicenseType.PUBLIC_DOMAIN, LicenseType.CCLI, LicenseType.DIRECT_PERMISSION, LicenseType.FAIR_USE_REFERENCE,
            LicenseType.NOT_APPLICABLE);

    private final PluginRuntimeGateway runtimeService;
    private final SharedImportPipeline importPipeline;
    private final PluginRegistryAuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ImportTransformExtensionService(PluginRuntimeGateway runtimeService, SharedImportPipeline importPipeline,
            PluginRegistryAuditRecorder auditRecorder, ObjectMapper objectMapper) {
        this.runtimeService = runtimeService;
        this.importPipeline = importPipeline;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    public ImportConnectorJobResult runImportConnector(PluginRuntimeInvocation invocation, ConnectorExecutionContext context) {
        PluginRuntimeResult result = runtimeService.execute(invocation);
        if (result.status() != ExecutionStatus.SUCCEEDED) {
            auditRecorder.record("PLUGIN_IMPORT_JOB_FAILED", null, invocation.actorId());
            return new ImportConnectorJobResult(JobStatus.FAILED, List.of(), result.safeErrors(), retryable(result.status()), null);
        }
        List<StagedImportCandidate> staged = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Object candidateObject : attributeList(result, "stagedImportCandidates")) {
            ImportConnectorCandidateV1 candidate = candidateObject instanceof ImportConnectorCandidateV1 typedCandidate
                    ? typedCandidate : objectMapper.convertValue(candidateObject, ImportConnectorCandidateV1.class);
            List<String> candidateErrors = validateImportCandidate(candidate, invocation.churchInstanceId());
            if (!candidateErrors.isEmpty()) {
                errors.addAll(candidateErrors);
                continue;
            }
            NormalizedImportCandidate normalized = toNormalized(candidate);
            CandidateValidationResult validation = importPipeline.validate(context, normalized);
            if (!validation.valid()) {
                validation.failures().forEach(failure -> errors.add(failure.errorCode().name()));
                continue;
            }
            staged.add(importPipeline.stage(context, normalized));
        }
        if (!staged.isEmpty()) {
            auditRecorder.record("PLUGIN_IMPORT_CANDIDATES_STAGED", result.metadata().get(0).pluginVersionId(), invocation.actorId());
        }
        if (!errors.isEmpty()) {
            auditRecorder.record("PLUGIN_IMPORT_JOB_DEGRADED", result.metadata().get(0).pluginVersionId(), invocation.actorId());
        }
        return new ImportConnectorJobResult(errors.isEmpty() ? JobStatus.SUCCEEDED : JobStatus.DEGRADED, staged, errors, false, null);
    }

    public MetadataTransformJobResult runMetadataTransform(PluginRuntimeInvocation invocation) {
        PluginRuntimeResult result = runtimeService.execute(invocation);
        if (result.status() != ExecutionStatus.SUCCEEDED) {
            auditRecorder.record("PLUGIN_TRANSFORM_JOB_FAILED", null, invocation.actorId());
            return new MetadataTransformJobResult(JobStatus.FAILED, List.of(), result.safeErrors(), retryable(result.status()), null);
        }
        List<MetadataTransformChangeSet> changeSets = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Object changeObject : attributeList(result, "metadataChangeSets")) {
            MetadataTransformChangeSet changeSet = changeObject instanceof MetadataTransformChangeSet typedChangeSet
                    ? typedChangeSet : objectMapper.convertValue(changeObject, MetadataTransformChangeSet.class);
            List<String> changeErrors = validateChangeSet(changeSet);
            if (changeErrors.isEmpty()) {
                changeSets.add(new MetadataTransformChangeSet(UUID.randomUUID(), changeSet.targetId(), changeSet.canonicalEligible(),
                        changeSet.proposedMetadata(), changeSet.tagMappings(), changeSet.reviewNotes(), ImportCandidateStatus.DEDUPLICATION_REVIEW));
            } else {
                errors.addAll(changeErrors);
            }
        }
        if (!changeSets.isEmpty()) {
            auditRecorder.record("PLUGIN_METADATA_CHANGESETS_PROPOSED", result.metadata().get(0).pluginVersionId(), invocation.actorId());
        }
        if (!errors.isEmpty()) {
            auditRecorder.record("PLUGIN_TRANSFORM_JOB_DEGRADED", result.metadata().get(0).pluginVersionId(), invocation.actorId());
        }
        return new MetadataTransformJobResult(errors.isEmpty() ? JobStatus.SUCCEEDED : JobStatus.DEGRADED, changeSets, errors, false, null);
    }

    private List<String> validateImportCandidate(ImportConnectorCandidateV1 candidate, String churchInstanceId) {
        List<String> errors = new ArrayList<>();
        if (candidate == null) {
            return List.of("PLUGIN_IMPORT_CANDIDATE_INVALID");
        }
        if (candidate.provenanceReferences() == null || candidate.provenanceReferences().isEmpty()) {
            errors.add("PLUGIN_IMPORT_PROVENANCE_MISSING");
        }
        if (candidate.licensingReferences() == null || candidate.licensingReferences().isEmpty()
                || candidate.licenseType() == LicenseType.UNKNOWN || !ACCEPTED_LICENSE_TYPES.contains(candidate.licenseType())) {
            errors.add("PLUGIN_IMPORT_LICENSE_INVALID");
        }
        if (hasDuplicates(candidate.externalIdentifiers())) {
            errors.add("PLUGIN_IMPORT_EXTERNAL_IDENTIFIER_DUPLICATE");
        }
        if (candidate.safeMetadata() != null && candidate.safeMetadata().containsKey("churchInstanceId")
                && !churchInstanceId.equals(candidate.safeMetadata().get("churchInstanceId"))) {
            errors.add("PLUGIN_IMPORT_INSTANCE_DATA_UNAUTHORIZED");
        }
        return errors;
    }

    private List<String> validateChangeSet(MetadataTransformChangeSet changeSet) {
        List<String> errors = new ArrayList<>();
        if (changeSet == null || changeSet.targetId() == null || changeSet.targetId().isBlank()) {
            return List.of("PLUGIN_TRANSFORM_CHANGESET_INVALID");
        }
        if (changeSet.proposedMetadata() != null) {
            changeSet.proposedMetadata().keySet().stream()
                    .filter(RESERVED_TRANSFORM_FIELDS::contains)
                    .forEach(field -> errors.add("PLUGIN_TRANSFORM_RECOMMENDATION_OR_APPROVAL_ATTEMPT"));
        }
        if (changeSet.tagMappings() != null) {
            changeSet.tagMappings().stream()
                    .filter(tag -> GOVERNED_TAG_PREFIXES.stream().noneMatch(prefix -> tag.toLowerCase(Locale.ROOT).startsWith(prefix)))
                    .forEach(tag -> errors.add("PLUGIN_TRANSFORM_TAG_MAPPING_INVALID"));
        }
        return errors;
    }

    private NormalizedImportCandidate toNormalized(ImportConnectorCandidateV1 candidate) {
        return new NormalizedImportCandidate(candidate.connectorId(), candidate.providerName(), candidate.importMethod(),
                candidate.sourceRecordId(), candidate.sourceReference(), candidate.licenseType(), candidate.retrievedAt(),
                candidate.rawContentHash(), candidate.normalizedContentHash(), candidate.rawTitle(), candidate.normalizedTitle(),
                candidate.sourceArtistName(), candidate.ccliNumber(), candidate.sourcePayloadJson(), candidate.safeMetadata() == null ? Map.of() : candidate.safeMetadata());
    }

    private List<Object> attributeList(PluginRuntimeResult result, String key) {
        if (result.outputs().isEmpty() || !(result.outputs().get(0).attributes().get(key) instanceof List<?> values)) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private boolean retryable(ExecutionStatus status) {
        return status == ExecutionStatus.RETRY_SCHEDULED || status == ExecutionStatus.JOB_FAILED;
    }

    private boolean hasDuplicates(List<String> values) {
        return values != null && values.stream().distinct().count() != values.size();
    }
}
