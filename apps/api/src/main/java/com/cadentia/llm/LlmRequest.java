package com.cadentia.llm;

import java.util.Objects;

public record LlmRequest(
        String promptVersion,
        String schemaVersion,
        String systemPrompt,
        String userText,
        String repairInstruction,
        String correlationId,
        LlmGenerationOptions generationOptions) {

    public LlmRequest {
        promptVersion = requireText(promptVersion, "promptVersion is required");
        schemaVersion = requireText(schemaVersion, "schemaVersion is required");
        systemPrompt = requireText(systemPrompt, "systemPrompt is required");
        userText = Objects.requireNonNullElse(userText, "");
        repairInstruction = Objects.requireNonNullElse(repairInstruction, "");
        correlationId = Objects.requireNonNullElse(correlationId, "");
        generationOptions = Objects.requireNonNull(generationOptions, "generationOptions is required");
    }

    public String userMessage() {
        if (repairInstruction.isBlank()) {
            return "User request to extract into JSON:\n" + userText;
        }
        return repairInstruction + "\n\nUser request to extract into JSON:\n" + userText;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
