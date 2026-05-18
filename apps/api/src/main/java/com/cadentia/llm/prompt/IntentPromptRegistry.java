package com.cadentia.llm.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntentPromptRegistry {

    public static final String INTENT_V1_PROMPT_VERSION = "intent-v1";
    public static final String INTENT_V1_SCHEMA_VERSION = "v1";
    private static final String INTENT_V1_RESOURCE = "prompts/intent/intent-v1-system-prompt.md";

    private final Map<String, IntentPromptTemplate> templatesByVersion;

    public IntentPromptRegistry() {
        this.templatesByVersion = Map.of(
                INTENT_V1_PROMPT_VERSION,
                new IntentPromptTemplate(
                        INTENT_V1_PROMPT_VERSION,
                        INTENT_V1_SCHEMA_VERSION,
                        readClasspathResource(INTENT_V1_RESOURCE)));
    }

    public IntentPromptTemplate get(String promptVersion) {
        IntentPromptTemplate template = templatesByVersion.get(promptVersion);
        if (template == null) {
            throw new IllegalArgumentException("Unsupported intent prompt version: " + promptVersion);
        }
        return template;
    }

    public IntentPromptTemplate getForSchemaVersion(String schemaVersion) {
        return templatesByVersion.values().stream()
                .filter(template -> template.schemaVersion().equals(schemaVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No intent prompt template registered for schema version: " + schemaVersion));
    }

    private static String readClasspathResource(String resourcePath) {
        ClassLoader classLoader = IntentPromptRegistry.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing intent prompt resource: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read intent prompt resource: " + resourcePath, exception);
        }
    }
}
