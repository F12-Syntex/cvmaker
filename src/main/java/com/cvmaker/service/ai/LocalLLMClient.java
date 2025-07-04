package com.cvmaker.service.ai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LocalLLMClient implements LLMClient {

    private final String endpoint;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public LocalLLMClient(String endpoint) {
        this.endpoint = endpoint;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void testConnection() {
        try {
            // First test basic connectivity
            System.out.println("Testing basic connectivity...");
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", 1234), 5000);
            socket.close();
            System.out.println("Basic connectivity: OK");

            // Then test HTTP
            Request request = new Request.Builder()
                    .url(endpoint + "/models")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Java-OkHttp-Client")
                    .get()
                    .build();

            System.out.println("Testing connection to: " + endpoint + "models");

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                System.out.println("Response status: " + response.code());
                System.out.println("Response body: " + responseBody);

                if (response.isSuccessful()) {
                    ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);
                    ArrayNode models = (ArrayNode) responseJson.get("data");
                    System.out.println("Connection successful! Found " + models.size() + " models:");
                    for (JsonNode model : models) {
                        System.out.println("- " + model.get("id").asText());
                    }
                } else {
                    System.out.println("Connection failed with status code: " + response.code());
                    System.out.println("Response body: " + responseBody);
                }
            }

        } catch (java.net.ConnectException e) {
            System.err.println("Connection refused - is the server running on port 1234?");
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Socket timeout - server is not responding");
        } catch (IOException e) {
            System.err.println("IO Exception: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", request.getModel())
                    .put("prompt", request.getPrompt())
                    .put("temperature", request.getTemperature())
                    .put("max_tokens", request.getMaxTokens())
                    .put("stream", false);

            RequestBody body = RequestBody.create(requestBody.toString(), JSON);

            Request httpRequest = new Request.Builder()
                    .url(endpoint + "/completions")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Java-OkHttp-Client")
                    .post(body)
                    .build();

            System.out.println("Sending request to: " + endpoint + " completions");

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body().string();

                System.out.println("Response status: " + response.code());
                System.out.println("Response body: " + responseBody);

                if (!response.isSuccessful()) {
                    throw new RuntimeException("API returned status code: " + response.code()
                            + " with body: " + responseBody);
                }

                ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);

                String content = responseJson.path("choices")
                        .path(0)
                        .path("text")
                        .asText("");

                UsageStats usage = null;
                if (responseJson.has("usage")) {
                    ObjectNode usageNode = (ObjectNode) responseJson.get("usage");
                    usage = new UsageStats(
                            usageNode.path("prompt_tokens").asInt(),
                            usageNode.path("completion_tokens").asInt(),
                            usageNode.path("total_tokens").asInt()
                    );
                }

                return LLMResponse.builder()
                        .content(content)
                        .usage(usage)
                        .model(request.getModel())
                        .build();
            }

        } catch (IOException e) {
            throw new RuntimeException("IO error during API call: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("API call failed: " + e.getMessage(), e);
        }
    }

    public List<String> getAvailableModels() {
        try {
            Request request = new Request.Builder()
                    .url(endpoint + "/models")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Java-OkHttp-Client")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to get models. Status code: " + response.code());
                }

                String responseBody = response.body().string();
                ObjectNode responseJson = objectMapper.readValue(responseBody, ObjectNode.class);
                ArrayNode dataArray = (ArrayNode) responseJson.get("data");

                List<String> models = new ArrayList<>();
                for (JsonNode model : dataArray) {
                    models.add(model.get("id").asText());
                }
                return models;
            }

        } catch (IOException e) {
            throw new RuntimeException("IO error while getting models: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get models: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        // Close the connection pool
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
