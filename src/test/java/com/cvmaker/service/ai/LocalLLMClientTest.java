package com.cvmaker.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LocalLLMClientTest {

    private static final String ENDPOINT = "http://localhost:1234/v1";
    private static final String SECRET_PROMPT = "Jane Doe, 12 Secret Street, jane@example.com";
    private static final String SECRET_ANSWER = "Dear Jane of Secret Street";
    private static final String OK_BODY = """
            {"choices":[{"text":"%s"}],
             "usage":{"prompt_tokens":12,"completion_tokens":5,"total_tokens":17}}
            """.formatted(SECRET_ANSWER);

    private final StubHttp http = new StubHttp();
    private final ObjectMapper mapper = new ObjectMapper();

    private LocalLLMClient client(boolean debug) {
        return new LocalLLMClient(ENDPOINT, http.client(), http.log(debug));
    }

    private static LLMRequest request() {
        return LLMRequest.builder().prompt(SECRET_PROMPT).model("mistralai/mistral-small-3.2")
                .temperature(0.4).maxTokens(900).build();
    }

    // ---- request and response mapping ----
    @Test
    void completePostsTheCompletionRequestAndMapsTheAnswer() throws Exception {
        http.respondWith(200, OK_BODY);

        LLMResponse response = client(false).complete(request());

        assertThat(http.requests).hasSize(1);
        assertThat(http.requests.get(0).method()).isEqualTo("POST");
        assertThat(http.requests.get(0).url().toString()).isEqualTo(ENDPOINT + "/completions");
        JsonNode sent = mapper.readTree(http.requestBodies.get(0));
        assertThat(sent.path("model").asText()).isEqualTo("mistralai/mistral-small-3.2");
        assertThat(sent.path("prompt").asText()).isEqualTo(SECRET_PROMPT);
        assertThat(sent.path("temperature").asDouble()).isEqualTo(0.4);
        assertThat(sent.path("max_tokens").asInt()).isEqualTo(900);
        assertThat(sent.path("stream").asBoolean()).isFalse();
        assertThat(sent.has("max_completion_tokens")).isFalse();

        assertThat(response.getContent()).isEqualTo(SECRET_ANSWER);
        assertThat(response.getModel()).isEqualTo("mistralai/mistral-small-3.2");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(12);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(5);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(17);
    }

    @Test
    void defaultTokenLimitIsSentWhenTheCallerSetNone() throws Exception {
        http.respondWith(200, OK_BODY);

        client(false).complete(LLMRequest.builder().prompt("hi").model("m").build());

        assertThat(mapper.readTree(http.requestBodies.get(0)).path("max_tokens").asInt())
                .isEqualTo(LLMRequest.DEFAULT_MAX_TOKENS);
    }

    @Test
    void responseWithoutUsageOrWithNullUsageIsAccepted() {
        http.respondWith(200, "{\"choices\":[{\"text\":\"hello\"}]}");
        assertThat(client(false).complete(request()).getUsage()).isNull();

        http.respondWith(200, "{\"choices\":[{\"text\":\"hello\"}],\"usage\":null}");
        LLMResponse response = client(false).complete(request());
        assertThat(response.getUsage()).isNull();
        assertThat(response.getContent()).isEqualTo("hello");
    }

    @Test
    void trailingSlashOnTheEndpointDoesNotDoubleUp() {
        http.respondWith(200, OK_BODY);

        new LocalLLMClient(ENDPOINT + "/", http.client(), http.log(false)).complete(request());

        assertThat(http.requests.get(0).url().toString()).isEqualTo(ENDPOINT + "/completions");
    }

    @Test
    void getAvailableModelsReadsTheIds() {
        http.respondWith(200, "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}");

        assertThat(client(false).getAvailableModels()).containsExactly("model-a", "model-b");
        assertThat(http.requests.get(0).url().toString()).isEqualTo(ENDPOINT + "/models");
        assertThat(http.requests.get(0).method()).isEqualTo("GET");
    }

    // ---- errors ----
    @Test
    void clientErrorIsNotRetryableAndServerErrorIs() {
        http.respondWith(400, "{\"error\":\"bad request\"}");
        assertThatThrownBy(() -> client(false).complete(request()))
                .isInstanceOf(LLMException.class)
                .hasMessageContaining("status code: 400")
                .hasMessageContaining("bad request")
                .matches(e -> !((LLMException) e).isRetryable(), "is not retryable");

        http.respondWith(503, "{\"error\":\"loading model\"}");
        assertThatThrownBy(() -> client(false).complete(request()))
                .isInstanceOf(LLMException.class)
                .matches(e -> ((LLMException) e).isRetryable(), "is retryable");
    }

    @Test
    void longErrorBodyIsCutInTheExceptionMessage() {
        String echo = "x".repeat(250) + SECRET_PROMPT + "y".repeat(500);
        http.respondWith(500, echo);

        assertThatThrownBy(() -> client(false).complete(request()))
                .hasMessageContaining("x".repeat(250))
                .hasMessageNotContaining("y".repeat(100))
                .hasMessageContaining("more chars");
    }

    // ---- log redaction ----
    @Test
    void promptAndAnswerAreNotLoggedByDefault() {
        http.respondWith(200, OK_BODY);

        client(false).complete(request());

        assertThat(http.logLines).isNotEmpty();
        assertThat(http.allLogText())
                .doesNotContain(SECRET_PROMPT)
                .doesNotContain("Secret Street")
                .doesNotContain(SECRET_ANSWER)
                .contains(ENDPOINT + "/completions")
                .contains("HTTP 200")
                .contains(OK_BODY.length() + " chars");
    }

    @Test
    void failedCallDoesNotLogTheBodiesEither() {
        http.respondWith(500, "echo: " + SECRET_PROMPT);

        assertThatThrownBy(() -> client(false).complete(request())).isInstanceOf(LLMException.class);

        assertThat(http.allLogText()).doesNotContain("Secret Street").contains("HTTP 500");
    }

    @Test
    void debugModeLogsTheBodies() {
        http.respondWith(200, OK_BODY);

        client(true).complete(request());

        assertThat(http.allLogText()).contains(SECRET_PROMPT).contains(SECRET_ANSWER);
    }

    // ---- testConnection address ----
    @Test
    void connectionTestAddressComesFromTheEndpoint() {
        InetSocketAddress custom = LocalLLMClient.socketAddressOf("http://192.168.1.20:11434/v1");
        assertThat(custom.getHostString()).isEqualTo("192.168.1.20");
        assertThat(custom.getPort()).isEqualTo(11434);

        InetSocketAddress lmStudio = LocalLLMClient.socketAddressOf(ENDPOINT);
        assertThat(lmStudio.getHostString()).isEqualTo("localhost");
        assertThat(lmStudio.getPort()).isEqualTo(1234);
    }

    @Test
    void connectionTestAddressDefaultsThePortFromTheScheme() {
        assertThat(LocalLLMClient.socketAddressOf("https://llm.example.com/v1").getPort()).isEqualTo(443);
        assertThat(LocalLLMClient.socketAddressOf("http://llm.example.com/v1").getPort()).isEqualTo(80);
        assertThatThrownBy(() -> LocalLLMClient.socketAddressOf("not a url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
