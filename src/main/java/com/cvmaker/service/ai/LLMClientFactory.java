package com.cvmaker.service.ai;

public class LLMClientFactory {

    public static LLMClient createClient(LLMProvider provider, LLMModel model) {
        return switch (provider) {
            case OPENAI ->
                new OpenAIClient();
            case LOCAL ->
                new LocalLLMClient(model.getEndpoint());
        };
    }
}
