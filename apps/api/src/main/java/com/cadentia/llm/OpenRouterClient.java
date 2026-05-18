package com.cadentia.llm;

import org.springframework.stereotype.Component;

@Component
public class OpenRouterClient implements LlmClient {

    @Override
    public String complete(String prompt) {
        throw new UnsupportedOperationException("OpenRouter integration has not been configured.");
    }
}
