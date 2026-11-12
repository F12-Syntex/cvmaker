package com.cvmaker.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cvmaker.service.ai.StubHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ExaSearchClientTest {

    private static final String API_KEY = "exa-test-key-123";
    private static final String SECRET_QUERY = "\"Acme Secret Corp\" recruiter email contact";
    private static final String SECRET_TEXT = "Contact Jane Secret at jane@acme.example";
    private static final String RESULTS_BODY = """
            {"results":[
              {"title":"Careers at Acme","url":"https://acme.example/careers","text":"%s",
               "publishedDate":"2026-10-01T00:00:00.000Z"},
              {"title":"Old format","url":"https://old.example","text":"t2","published_date":"2020-01-02"},
              {"title":null,"url":"https://nodate.example","text":"t3","publishedDate":null}
            ]}
            """.formatted(SECRET_TEXT);

    private final StubHttp http = new StubHttp();
    private final ObjectMapper mapper = new ObjectMapper();

    private ExaSearchClient client(boolean debug) {
        return new ExaSearchClient(API_KEY, http.client(), http.log(debug));
    }

    private JsonNode sentBody() throws IOException {
        return mapper.readTree(http.requestBodies.get(0));
    }

    private static List<String> texts(JsonNode array) {
        return mapper().convertValue(array, mapper().getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper();
    }

    // ---- request mapping ----
    @Test
    void searchSendsQueryOptionsAndKey() throws Exception {
        http.respondWith(200, RESULTS_BODY);
        SearchRequest request = SearchRequest.builder()
                .query(SECRET_QUERY)
                .numResults(5)
                .includeDomains("linkedin.com, acme.example ,,linkedin.com")
                .excludeDomains("spam.example")
                .build();

        client(false).search(request);

        assertThat(http.requests).hasSize(1);
        assertThat(http.requests.get(0).url().toString()).isEqualTo("https://api.exa.ai/search");
        assertThat(http.requests.get(0).method()).isEqualTo("POST");
        assertThat(http.requests.get(0).header("x-api-key")).isEqualTo(API_KEY);
        JsonNode sent = sentBody();
        assertThat(sent.path("query").asText()).isEqualTo(SECRET_QUERY);
        assertThat(sent.path("type").asText()).isEqualTo("auto");
        assertThat(sent.path("numResults").asInt()).isEqualTo(5);
        assertThat(texts(sent.path("includeDomains"))).containsExactly("linkedin.com", "acme.example");
        assertThat(texts(sent.path("excludeDomains"))).containsExactly("spam.example");
        assertThat(sent.path("contents").path("text").asBoolean()).isTrue();
    }

    @Test
    void emptyDomainListsAreLeftOutOfTheRequest() throws Exception {
        http.respondWith(200, RESULTS_BODY);

        client(false).search(SearchRequest.builder().query("java jobs").build());

        JsonNode sent = sentBody();
        assertThat(sent.path("numResults").asInt()).isEqualTo(10);
        assertThat(sent.has("includeDomains")).isFalse();
        assertThat(sent.has("excludeDomains")).isFalse();
    }

    @Test
    void nullDomainListsAreLeftOutOfTheRequest() throws Exception {
        http.respondWith(200, RESULTS_BODY);

        new SearchService(client(false)).searchWithOptions("java jobs", 3, null, null);

        JsonNode sent = sentBody();
        assertThat(sent.path("numResults").asInt()).isEqualTo(3);
        assertThat(sent.has("includeDomains")).isFalse();
        assertThat(sent.has("excludeDomains")).isFalse();
    }

    @Test
    void numResultsIsKeptWithinTheApiBounds() {
        assertThat(ExaSearchClient.clampResults(0)).isEqualTo(10);
        assertThat(ExaSearchClient.clampResults(-4)).isEqualTo(10);
        assertThat(ExaSearchClient.clampResults(1)).isEqualTo(1);
        assertThat(ExaSearchClient.clampResults(100)).isEqualTo(100);
        assertThat(ExaSearchClient.clampResults(5000)).isEqualTo(100);
    }

    @Test
    void searchServicePassesItsOptionsThrough() throws Exception {
        http.respondWith(200, RESULTS_BODY);
        SearchService service = new SearchService(client(false));

        List<SearchResult> results = service.searchWithOptions("java jobs", 7, "reed.co.uk", "spam.example,ads.example");

        JsonNode sent = sentBody();
        assertThat(sent.path("numResults").asInt()).isEqualTo(7);
        assertThat(texts(sent.path("includeDomains"))).containsExactly("reed.co.uk");
        assertThat(texts(sent.path("excludeDomains"))).containsExactly("spam.example", "ads.example");
        assertThat(results).hasSize(3);
    }

    @Test
    void blankQueryIsRejectedBeforeAnyCall() {
        assertThatThrownBy(() -> client(false).search(SearchRequest.builder().query("  ").build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(http.requests).isEmpty();
    }

    // ---- response mapping ----
    @Test
    void resultsAreMappedIncludingThePublishedDate() throws Exception {
        http.respondWith(200, RESULTS_BODY);

        List<SearchResult> results = client(false).search(SearchRequest.builder().query("acme").build());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTitle()).isEqualTo("Careers at Acme");
        assertThat(results.get(0).getUrl()).isEqualTo("https://acme.example/careers");
        assertThat(results.get(0).getSnippet()).isEqualTo(SECRET_TEXT);
        assertThat(results.get(0).getPublishedDate()).isEqualTo("2026-10-01T00:00:00.000Z");
    }

    @Test
    void oldDateFieldIsStillReadAndNullsBecomeEmptyStrings() throws Exception {
        http.respondWith(200, RESULTS_BODY);

        List<SearchResult> results = client(false).search(SearchRequest.builder().query("acme").build());

        assertThat(results.get(1).getPublishedDate()).isEqualTo("2020-01-02");
        assertThat(results.get(2).getPublishedDate()).isEmpty();
        assertThat(results.get(2).getTitle()).isEmpty();
    }

    @Test
    void responseWithoutResultsGivesAnEmptyList() throws Exception {
        http.respondWith(200, "{}");

        assertThat(client(false).search(SearchRequest.builder().query("acme").build())).isEmpty();
    }

    @Test
    void answerAndChatReadTheirFields() throws Exception {
        http.respondWith(200, "{\"answer\":\"Forty-two\"}");
        assertThat(client(false).getAnswer("meaning of life")).isEqualTo("Forty-two");
        assertThat(http.requests.get(0).url().toString()).isEqualTo("https://api.exa.ai/answer");

        http.respondWith(200, "{\"choices\":[{\"message\":{\"content\":\"Hello\"}}]}");
        assertThat(client(false).chat("hi")).isEqualTo("Hello");
        assertThat(http.requests.get(1).url().toString()).isEqualTo("https://api.exa.ai/chat/completions");

        http.respondWith(200, "{\"choices\":[]}");
        assertThat(client(false).chat("hi")).isEmpty();
    }

    // ---- errors ----
    @Test
    void missingApiKeyIsAClearIoErrorAndNothingIsSent() {
        for (String key : new String[]{null, "", "   "}) {
            ExaSearchClient noKey = new ExaSearchClient(key, http.client(), http.log(false));

            assertThat(noKey.hasApiKey()).isFalse();
            assertThatThrownBy(() -> noKey.search(SearchRequest.builder().query("acme").build()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("EXA_API_KEY");
            assertThatThrownBy(() -> noKey.getAnswer("acme")).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> noKey.chat("acme")).isInstanceOf(IOException.class);
        }
        assertThat(http.requests).isEmpty();
    }

    @Test
    void errorStatusBecomesAnIoExceptionWithAShortBody() {
        http.respondWith(401, "{\"error\":\"invalid api key\"}");
        assertThatThrownBy(() -> client(false).search(SearchRequest.builder().query("acme").build()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("invalid api key");

        http.respondWith(500, "x".repeat(300) + SECRET_TEXT);
        assertThatThrownBy(() -> client(false).search(SearchRequest.builder().query("acme").build()))
                .isInstanceOf(IOException.class)
                .hasMessageNotContaining("Jane Secret");
    }

    // ---- log redaction ----
    @Test
    void queryResultsAndKeyAreNotLoggedByDefault() throws Exception {
        http.respondWith(200, RESULTS_BODY);

        client(false).search(SearchRequest.builder().query(SECRET_QUERY).build());
        http.respondWith(200, "{\"answer\":\"" + SECRET_TEXT + "\"}");
        client(false).getAnswer(SECRET_QUERY);
        http.respondWith(200, "{\"choices\":[{\"message\":{\"content\":\"" + SECRET_TEXT + "\"}}]}");
        client(false).chat(SECRET_QUERY);

        assertThat(http.logLines).hasSize(6);
        assertThat(http.allLogText())
                .doesNotContain("Acme Secret Corp")
                .doesNotContain("Jane Secret")
                .doesNotContain(API_KEY)
                .contains("https://api.exa.ai/search")
                .contains("HTTP 200")
                .contains(RESULTS_BODY.length() + " chars");
    }

    @Test
    void debugModeLogsTheBodiesButNeverTheKey() throws Exception {
        http.respondWith(200, RESULTS_BODY);

        client(true).search(SearchRequest.builder().query(SECRET_QUERY).build());

        assertThat(http.allLogText())
                .contains("Acme Secret Corp")
                .contains("Jane Secret")
                .doesNotContain(API_KEY);
    }
}
