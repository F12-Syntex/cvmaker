package com.cvmaker.service.ai;

import com.openai.models.ChatModel;

public enum LLMModel {
    GPT_4_1(ChatModel.GPT_4_1.asString(), "https://api.openai.com/v1", LLMProvider.OPENAI),
    GPT_4_1_MINI(ChatModel.GPT_4_1_MINI.asString(), "https://api.openai.com/v1", LLMProvider.OPENAI),
    GPT_4_1_NANO(ChatModel.GPT_4_1_NANO.asString(), "https://api.openai.com/v1", LLMProvider.OPENAI),
    GPT_5_MINI("gpt-5-mini-2025-08-07", "https://api.openai.com/v1", LLMProvider.OPENAI),
    /**
     * Old, misleading name for {@link #GPT_5_MINI}: the model behind it is
     * gpt-5-mini, not a 5.1 model. Kept so that existing code and configuration
     * files keep working.
     */
    GPT_5_1_MINI("gpt-5-mini-2025-08-07", "https://api.openai.com/v1", LLMProvider.OPENAI),
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

    /**
     * The model to use when only the provider is known.
     */
    public static LLMModel defaultFor(LLMProvider provider) {
        return switch (provider) {
            case OPENAI ->
                GPT_5_MINI;
            case LOCAL ->
                MISTRAL_SMALL_3_2;
        };
    }

    /**
     * Which optional request parameters this model accepts.
     */
    public ModelCapabilities getCapabilities() {
        return switch (provider) {
            case OPENAI ->
                ModelCapabilities.forOpenAiModel(modelName);
            case LOCAL ->
                ModelCapabilities.forLocalModel();
        };
    }

    /**
     * False for models that only accept their default temperature (the GPT-5
     * family and other OpenAI reasoning models). A temperature set for such a
     * model is not sent.
     */
    public boolean supportsTemperature() {
        return getCapabilities().supportsTemperature();
    }

}
