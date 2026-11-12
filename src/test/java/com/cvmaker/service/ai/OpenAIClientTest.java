package com.cvmaker.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.openai.core.JsonValue;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;

/**
 * Covers request building and response mapping without the network: the SDK
 * client is a mock.
 */
class OpenAIClientTest {

    private static LLMRequest.Builder request(String model) {
        return LLMRequest.builder().prompt("Say hello").model(model).temperature(0.5);
    }

    private static ChatCompletion completion(String content, ChatCompletion.Choice.FinishReason finishReason,
            CompletionUsage usage) {
        ChatCompletionMessage message = ChatCompletionMessage.builder()
                .content(Optional.ofNullable(content))
                .refusal(Optional.empty())
                .role(JsonValue.from("assistant"))
                .build();
        ChatCompletion.Builder builder = ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(0L)
                .model("test-model")
                .object_(JsonValue.from("chat.completion"))
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0L)
                        .finishReason(finishReason)
                        .logprobs(Optional.empty())
                        .message(message)
                        .build());
        if (usage != null) {
            builder.usage(usage);
        }
        return builder.build();
    }

    private static CompletionUsage usage(long prompt, long completion) {
        return CompletionUsage.builder()
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(prompt + completion)
                .build();
    }

    // ---- request building ----
    @Test
    void gpt5FamilyRequestHasNoTemperature() {
        ChatCompletionCreateParams params = OpenAIClient.buildParams(
                request(LLMModel.GPT_5_MINI.getModelName()).build());

        assertThat(params.temperature()).isEmpty();
        assertThat(params.model().asString()).isEqualTo("gpt-5-mini-2025-08-07");
    }

    @Test
    void oldGpt5ConstantUsedByTheFormFillerHasNoTemperatureEither() {
        ChatCompletionCreateParams params = OpenAIClient.buildParams(
                request(LLMModel.GPT_5_1_MINI.getModelName()).temperature(0.7).build());

        assertThat(params.temperature()).isEmpty();
    }

    @Test
    void gpt4FamilyRequestKeepsTheTemperature() {
        ChatCompletionCreateParams params = OpenAIClient.buildParams(
                request(LLMModel.GPT_4_1_MINI.getModelName()).temperature(0.3).build());

        assertThat(params.temperature()).contains(0.3);
    }

    @Test
    void explicitMaxTokensIsSentAsMaxCompletionTokens() {
        for (LLMModel model : new LLMModel[]{LLMModel.GPT_4_1_MINI, LLMModel.GPT_5_MINI}) {
            ChatCompletionCreateParams params = OpenAIClient.buildParams(
                    request(model.getModelName()).maxTokens(1234).build());

            assertThat(params.maxCompletionTokens()).as(model.name()).contains(1234L);
            assertThat(params.maxTokens()).as(model.name()).isEmpty();
        }
    }

    @Test
    void noTokenLimitIsSentUnlessTheCallerSetOne() {
        LLMRequest withoutLimit = request(LLMModel.GPT_5_MINI.getModelName()).build();

        ChatCompletionCreateParams params = OpenAIClient.buildParams(withoutLimit);

        assertThat(withoutLimit.hasMaxTokens()).isFalse();
        assertThat(withoutLimit.getMaxTokens()).isEqualTo(LLMRequest.DEFAULT_MAX_TOKENS);
        assertThat(params.maxCompletionTokens()).isEmpty();
        assertThat(params.maxTokens()).isEmpty();
    }

    @Test
    void zeroMaxTokensMeansNoLimit() {
        LLMRequest request = request("gpt-4.1").maxTokens(500).maxTokens(0).build();

        assertThat(request.hasMaxTokens()).isFalse();
        assertThat(OpenAIClient.buildParams(request).maxCompletionTokens()).isEmpty();
    }

    @Test
    void promptIsSentAsTheSingleUserMessage() {
        ChatCompletionCreateParams params = OpenAIClient.buildParams(request("gpt-4.1").build());

        assertThat(params.messages()).hasSize(1);
        assertThat(params.messages().get(0).asUser().content().asText()).isEqualTo("Say hello");
    }

    @Test
    void missingPromptOrModelIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> OpenAIClient.buildParams(LLMRequest.builder().model("gpt-4.1").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
        assertThatThrownBy(() -> OpenAIClient.buildParams(LLMRequest.builder().prompt("hi").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
    }

    // ---- response mapping ----
    @Test
    void responseWithUsageIsMapped() {
        LLMResponse response = OpenAIClient.toResponse(
                completion("Hello!", ChatCompletion.Choice.FinishReason.STOP, usage(11, 7)),
                request("gpt-4.1").build());

        assertThat(response.getContent()).isEqualTo("Hello!");
        assertThat(response.getModel()).isEqualTo("gpt-4.1");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(11);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(7);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(18);
    }

    @Test
    void responseWithoutUsageBlockIsAccepted() {
        LLMResponse response = OpenAIClient.toResponse(
                completion("Hello!", ChatCompletion.Choice.FinishReason.STOP, null),
                request("gpt-4.1").build());

        assertThat(response.getContent()).isEqualTo("Hello!");
        assertThat(response.getUsage()).isNull();
    }

    @Test
    void emptyTextIsAnErrorThatNamesTheFinishReason() {
        ChatCompletion cutOff = completion("", ChatCompletion.Choice.FinishReason.LENGTH, usage(11, 100));

        assertThatThrownBy(() -> OpenAIClient.toResponse(cutOff, request("gpt-5-mini").build()))
                .isInstanceOf(LLMException.class)
                .hasMessageContaining("finish_reason=length")
                .hasMessageContaining("maxTokens")
                .matches(e -> !((LLMException) e).isRetryable(), "is not retryable");
    }

    @Test
    void missingTextIsAnError() {
        ChatCompletion noText = completion(null, ChatCompletion.Choice.FinishReason.CONTENT_FILTER, null);

        assertThatThrownBy(() -> OpenAIClient.toResponse(noText, request("gpt-4.1").build()))
                .isInstanceOf(LLMException.class)
                .hasMessageContaining("finish_reason=content_filter")
                .matches(e -> !((LLMException) e).isRetryable(), "is not retryable");
    }

    @Test
    void emptyTextAfterANormalStopCanBeRetried() {
        ChatCompletion blank = completion("  ", ChatCompletion.Choice.FinishReason.STOP, null);

        assertThatThrownBy(() -> OpenAIClient.toResponse(blank, request("gpt-4.1").build()))
                .isInstanceOf(LLMException.class)
                .matches(e -> ((LLMException) e).isRetryable(), "is retryable");
    }

    // ---- complete() through a mocked SDK client ----
    @Test
    void completeSendsTheBuiltParamsAndMapsTheAnswer() {
        com.openai.client.OpenAIClient sdk = mock(com.openai.client.OpenAIClient.class);
        ChatService chat = mock(ChatService.class);
        ChatCompletionService completions = mock(ChatCompletionService.class);
        when(sdk.chat()).thenReturn(chat);
        when(chat.completions()).thenReturn(completions);
        when(completions.create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion("Hi there", ChatCompletion.Choice.FinishReason.STOP, null));

        LLMResponse response = new OpenAIClient(sdk).complete(
                request(LLMModel.GPT_5_1_MINI.getModelName()).temperature(0.7).maxTokens(4000).build());

        ArgumentCaptor<ChatCompletionCreateParams> sent = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(completions).create(sent.capture());
        assertThat(sent.getValue().temperature()).isEmpty();
        assertThat(sent.getValue().maxCompletionTokens()).contains(4000L);
        assertThat(response.getContent()).isEqualTo("Hi there");
        assertThat(response.getUsage()).isNull();
    }

    @Test
    void completeWrapsFailuresWithTheirCause() {
        com.openai.client.OpenAIClient sdk = mock(com.openai.client.OpenAIClient.class);
        ChatService chat = mock(ChatService.class);
        ChatCompletionService completions = mock(ChatCompletionService.class);
        when(sdk.chat()).thenReturn(chat);
        when(chat.completions()).thenReturn(completions);
        IllegalStateException boom = new IllegalStateException("boom");
        when(completions.create(any(ChatCompletionCreateParams.class))).thenThrow(boom);

        assertThatThrownBy(() -> new OpenAIClient(sdk).complete(request("gpt-4.1").build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("OpenAI API call failed: boom")
                .hasCause(boom)
                .matches(e -> ((LLMException) e).isRetryable(), "is retryable");
    }

    @Test
    void rejectedRequestIsMarkedAsNotRetryable() {
        OpenAIServiceException badRequest = mock(OpenAIServiceException.class);
        when(badRequest.statusCode()).thenReturn(400);
        OpenAIServiceException overloaded = mock(OpenAIServiceException.class);
        when(overloaded.statusCode()).thenReturn(503);
        OpenAIServiceException rateLimited = mock(OpenAIServiceException.class);
        when(rateLimited.statusCode()).thenReturn(429);

        assertThat(OpenAIClient.isRetryable(badRequest)).isFalse();
        assertThat(OpenAIClient.isRetryable(overloaded)).isTrue();
        assertThat(OpenAIClient.isRetryable(rateLimited)).isTrue();
        assertThat(OpenAIClient.isRetryable(new IllegalArgumentException("LLM request has no prompt"))).isFalse();
        assertThat(OpenAIClient.isRetryable(new java.io.IOException("connection reset"))).isTrue();
    }

    @Test
    void invalidRequestFailsWithoutCallingTheApi() {
        com.openai.client.OpenAIClient sdk = mock(com.openai.client.OpenAIClient.class);

        assertThatThrownBy(() -> new OpenAIClient(sdk).complete(LLMRequest.builder().model("gpt-4.1").build()))
                .isInstanceOf(LLMException.class)
                .hasMessageContaining("no prompt")
                .matches(e -> !((LLMException) e).isRetryable(), "is not retryable");
        org.mockito.Mockito.verifyNoInteractions(sdk);
    }

    @Test
    void shutdownClosesTheSdkClient() {
        com.openai.client.OpenAIClient sdk = mock(com.openai.client.OpenAIClient.class);

        new OpenAIClient(sdk).shutdown();

        verify(sdk).close();
    }
}
