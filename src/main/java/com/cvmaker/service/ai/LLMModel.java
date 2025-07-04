package com.cvmaker.service.ai;

import com.openai.models.ChatModel;

public enum LLMModel {
    GPT_4_1(ChatModel.GPT_4_1.asString(), "https://api.openai.com/v1", LLMProvider.OPENAI),
    GPT_4_1_MINI(ChatModel.GPT_4_1_MINI.asString(), "https://api.openai.com/v1", LLMProvider.OPENAI),
    GPT_4_1_NANO(ChatModel.GPT_4_1_NANO.asString(), "https://api.openai.com/v1", LLMProvider.OPENAI),
    LLAMA_3_8B_LEXI_UNCENSORED("llama-3-8b-lexi-uncensored", "http://localhost:1234/v1", LLMProvider.LOCAL),
    MISTRAL_SMALL_3_2("mistralai/mistral-small-3.2", "http://localhost:1234/v1", LLMProvider.LOCAL),;

    private final String modelName;
    private final String endpoint;
    private final LLMProvider provider;

    LLMModel(String modelName, String endpoint, LLMProvider provider) {
        this.modelName = modelName;
        this.endpoint = endpoint;
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public LLMProvider getProvider() {
        return provider;
    }

}
