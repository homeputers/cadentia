package com.cadentia.llm;

public record LlmGenerationOptions(
        double temperature,
        double topP,
        int maxOutputTokens) {}
