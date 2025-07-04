package com.cvmaker.service.ai;

import com.openai.models.ChatModel;

public enum LLMModel {
    GPT_4_1(ChatModel.GPT_4_1.asString(), "https://api.openai.com/v1"),
    GPT_4_1_MINI(ChatModel.GPT_4_1_MINI.asString(), "https://api.openai.com/v1"),
    GPT_4_1_NANO(ChatModel.GPT_4_1_NANO.asString(), "https://api.openai.com/v1"),
    LLAMA_3_8B_LEXI_UNCENSORED("llama-3-8b-lexi-uncensored", "http://localhost:1234/v1"),
    MISTRAL_SMALL_3_2("mistralai/mistral-small-3.2", "http://localhost:1234/v1");

    private final String modelName;
    private final String endpoint;

    LLMModel(String modelName, String endpoint) {
        this.modelName = modelName;
        this.endpoint = endpoint;
    }

    public String getModelName() {
        return modelName;
    }

    public String getEndpoint() {
        return endpoint;
    }

}
