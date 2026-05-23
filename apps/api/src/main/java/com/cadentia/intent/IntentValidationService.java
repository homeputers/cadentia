package com.cadentia.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class IntentValidationService {

    public static final String CONTRACT_VERSION = "v1";
    private static final String SCHEMA_NAME = "intent.generate_setlist";
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final Counts DEFAULT_COUNTS = new Counts(10, 5);
    private static final IntentKeyPolicy DEFAULT_KEY_POLICY = new IntentKeyPolicy(true, true, 2);
    private static final IntentTempoPolicy DEFAULT_TEMPO_POLICY = new IntentTempoPolicy(12);
    private static final Set<String> TOP_LEVEL_GENERATE_FIELDS = Set.of(
            "schemaName", "schemaVersion", "contractRevision", "intent", "slots");
    private static final Set<String> TOP_LEVEL_CLARIFY_FIELDS = Set.of(
            "schemaName", "schemaVersion", "contractRevision", "intent", "reasonCode", "clarificationQuestion", "missingSlots");
    private static final Set<String> TOP_LEVEL_UNSUPPORTED_FIELDS = Set.of(
            "schemaName", "schemaVersion", "contractRevision", "intent", "reasonCode", "safeMessage");
    private static final Set<String> GENERATE_SLOT_FIELDS = Set.of(
            "verseText",
            "scriptureReferences",
            "themeHints",
            "counts",
            "keyPolicy",
            "tempoPolicy",
            "language",
            "energyArc",
            "excludedSongs",
            "serviceMoment");
    private static final Set<String> COUNT_FIELDS = Set.of("praise", "worship");
    private static final Set<String> KEY_POLICY_FIELDS = Set.of(
            "preferSameKey", "allowRelativeMajorMinor", "maxKeyCenters");
    private static final Set<String> TEMPO_POLICY_FIELDS = Set.of("maxJumpBpm");
    private static final Set<String> SERVICE_MOMENTS = Set.of(
            "opening", "communion", "response", "altar_call", "sending", "other");
    private static final Set<String> ENERGY_ARCS = Set.of(
            "steady", "rising", "falling", "low_to_high", "high_to_low");
    private static final Set<String> CLARIFY_REASON_CODES = Set.of(
            "MISSING_REQUIRED_INFORMATION", "AMBIGUOUS_REQUEST", "INSUFFICIENT_CONTEXT");
    private static final Set<String> UNSUPPORTED_REASON_CODES = Set.of(
            "OUT_OF_SCOPE", "UNSUPPORTED_ACTION", "UNSUPPORTED_INTENT");
    private static final Set<String> MISSING_SLOT_VALUES = GENERATE_SLOT_FIELDS;

    private final ObjectMapper objectMapper;

    public IntentValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IntentValidationResult validate(String llmJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(llmJson);
        } catch (JsonProcessingException exception) {
            return reject(IntentValidationErrorCode.MALFORMED_JSON, "$", "LLM output must be valid JSON.");
        }

        if (!root.isObject()) {
            return reject(IntentValidationErrorCode.INVALID_TYPE, "$", "Intent output must be a JSON object.");
        }

        IntentValidationError schemaError = validateSchemaEnvelope(root);
        if (schemaError != null) {
            return IntentValidationResult.rejected(List.of(schemaError));
        }

        JsonNode intentNode = root.get("intent");
        if (intentNode == null) {
            return reject(IntentValidationErrorCode.MISSING_REQUIRED_FIELD, "$.intent", "Intent is required.");
        }
        if (!intentNode.isTextual()) {
            return reject(IntentValidationErrorCode.INVALID_TYPE, "$.intent", "Intent must be a string.");
        }

        return switch (intentNode.asText()) {
            case "GENERATE_SETLIST" -> validateGenerateSetlist(root);
            case "CLARIFY_REQUEST" -> validateClarifyRequest(root);
            case "UNSUPPORTED_REQUEST" -> validateUnsupportedRequest(root);
            default -> reject(IntentValidationErrorCode.UNKNOWN_INTENT, "$.intent", "Intent is not supported.");
        };
    }

    private IntentValidationError validateSchemaEnvelope(JsonNode root) {
        JsonNode schemaNameNode = root.get("schemaName");
        if (schemaNameNode != null) {
            if (!schemaNameNode.isTextual()) {
                return error(IntentValidationErrorCode.INVALID_TYPE, "$.schemaName", "schemaName must be a string.");
            }
            if (!SCHEMA_NAME.equals(schemaNameNode.asText())) {
                return error(IntentValidationErrorCode.UNSUPPORTED_SCHEMA_NAME, "$.schemaName", "Schema name is not supported.");
            }
        }
        JsonNode schemaVersionNode = root.get("schemaVersion");
        if (schemaVersionNode != null) {
            if (!schemaVersionNode.isTextual()) {
                return error(IntentValidationErrorCode.INVALID_TYPE, "$.schemaVersion", "schemaVersion must be a string.");
            }
            if (!SCHEMA_VERSION.equals(schemaVersionNode.asText())) {
                return error(IntentValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION, "$.schemaVersion", "Schema version is not supported.");
            }
        }
        JsonNode contractRevisionNode = root.get("contractRevision");
        if (contractRevisionNode != null && !contractRevisionNode.isInt()) {
            return error(IntentValidationErrorCode.INVALID_TYPE, "$.contractRevision", "contractRevision must be an integer.");
        }
        return null;
    }

    private IntentValidationResult validateGenerateSetlist(JsonNode root) {
        List<IntentValidationError> errors = new ArrayList<>();
        rejectUnknownFields(root, TOP_LEVEL_GENERATE_FIELDS, "$", errors);
        JsonNode slotsNode = root.get("slots");
        if (slotsNode == null) {
            errors.add(error(IntentValidationErrorCode.MISSING_REQUIRED_FIELD, "$.slots", "Slots are required."));
        } else if (!slotsNode.isObject()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, "$.slots", "Slots must be an object."));
        }
        if (!errors.isEmpty()) {
            return IntentValidationResult.rejected(errors);
        }

        Map<String, JsonNode> slots = objectFields(slotsNode);
        rejectUnknownFields(slotsNode, GENERATE_SLOT_FIELDS, "$.slots", errors);

        String verseText = readOptionalString(slots.get("verseText"), "$.slots.verseText", 0, 5000, errors, "");
        List<String> scriptureReferences = readStringArray(
                slots.get("scriptureReferences"), "$.slots.scriptureReferences", 20, 1, 100, errors);
        List<String> themeHints = readStringArray(
                slots.get("themeHints"), "$.slots.themeHints", 20, 1, 120, errors);
        Counts counts = readCounts(slots.get("counts"), errors);
        IntentKeyPolicy keyPolicy = readKeyPolicy(slots.get("keyPolicy"), errors);
        IntentTempoPolicy tempoPolicy = readTempoPolicy(slots.get("tempoPolicy"), errors);
        String language = readNullableString(
                slots.get("language"), "$.slots.language", 2, 35, errors, IntentValidationService::isValidLanguage);
        String energyArc = readNullableEnum(slots.get("energyArc"), "$.slots.energyArc", ENERGY_ARCS, errors);
        List<String> excludedSongs = readStringArray(
                slots.get("excludedSongs"), "$.slots.excludedSongs", 25, 1, 120, errors);
        String serviceMoment = readNullableEnum(
                slots.get("serviceMoment"), "$.slots.serviceMoment", SERVICE_MOMENTS, errors);

        if (!errors.isEmpty()) {
            return IntentValidationResult.rejected(errors);
        }

        GenerateSetlistSlots validatedSlots = new GenerateSetlistSlots(
                verseText,
                scriptureReferences,
                themeHints,
                counts,
                keyPolicy,
                tempoPolicy,
                language,
                energyArc,
                excludedSongs,
                serviceMoment);
        return IntentValidationResult.accepted(new GenerateSetlistIntent(CONTRACT_VERSION, validatedSlots));
    }

    private IntentValidationResult validateClarifyRequest(JsonNode root) {
        List<IntentValidationError> errors = new ArrayList<>();
        rejectUnknownFields(root, TOP_LEVEL_CLARIFY_FIELDS, "$", errors);
        String reasonCode = readRequiredEnum(root.get("reasonCode"), "$.reasonCode", CLARIFY_REASON_CODES, errors);
        String clarificationQuestion = readRequiredString(
                root.get("clarificationQuestion"), "$.clarificationQuestion", 1, 500, errors);
        List<String> missingSlots = readOptionalEnumArray(
                root.get("missingSlots"), "$.missingSlots", 20, MISSING_SLOT_VALUES, errors);
        if (!errors.isEmpty()) {
            return IntentValidationResult.rejected(errors);
        }
        return IntentValidationResult.accepted(new ClarifyRequestIntent(
                CONTRACT_VERSION, reasonCode, clarificationQuestion, missingSlots));
    }

    private IntentValidationResult validateUnsupportedRequest(JsonNode root) {
        List<IntentValidationError> errors = new ArrayList<>();
        rejectUnknownFields(root, TOP_LEVEL_UNSUPPORTED_FIELDS, "$", errors);
        String reasonCode = readRequiredEnum(root.get("reasonCode"), "$.reasonCode", UNSUPPORTED_REASON_CODES, errors);
        String safeMessage = readRequiredString(root.get("safeMessage"), "$.safeMessage", 1, 500, errors);
        if (!errors.isEmpty()) {
            return IntentValidationResult.rejected(errors);
        }
        return IntentValidationResult.accepted(new UnsupportedRequestIntent(CONTRACT_VERSION, reasonCode, safeMessage));
    }

    private Counts readCounts(JsonNode node, List<IntentValidationError> errors) {
        if (node == null) {
            return DEFAULT_COUNTS;
        }
        if (!node.isObject()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, "$.slots.counts", "Counts must be an object."));
            return DEFAULT_COUNTS;
        }
        rejectUnknownFields(node, COUNT_FIELDS, "$.slots.counts", errors);
        int praise = readOptionalInteger(node.get("praise"), "$.slots.counts.praise", 0, 25, errors, DEFAULT_COUNTS.praise());
        int worship = readOptionalInteger(
                node.get("worship"), "$.slots.counts.worship", 0, 25, errors, DEFAULT_COUNTS.worship());
        return new Counts(praise, worship);
    }

    private IntentKeyPolicy readKeyPolicy(JsonNode node, List<IntentValidationError> errors) {
        if (node == null) {
            return DEFAULT_KEY_POLICY;
        }
        if (!node.isObject()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, "$.slots.keyPolicy", "Key policy must be an object."));
            return DEFAULT_KEY_POLICY;
        }
        rejectUnknownFields(node, KEY_POLICY_FIELDS, "$.slots.keyPolicy", errors);
        boolean preferSameKey = readOptionalBoolean(
                node.get("preferSameKey"), "$.slots.keyPolicy.preferSameKey", errors, DEFAULT_KEY_POLICY.preferSameKey());
        boolean allowRelativeMajorMinor = readOptionalBoolean(
                node.get("allowRelativeMajorMinor"),
                "$.slots.keyPolicy.allowRelativeMajorMinor",
                errors,
                DEFAULT_KEY_POLICY.allowRelativeMajorMinor());
        int maxKeyCenters = readOptionalInteger(
                node.get("maxKeyCenters"),
                "$.slots.keyPolicy.maxKeyCenters",
                1,
                12,
                errors,
                DEFAULT_KEY_POLICY.maxKeyCenters());
        return new IntentKeyPolicy(preferSameKey, allowRelativeMajorMinor, maxKeyCenters);
    }

    private IntentTempoPolicy readTempoPolicy(JsonNode node, List<IntentValidationError> errors) {
        if (node == null) {
            return DEFAULT_TEMPO_POLICY;
        }
        if (!node.isObject()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, "$.slots.tempoPolicy", "Tempo policy must be an object."));
            return DEFAULT_TEMPO_POLICY;
        }
        rejectUnknownFields(node, TEMPO_POLICY_FIELDS, "$.slots.tempoPolicy", errors);
        int maxJumpBpm = readOptionalInteger(
                node.get("maxJumpBpm"),
                "$.slots.tempoPolicy.maxJumpBpm",
                1,
                60,
                errors,
                DEFAULT_TEMPO_POLICY.maxJumpBpm());
        return new IntentTempoPolicy(maxJumpBpm);
    }

    private void rejectUnknownFields(
            JsonNode node, Set<String> allowedFields, String path, List<IntentValidationError> errors) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                errors.add(error(
                        IntentValidationErrorCode.UNSUPPORTED_FIELD,
                        path + "." + fieldName,
                        "Field is not supported by intent contract " + CONTRACT_VERSION + "."));
            }
        }
    }

    private Map<String, JsonNode> objectFields(JsonNode node) {
        Map<String, JsonNode> fields = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> field = iterator.next();
            fields.put(field.getKey(), field.getValue());
        }
        return fields;
    }

    private static String readOptionalString(
            JsonNode node,
            String path,
            int minLength,
            int maxLength,
            List<IntentValidationError> errors,
            String defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        return readString(node, path, minLength, maxLength, errors, defaultValue, false);
    }

    private static String readRequiredString(
            JsonNode node, String path, int minLength, int maxLength, List<IntentValidationError> errors) {
        if (node == null) {
            errors.add(error(IntentValidationErrorCode.MISSING_REQUIRED_FIELD, path, "Field is required."));
            return "";
        }
        return readString(node, path, minLength, maxLength, errors, "", false);
    }

    private static String readString(
            JsonNode node,
            String path,
            int minLength,
            int maxLength,
            List<IntentValidationError> errors,
            String defaultValue,
            boolean nullable) {
        if (node.isNull() && nullable) {
            return null;
        }
        if (!node.isTextual()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, path, "Field must be a string."));
            return defaultValue;
        }
        String value = node.asText();
        if (value.length() < minLength || value.length() > maxLength) {
            errors.add(error(IntentValidationErrorCode.OUT_OF_RANGE, path, "String length is outside supported bounds."));
            return defaultValue;
        }
        return value;
    }

    private static String readNullableString(
            JsonNode node,
            String path,
            int minLength,
            int maxLength,
            List<IntentValidationError> errors,
            StringRule rule) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = readString(node, path, minLength, maxLength, errors, null, true);
        if (value != null && !rule.isValid(value)) {
            errors.add(error(IntentValidationErrorCode.UNSUPPORTED_ENUM, path, "String value is not supported."));
            return null;
        }
        return value;
    }

    private static List<String> readStringArray(
            JsonNode node,
            String path,
            int maxItems,
            int minItemLength,
            int maxItemLength,
            List<IntentValidationError> errors) {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, path, "Field must be an array."));
            return List.of();
        }
        if (node.size() > maxItems) {
            errors.add(error(IntentValidationErrorCode.OUT_OF_RANGE, path, "Array contains too many items."));
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            values.add(readString(
                    node.get(index),
                    path + "[" + index + "]",
                    minItemLength,
                    maxItemLength,
                    errors,
                    "",
                    false));
        }
        return values;
    }

    private static List<String> readOptionalEnumArray(
            JsonNode node,
            String path,
            int maxItems,
            Set<String> allowedValues,
            List<IntentValidationError> errors) {
        List<String> values = readStringArray(node, path, maxItems, 1, 120, errors);
        for (String value : new HashSet<>(values)) {
            if (!allowedValues.contains(value)) {
                errors.add(error(IntentValidationErrorCode.UNSUPPORTED_ENUM, path, "Array contains unsupported enum value."));
            }
        }
        return values;
    }

    private static int readOptionalInteger(
            JsonNode node,
            String path,
            int minimum,
            int maximum,
            List<IntentValidationError> errors,
            int defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        if (!node.isInt()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, path, "Field must be an integer."));
            return defaultValue;
        }
        int value = node.asInt();
        if (value < minimum || value > maximum) {
            errors.add(error(IntentValidationErrorCode.OUT_OF_RANGE, path, "Integer is outside supported bounds."));
            return defaultValue;
        }
        return value;
    }

    private static boolean readOptionalBoolean(
            JsonNode node, String path, List<IntentValidationError> errors, boolean defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        if (!node.isBoolean()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, path, "Field must be a boolean."));
            return defaultValue;
        }
        return node.asBoolean();
    }

    private static String readNullableEnum(
            JsonNode node, String path, Set<String> allowedValues, List<IntentValidationError> errors) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            errors.add(error(IntentValidationErrorCode.INVALID_TYPE, path, "Field must be a string or null."));
            return null;
        }
        String value = node.asText();
        if (!allowedValues.contains(value)) {
            errors.add(error(IntentValidationErrorCode.UNSUPPORTED_ENUM, path, "Enum value is not supported."));
            return null;
        }
        return value;
    }

    private static String readRequiredEnum(
            JsonNode node, String path, Set<String> allowedValues, List<IntentValidationError> errors) {
        String value = readRequiredString(node, path, 1, 120, errors);
        if (!value.isEmpty() && !allowedValues.contains(value)) {
            errors.add(error(IntentValidationErrorCode.UNSUPPORTED_ENUM, path, "Enum value is not supported."));
        }
        return value;
    }

    private static boolean isValidLanguage(String value) {
        return value.matches("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");
    }

    private static IntentValidationResult reject(IntentValidationErrorCode code, String path, String message) {
        return IntentValidationResult.rejected(List.of(error(code, path, message)));
    }

    private static IntentValidationError error(IntentValidationErrorCode code, String path, String message) {
        return new IntentValidationError(code, path, message);
    }

    @FunctionalInterface
    private interface StringRule {
        boolean isValid(String value);
    }
}
