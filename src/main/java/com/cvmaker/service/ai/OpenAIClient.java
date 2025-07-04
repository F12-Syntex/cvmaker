package com.cvmaker.service.ai;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class OpenAIClient implements LLMClient {

    private final com.openai.client.OpenAIClient client;

    public OpenAIClient() {
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addUserMessage(request.getPrompt())
                    .model(request.getModel())
                    .temperature(request.getTemperature())
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);

            var usage = completion.usage().get();
            var responseContent = completion.choices().get(0).message().content();

            if (!responseContent.isPresent()) {
                throw new RuntimeException("Empty response from OpenAI API");
            }

            return LLMResponse.builder()
                    .content(responseContent.get())
                    .usage(new UsageStats(
                            (int) usage.promptTokens(),
                            (int) usage.completionTokens(),
                            (int) usage.totalTokens()))
                    .model(request.getModel())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        try {
            client.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to shutdown OpenAI client: " + e.getMessage(), e);
        }
    }
}
