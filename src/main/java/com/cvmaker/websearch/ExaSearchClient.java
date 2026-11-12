package com.cvmaker.websearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.cvmaker.application.management.ApplicationConfig;
import com.cvmaker.service.ai.ApiLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ExaSearchClient {

    private static final String EXA_SEARCH_ENDPOINT = "https://api.exa.ai/search";
    private static final String EXA_ANSWER_ENDPOINT = "https://api.exa.ai/answer";
    private static final String EXA_CHAT_ENDPOINT = "https://api.exa.ai/chat/completions";

    /**
     * Bounds of {@code numResults} in the Exa search API (docs.exa.ai, read on
     * 2026-11-12): minimum 1, maximum 100, default 10.
     */
    static final int MIN_RESULTS = 1;
    static final int MAX_RESULTS = 100;
    static final int DEFAULT_RESULTS = 10;

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiLog log;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public ExaSearchClient(String apiKey) {
        this(apiKey, new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build());
    }

    /**
     * Uses the given HTTP client. Tests pass one with an interceptor that
     * answers without the network.
     */
    public ExaSearchClient(String apiKey, OkHttpClient httpClient) {
        this(apiKey, httpClient, ApiLog.standard());
    }

    public ExaSearchClient(String apiKey, OkHttpClient httpClient, ApiLog log) {
        this.apiKey = apiKey;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.log = Objects.requireNonNull(log, "log");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * True when an API key was given. The constructor accepts a missing key so
     * that code which never searches still starts; every request checks it.
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * A missing key is reported as an {@link IOException}, like any other
     * failed search, so callers that already handle a failed search (web
     * enrichment of emails) carry on without it.
     */
    private String requireApiKey() throws IOException {
        if (!hasApiKey()) {
            throw new IOException(
                    "Exa API key is not set. Set EXA_API_KEY in the environment or in .env");
        }
        return apiKey.trim();
    }

    public List<SearchResult> search(SearchRequest searchRequest) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(buildSearchBody(searchRequest));
        String responseBody = post("Exa search", EXA_SEARCH_ENDPOINT, jsonBody);
        return parseSearchResults(responseBody);
    }

    /**
     * The JSON body for {@code POST /search}: the query, the number of results
     * (kept within 1 to 100) and the include and exclude domain lists when
     * they are not empty. {@link SearchRequest#isUseAutoprompt()} is not sent:
     * the current API has no such field, and {@code type: auto} covers it.
     */
    ObjectNode buildSearchBody(SearchRequest searchRequest) {
        if (searchRequest.getQuery() == null || searchRequest.getQuery().isBlank()) {
            throw new IllegalArgumentException("Search query is empty");
        }

        ObjectNode requestBody = objectMapper.createObjectNode()
                .put("query", searchRequest.getQuery())
                .put("type", "auto")
                .put("numResults", clampResults(searchRequest.getNumResults()));

        addDomains(requestBody, "includeDomains", searchRequest.getIncludeDomains());
        addDomains(requestBody, "excludeDomains", searchRequest.getExcludeDomains());

        requestBody.putObject("contents").put("text", true);
        return requestBody;
    }

    static int clampResults(int requested) {
        if (requested < MIN_RESULTS) {
            return DEFAULT_RESULTS;
        }
        return Math.min(requested, MAX_RESULTS);
    }

    /**
     * Splits a comma-separated domain list, dropping blanks and duplicates.
     */
    static List<String> parseDomains(String commaSeparated) {
        List<String> domains = new ArrayList<>();
        if (commaSeparated == null) {
            return domains;
        }
        for (String part : commaSeparated.split(",")) {
            String domain = part.trim();
            if (!domain.isEmpty() && !domains.contains(domain)) {
                domains.add(domain);
            }
        }
        return domains;
    }

    private void addDomains(ObjectNode requestBody, String field, String commaSeparated) {
        List<String> domains = parseDomains(commaSeparated);
        if (!domains.isEmpty()) {
            ArrayNode array = requestBody.putArray(field);
            domains.forEach(array::add);
        }
    }

    public String getAnswer(String query) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode()
                .put("query", query)
                .put("text", true);

        String responseBody = post("Exa answer", EXA_ANSWER_ENDPOINT, objectMapper.writeValueAsString(requestBody));
        return text(objectMapper.readTree(responseBody), "answer");
    }

    public String chat(String message) throws IOException {
        ObjectNode systemMessage = objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", "You are a helpful assistant.");

        ObjectNode userMessage = objectMapper.createObjectNode()
                .put("role", "user")
                .put("content", message);

        ObjectNode extraBody = objectMapper.createObjectNode()
                .put("text", true);

        ObjectNode requestBody = objectMapper.createObjectNode()
                .put("model", "exa")
                .set("extra_body", extraBody);

        requestBody.putArray("messages")
                .add(systemMessage)
                .add(userMessage);

        String responseBody = post("Exa chat", EXA_CHAT_ENDPOINT, objectMapper.writeValueAsString(requestBody));
        return text(objectMapper.readTree(responseBody).path("choices").path(0).path("message"), "content");
    }

    /**
     * Sends a JSON body with the API key and returns the response body. Only
     * the URL, the status and the body sizes are logged, see {@link ApiLog}.
     *
     * @throws IOException when no API key is set or the API answers with an
     * error status
     */
    private String post(String label, String url, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-api-key", requireApiKey())
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        log.request(label, url, jsonBody);

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseBody = body == null ? "" : body.string();
            log.response(label, response.code(), responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("API request failed with code " + response.code() + ": "
                        + log.errorBody(responseBody));
            }
            return responseBody;
        }
    }

    List<SearchResult> parseSearchResults(String responseBody) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        JsonNode rootNode = objectMapper.readTree(responseBody);

        rootNode.path("results").forEach(result -> {
            // The API returns publishedDate; published_date is read as a fallback
            // for older responses.
            String publishedDate = text(result, "publishedDate");
            if (publishedDate.isEmpty()) {
                publishedDate = text(result, "published_date");
            }
            results.add(new SearchResult(
                    text(result, "title"),
                    text(result, "url"),
                    text(result, "text"),
                    publishedDate
            ));
        });

        return results;
    }

    /**
     * Text of a field, or an empty string when it is missing or JSON null
     * (Jackson would otherwise give the word "null").
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // Main method for testing
    public static void main(String[] args) {
        try {
            String apiKey = ApplicationConfig.getExaApiKey();
            ExaSearchClient client = new ExaSearchClient(apiKey);

            // Test search
            System.out.println("\nTesting search...");
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("Latest developments in artificial intelligence")
                    .build();
            List<SearchResult> results = client.search(searchRequest);

            System.out.println("Search Results:");
            for (SearchResult result : results) {
                System.out.println("\nTitle: " + result.getTitle());
                System.out.println("URL: " + result.getUrl());
                System.out.println("Text: " + result.getSnippet());
                System.out.println("Published Date: " + result.getPublishedDate());
            }

            // Test answer
            System.out.println("\nTesting answer...");
            String answer = client.getAnswer("What are the latest findings on gut microbiome's influence on mental health?");
            System.out.println("Answer: " + answer);

            // Test chat
            System.out.println("\nTesting chat...");
            String chatResponse = client.chat("What are the latest developments in quantum computing?");
            System.out.println("Chat Response: " + chatResponse);

            client.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
