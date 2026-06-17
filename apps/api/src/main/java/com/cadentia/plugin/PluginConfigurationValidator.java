package com.cadentia.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PluginConfigurationValidator {
    private static final Pattern SECRET_REF = Pattern.compile("^(secret-manager|env|vault|aws-sm|gcp-sm|azure-kv):[A-Za-z0-9_./:@-]+$");

    public void validate(JsonNode schema, JsonNode values, Map<String, String> secretRefs) {
        List<String> errors = new ArrayList<>();
        if (schema == null || !schema.isObject()) {
            throw new PluginRegistryException(List.of("configuration schema must be an object"));
        }
        if (values == null || !values.isObject()) {
            errors.add("configuration values must be an object");
        }
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode requiredField : required) {
                String field = requiredField.asText();
                JsonNode property = schema.path("properties").path(field);
                boolean secret = property.path("secret").asBoolean(false);
                if (secret) {
                    if (!secretRefs.containsKey(field) || !SECRET_REF.matcher(secretRefs.get(field)).matches()) {
                        errors.add("/secretRefs/" + field + ": required secret reference is missing or invalid");
                    }
                    if (values != null && values.has(field)) {
                        errors.add("/" + field + ": raw secret values are not allowed in configuration records");
                    }
                } else if ((values == null || !values.has(field)) && !property.has("default")) {
                    errors.add("/" + field + ": required configuration value is missing and has no default");
                }
            }
        }
        if (values != null) {
            values.fieldNames().forEachRemaining(field -> {
                if (field.toLowerCase().matches(".*(password|secret|token|api[_-]?key|credential).*")) {
                    errors.add("/" + field + ": suspected secret must be stored as a secret reference");
                }
            });
        }
        secretRefs.forEach((field, ref) -> {
            if (!SECRET_REF.matcher(ref).matches()) {
                errors.add("/secretRefs/" + field + ": invalid secret reference");
            }
        });
        if (!errors.isEmpty()) {
            throw new PluginRegistryException(errors);
        }
    }
}
