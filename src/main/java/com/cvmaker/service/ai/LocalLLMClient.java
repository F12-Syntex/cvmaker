package com.cvmaker.service.ai;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LocalLLMClient implements LLMClient {

    private static final String LOG_LABEL = "Local LLM";

    private final String endpoint;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApiLog log;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public LocalLLMClient(String endpoint) {
        this(endpoint, new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build());
    }

    /**
     * Uses the given HTTP client. Tests pass one with an interceptor that
     * answers without the network.
     */
    public LocalLLMClient(String endpoint, OkHttpClient httpClient) {
        this(endpoint, httpClient, ApiLog.standard());
    }

    public LocalLLMClient(String endpoint, OkHttpClient httpClient, ApiLog log) {
        this.endpoint = stripTrailingSlash(Objects.requireNonNull(endpoint, "endpoint"));
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.log = Objects.requireNonNull(log, "log");
        this.objectMapper = new ObjectMapper();
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Host and port of the endpoint, for the plain socket check in
     * {@link #testConnection()}. Without an explicit port, the scheme decides:
     * 443 for https, otherwise 80.
     */
    static InetSocketAddress socketAddressOf(String endpoint) {
        URI uri = URI.create(endpoint.trim());
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Endpoint has no host: " + endpoint);
        }
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return InetSocketAddress.createUnresolved(host, port);
    }

    public void testConnection() {
        InetSocketAddress address = null;
        try {
            // First test basic connectivity
            address = socketAddressOf(endpoint);
            System.out.println("Testing basic connectivity to " + address.getHostString() + ":" + address.getPort() + "...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address.getHostString(), address.getPort()), 5000);
            }
            System.out.println("Basic connectivity: OK");

            // Then test HTTP
            String modelsUrl = endpoint + "/models";
            Request request = new Request.Builder()
                    .url(modelsUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Java-OkHttp-Client")
                    .get()
                    .build();

            System.out.println("Testing connection to: " + modelsUrl);

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = bodyOf(response);
                log.response(LOG_LABEL + " models", response.code(), responseBody);

                if (response.isSuccessful()) {
                    JsonNode models = objectMapper.readTree(responseBody).path("data");
                    System.out.println("Connection successful! Found " + models.size() + " models:");
                    for (JsonNode model : models) {
                        System.out.println("- " + model.path("id").asText());
                    }
                } else {
                    System.out.println("Connection failed with status code: " + response.code());
                    System.out.println("Response body: " + log.errorBody(responseBody));
                }
            }

        } catch (java.net.ConnectException e) {
            System.err.println("Connection refused - is the server running"
                    + (address != null ? " on port " + address.getPort() : "") + "?");
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

    /**
     * The JSON body for the OpenAI-compatible {@code /completions} endpoint.
     * The field names come from {@link ModelCapabilities#forLocalModel()}.
     */
    ObjectNode buildRequestBody(LLMRequest request) {
        ModelCapabilities capabilities = ModelCapabilities.forLocalModel();

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", request.getModel())
                .put("prompt", request.getPrompt());
        if (capabilities.supportsTemperature()) {
            requestBody.put("temperature", request.getTemperature());
        }
        requestBody.put(capabilities.getTokenLimitParameter().getFieldName(), request.getMaxTokens())
                .put("stream", false);
        return requestBody;
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        try {
            String url = endpoint + "/completions";
            String jsonBody = buildRequestBody(request).toString();

            Request httpRequest = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Java-OkHttp-Client")
                    .post(RequestBody.create(jsonBody, JSON))
                    .build();

            log.request(LOG_LABEL, url, jsonBody);

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = bodyOf(response);
                log.response(LOG_LABEL, response.code(), responseBody);

                if (!response.isSuccessful()) {
                    throw new LLMException("API returned status code: " + response.code()
                            + " with body: " + log.errorBody(responseBody),
                            LLMException.isRetryableStatus(response.code()));
                }

                JsonNode responseJson = objectMapper.readTree(responseBody);

                String content = responseJson.path("choices")
                        .path(0)
                        .path("text")
                        .asText("");

                UsageStats usage = null;
                JsonNode usageNode = responseJson.path("usage");
                if (usageNode.isObject()) {
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

        } catch (LLMException e) {
            throw e;
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

                JsonNode dataArray = objectMapper.readTree(bodyOf(response)).path("data");

                List<String> models = new ArrayList<>();
                for (JsonNode model : dataArray) {
                    models.add(model.path("id").asText());
                }
                return models;
            }

        } catch (IOException e) {
            throw new RuntimeException("IO error while getting models: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get models: " + e.getMessage(), e);
        }
    }

    private static String bodyOf(Response response) throws IOException {
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    @Override
    public void shutdown() {
        // Close the connection pool
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
