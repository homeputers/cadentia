package com.cadentia.llm;

import java.util.Map;
import java.util.Objects;

public record LlmResponse(
        String content,
        String provider,
        String model,
        Map<String, String> metadata) {

    public LlmResponse {
        content = Objects.requireNonNullElse(content, "");
        provider = Objects.requireNonNullElse(provider, "");
        model = Objects.requireNonNullElse(model, "");
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
