package com.cvmaker.service.ai;

import java.util.Objects;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class OpenAIClient implements LLMClient {

    private final com.openai.client.OpenAIClient client;

    public OpenAIClient() {
        //TODO: grab from .env file instead
        this(OpenAIOkHttpClient.fromEnv());
    }

    /**
     * Uses an already configured SDK client. This is the seam for tests, which
     * pass a mock so that nothing reaches the network.
     */
    public OpenAIClient(com.openai.client.OpenAIClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        try {
            ChatCompletionCreateParams params = buildParams(request);
            ChatCompletion completion = client.chat().completions().create(params);
            return toResponse(completion, request);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the Chat Completions request. Only parameters the model accepts
     * are sent, as listed in {@link ModelCapabilities}: {@code temperature} is
     * left out for models that reject it, and a token limit is sent only when
     * the caller set one, under the field name the model expects.
     */
    static ChatCompletionCreateParams buildParams(LLMRequest request) {
        if (request.getPrompt() == null) {
            throw new IllegalArgumentException("LLM request has no prompt");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new IllegalArgumentException("LLM request has no model");
        }

        ModelCapabilities capabilities = ModelCapabilities.forOpenAiModel(request.getModel());

        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .addUserMessage(request.getPrompt())
                .model(request.getModel());

        if (capabilities.supportsTemperature()) {
            params.temperature(request.getTemperature());
        }

        if (request.hasMaxTokens()) {
            long limit = request.getMaxTokens();
            switch (capabilities.getTokenLimitParameter()) {
                case MAX_COMPLETION_TOKENS ->
                    params.maxCompletionTokens(limit);
                case MAX_TOKENS ->
                    params.maxTokens(limit);
            }
        }

        return params.build();
    }

    /**
     * Maps the SDK response to an {@link LLMResponse}. A response without a
     * usage block is valid and gives a null {@link LLMResponse#getUsage()}.
     *
     * @throws IllegalStateException when the response carries no text
     */
    static LLMResponse toResponse(ChatCompletion completion, LLMRequest request) {
        if (completion.choices().isEmpty()) {
            throw new IllegalStateException("Empty response from OpenAI API (no choices)");
        }

        ChatCompletion.Choice choice = completion.choices().get(0);
        String content = choice.message().content().orElse(null);

        if (content == null || content.isBlank()) {
            String finishReason = finishReasonOf(choice);
            String hint = "length".equals(finishReason)
                    ? "; the token limit was used up before any text was produced, so raise or remove maxTokens"
                    : "";
            throw new IllegalStateException(
                    "Empty response from OpenAI API (finish_reason=" + finishReason + ")" + hint);
        }

        UsageStats usage = completion.usage()
                .map(u -> new UsageStats(
                (int) u.promptTokens(),
                (int) u.completionTokens(),
                (int) u.totalTokens()))
                .orElse(null);

        return LLMResponse.builder()
                .content(content)
                .usage(usage)
                .model(request.getModel())
                .build();
    }

    private static String finishReasonOf(ChatCompletion.Choice choice) {
        try {
            return choice.finishReason().asString();
        } catch (RuntimeException e) {
            return "unknown";
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
