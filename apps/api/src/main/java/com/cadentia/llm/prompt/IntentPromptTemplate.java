package com.cadentia.llm.prompt;

public record IntentPromptTemplate(
        String promptVersion,
        String schemaVersion,
        String systemPrompt) {

    public IntentPromptTemplate {
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("Prompt version is required.");
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("Schema version is required.");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("System prompt is required.");
        }
    }
}
