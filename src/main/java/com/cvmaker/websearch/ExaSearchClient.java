package com.cvmaker.websearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.cvmaker.application.management.ApplicationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ExaSearchClient {

    private static final String EXA_SEARCH_ENDPOINT = "https://api.exa.ai/search";
    private static final String EXA_ANSWER_ENDPOINT = "https://api.exa.ai/answer";
    private static final String EXA_CHAT_ENDPOINT = "https://api.exa.ai/chat/completions";

    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public ExaSearchClient(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public List<SearchResult> search(SearchRequest searchRequest) throws IOException {
        ObjectNode contentsNode = objectMapper.createObjectNode();
        contentsNode.put("text", true);

        ObjectNode requestBody = objectMapper.createObjectNode()
                .put("query", searchRequest.getQuery())
                .put("type", "auto")
                .set("contents", contentsNode);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        System.out.println("Search request body: " + jsonBody);

        Request request = new Request.Builder()
                .url(EXA_SEARCH_ENDPOINT)
                .addHeader("x-api-key", apiKey)
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        return executeRequest(request);
    }

    public String getAnswer(String query) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode()
                .put("query", query)
                .put("text", true);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        System.out.println("Answer request body: " + jsonBody);

        Request request = new Request.Builder()
                .url(EXA_ANSWER_ENDPOINT)
                .addHeader("x-api-key", apiKey)
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("API request failed with code " + response.code() + ": " + responseBody);
            }

            ObjectNode rootNode = objectMapper.readValue(responseBody, ObjectNode.class);
            return rootNode.path("answer").asText();
        }
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

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        System.out.println("Chat request body: " + jsonBody);

        Request request = new Request.Builder()
                .url(EXA_CHAT_ENDPOINT)
                .addHeader("x-api-key", apiKey)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("API request failed with code " + response.code() + ": " + responseBody);
            }

            ObjectNode rootNode = objectMapper.readValue(responseBody, ObjectNode.class);
            return rootNode.path("choices").get(0).path("message").path("content").asText();
        }
    }

    private List<SearchResult> executeRequest(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + responseBody);

            if (!response.isSuccessful()) {
                throw new IOException("API request failed with code " + response.code() + ": " + responseBody);
            }

            return parseSearchResults(responseBody);
        }
    }

    private List<SearchResult> parseSearchResults(String responseBody) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        ObjectNode rootNode = objectMapper.readValue(responseBody, ObjectNode.class);

        rootNode.path("results").forEach(result -> {
            results.add(new SearchResult(
                    result.path("title").asText(),
                    result.path("url").asText(),
                    result.path("text").asText(),
                    result.path("published_date").asText()
            ));
        });

        return results;
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
