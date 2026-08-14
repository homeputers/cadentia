package com.cadentia.llm;

public interface LlmClient {

    LlmResponse complete(LlmRequest request);
}
