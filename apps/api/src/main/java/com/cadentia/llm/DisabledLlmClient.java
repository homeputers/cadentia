package com.cadentia.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cadentia.llm.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledLlmClient implements LlmClient {

    @Override
    public LlmResponse complete(LlmRequest request) {
        throw new LlmProviderException(LlmProviderErrorCode.DISABLED, "LLM provider is disabled.");
    }
}
