package com.cadentia.plugin.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SpiContractValidator {
    private static final Set<String> BASE_ENVELOPE_FIELDS = Set.of("spiVersion", "extensionPoint", "executionId",
            "correlationId", "churchInstanceId", "environment", "pluginVersionId", "configurationVersionId",
            "registrySnapshotId", "policySnapshotId", "catalogSnapshotId", "actorContext", "systemContext", "locale",
            "deterministicSeed", "runId");
    private static final Set<String> STATUSES = Set.of("SUCCESS", "DEGRADED", "FAILURE");
    private static final Map<String, Set<String>> REQUIRED_OUTPUT_FIELDS = Map.of(
            "IMPORT_CONNECTOR", Set.of("envelope", "status", "documents", "errors", "warnings"),
            "METADATA_TRANSFORM", Set.of("envelope", "status", "proposedMetadata", "confidence", "errors", "reviewNotes"),
            "RECOMMENDATION_CONSTRAINT", Set.of("envelope", "status", "constraints", "errors"),
            "SCORING_CONTRIBUTION", Set.of("envelope", "status", "adjustments", "errors"),
            "EXPORT_RENDERER", Set.of("envelope", "status", "mimeType", "filename", "checksumSha256", "artifactRef", "errors", "warnings"),
            "OUTBOUND_PUBLISH_HOOK", Set.of("envelope", "status", "externalReference", "reconciliationStatus", "errors"));
    private static final Map<String, Set<String>> OPTIONAL_INPUT_FIELDS = Map.of(
            "IMPORT_CONNECTOR", Set.of("sourcePointer", "legalMode", "secretRefs", "options"),
            "METADATA_TRANSFORM", Set.of("stagedCandidateId", "candidateSnapshotId", "controlledVocabularyVersion", "metadata"),
            "RECOMMENDATION_CONSTRAINT", Set.of("requestSnapshotId", "allowedConstraintCodes", "candidateIds"),
            "SCORING_CONTRIBUTION", Set.of("scoringProfileVersion", "allowedComponentCodes", "candidateIds"),
            "EXPORT_RENDERER", Set.of("servicePlanSnapshotId", "requestedFormat", "exportOptions"),
            "OUTBOUND_PUBLISH_HOOK", Set.of("eventSchemaId", "idempotencyKey", "redactedEvent"));

    public void validateInput(String extensionPoint, JsonNode payload) {
        validatePayload(extensionPoint, payload, OPTIONAL_INPUT_FIELDS.get(extensionPoint), false);
    }

    public void validateOutput(String extensionPoint, JsonNode payload) {
        validatePayload(extensionPoint, payload, REQUIRED_OUTPUT_FIELDS.get(extensionPoint), true);
        List<String> errors = validateOutputRules(extensionPoint, payload);
        if (!errors.isEmpty()) {
            throw new SpiContractValidationException(errors);
        }
    }

    private void validatePayload(String extensionPoint, JsonNode payload, Set<String> contractFields, boolean output) {
        List<String> errors = new ArrayList<>();
        if (!OPTIONAL_INPUT_FIELDS.containsKey(extensionPoint)) {
            errors.add("unsupported extension point: " + extensionPoint);
        }
        if (payload == null || !payload.isObject()) {
            errors.add("payload must be an object");
            throw new SpiContractValidationException(errors);
        }
        Set<String> allowed = new java.util.HashSet<>();
        allowed.add("envelope");
        if (contractFields != null) {
            allowed.addAll(contractFields);
        }
        payload.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                errors.add("/" + field + ": additional fields are not allowed");
            }
        });
        if (!payload.has("envelope")) {
            errors.add("/envelope: required field is missing");
        } else {
            validateEnvelope(extensionPoint, payload.path("envelope"), errors);
        }
        if (contractFields != null) {
            for (String field : contractFields) {
                if (!payload.has(field)) {
                    errors.add("/" + field + ": required field is missing");
                }
            }
        }
        if (output && payload.has("status") && !STATUSES.contains(payload.path("status").asText())) {
            errors.add("/status: invalid enum value");
        }
        if (!errors.isEmpty()) {
            throw new SpiContractValidationException(errors);
        }
    }

    private void validateEnvelope(String extensionPoint, JsonNode envelope, List<String> errors) {
        if (!envelope.isObject()) {
            errors.add("/envelope: must be an object");
            return;
        }
        envelope.fieldNames().forEachRemaining(field -> {
            if (!BASE_ENVELOPE_FIELDS.contains(field)) {
                errors.add("/envelope/" + field + ": additional fields are not allowed");
            }
        });
        for (String field : List.of("spiVersion", "extensionPoint", "executionId", "correlationId", "churchInstanceId",
                "environment", "pluginVersionId", "configurationVersionId", "registrySnapshotId", "policySnapshotId", "locale")) {
            if (!envelope.hasNonNull(field) || envelope.path(field).asText().isBlank()) {
                errors.add("/envelope/" + field + ": required field is missing");
            }
        }
        if (!"1.0.0".equals(envelope.path("spiVersion").asText())) {
            errors.add("/envelope/spiVersion: unsupported SPI version");
        }
        if (!extensionPoint.equals(envelope.path("extensionPoint").asText())) {
            errors.add("/envelope/extensionPoint: does not match invocation extension point");
        }
        if (("RECOMMENDATION_CONSTRAINT".equals(extensionPoint) || "SCORING_CONTRIBUTION".equals(extensionPoint))
                && !envelope.hasNonNull("deterministicSeed")) {
            errors.add("/envelope/deterministicSeed: required for recommendation-path contracts");
        }
    }

    private List<String> validateOutputRules(String extensionPoint, JsonNode payload) {
        List<String> errors = new ArrayList<>();
        if (!payload.path("errors").isArray()) {
            errors.add("/errors: safe error list is required");
        }
        if (("SUCCESS".equals(payload.path("status").asText()) || "DEGRADED".equals(payload.path("status").asText()))
                && payload.path("errors").size() > 0 && !"DEGRADED".equals(payload.path("status").asText())) {
            errors.add("/status: successful outputs cannot include errors; use DEGRADED or FAILURE");
        }
        if ("RECOMMENDATION_CONSTRAINT".equals(extensionPoint)) {
            for (JsonNode constraint : payload.path("constraints")) {
                if (constraint.has("songId") || constraint.has("eligible")) {
                    errors.add("/constraints: free-form eligibility decisions are not allowed");
                }
                double weight = constraint.path("weight").asDouble(999);
                if (weight < -1.0 || weight > 1.0) {
                    errors.add("/constraints/weight: must be between -1.0 and 1.0");
                }
            }
        }
        if ("SCORING_CONTRIBUTION".equals(extensionPoint)) {
            for (JsonNode adjustment : payload.path("adjustments")) {
                double delta = adjustment.path("delta").asDouble(999);
                if (delta < -0.2 || delta > 0.2) {
                    errors.add("/adjustments/delta: must be between -0.2 and 0.2");
                }
            }
        }
        return errors;
    }
}
