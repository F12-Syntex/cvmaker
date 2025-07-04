package com.cvmaker.service.ai;

public class LLMClientFactory {

    public static LLMClient createClient(LLMModel model) {
        return switch (model.getProvider()) {
            case OPENAI ->
                new OpenAIClient();
            case LOCAL ->
                new LocalLLMClient(model.getEndpoint());
        };
    }
}
