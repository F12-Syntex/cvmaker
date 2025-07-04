package com.cvmaker.service.ai;

public interface LLMClient {
    LLMResponse complete(LLMRequest request);
    void shutdown();
}
