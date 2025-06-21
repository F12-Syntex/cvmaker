package com.cvmaker;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class AiService {

    private final OpenAIClient client;
    private ChatModel model;
    private double temperature;
    private final ExecutorService executorService;

    public AiService() {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.3;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public AiService(ChatModel model, double temperature) {
        this();
        this.model = model;
        this.temperature = temperature;
    }

    public AiService(OpenAIClient client) {
        this.client = client;
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.3;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public AiService setModel(ChatModel model) {
        this.model = model;
        return this;
    }

    public AiService setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public String query(String prompt) {
        return queryWithProgress(prompt);
    }

    public String queryWithProgress(String prompt) {
        try {

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(model)
                    .temperature(temperature)
                    .build();

            CompletableFuture<ChatCompletion> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return client.chat().completions().create(params);
                } catch (Exception e) {
                    throw new RuntimeException("AI API call failed: " + e.getMessage(), e);
                }
            }, executorService);

            ChatCompletion completion = waitForCompletionWithProgress(future);

            var usage = completion.usage().get();
            System.out.printf("📊 Token usage - Prompt: %d, Completion: %d, Total: %d\n",
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens());

            Optional<String> responseContent = completion.choices().get(0).message().content();

            if (!responseContent.isPresent()) {
                throw new RuntimeException("Empty response from OpenAI API");
            }

            return responseContent.get();
        } catch (RuntimeException e) {
            throw new RuntimeException("Error while querying OpenAI API", e);
        }
    }

    /**
     * Generate a complete LaTeX CV directly with enhanced progress tracking
     */
    public String generateDirectLatexCV(String unstructuredText, String referenceTemplate, String jobDescription, String ai_prompt) {
        try {
            String prompt = buildDirectLatexGenerationPrompt(unstructuredText, referenceTemplate, jobDescription, ai_prompt);
            String response = queryWithProgress(prompt);
            String result = extractLatexFromResponse(response);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX CV: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a complete LaTeX cover letter with enhanced progress tracking
     */
    public String generateDirectLatexCoverLetter(String unstructuredText, String referenceTemplate, String jobDescription, String coverLetterPrompt) {
        try {
            String prompt = buildDirectLatexCoverLetterPrompt(unstructuredText, referenceTemplate, jobDescription, coverLetterPrompt);
            String response = queryWithProgress(prompt);
            String result = extractLatexFromResponse(response);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX cover letter: " + e.getMessage(), e);
        }
    }

    private ChatCompletion waitForCompletionWithProgress(CompletableFuture<ChatCompletion> future) {
        try {
            long startTime = System.currentTimeMillis();

            while (!future.isDone()) {
                Thread.sleep(1000);
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed > 5000) {
                    System.out.printf("\r   🤖 AI processing... (%ds elapsed)   ", elapsed / 1000);
                }

                if (elapsed > 30000) {
                    System.out.printf("\r   ⏳ Complex AI operation in progress... (%ds elapsed)   ", elapsed / 1000);
                }
            }

            System.out.print("\r   ✅ AI operation completed!             \n");
            return future.get(60, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.out.print("\r   ❌ AI operation failed!               \n");
            throw new RuntimeException("AI operation timed out or failed: " + e.getMessage(), e);
        }
    }

    private String extractLatexFromResponse(String response) {
        String cleaned = response.trim();

        // Handle code blocks more comprehensively
        if (cleaned.startsWith("```")) {
            // Find the first newline after opening ```
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }

            // Remove closing ```
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
        }

        // Remove any remaining markdown artifacts
        cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "")
                .replaceAll("```$", "");

        // Remove excessive whitespace at the beginning
        cleaned = cleaned.replaceAll("^\\s*\\n+", "");

        // Ensure document starts with \documentclass
        if (!cleaned.startsWith("\\documentclass")) {
            int docStart = cleaned.indexOf("\\documentclass");
            if (docStart > 0) {
                cleaned = cleaned.substring(docStart);
            }
        }

        return cleaned.trim();
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private String buildDirectLatexGenerationPrompt(String userData, String latexTemplate, String jobDescription, String ai_prompt) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(ai_prompt);
        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("JOB DESCRIPTION:\n");
            prompt.append(jobDescription).append("\n\n");
        }

        // Candidate data
        prompt.append("CANDIDATE INFORMATION:\n");
        prompt.append(userData).append("\n\n");

        // Reference template
        if (latexTemplate != null && !latexTemplate.trim().isEmpty()) {
            prompt.append("LATEX TEMPLATE:\n");
            prompt.append(latexTemplate).append("\n\n");
        }

        return prompt.toString();
    }

    private String buildDirectLatexCoverLetterPrompt(String userData, String latexTemplate, String jobDescription, String coverLetterPrompt) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(coverLetterPrompt);

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("JOB DESCRIPTION:\n");
            prompt.append(jobDescription).append("\n\n");
        }

        // Candidate data
        prompt.append("CANDIDATE INFORMATION:\n");
        prompt.append(userData).append("\n\n");

        // Reference template
        if (latexTemplate != null && !latexTemplate.trim().isEmpty()) {
            prompt.append("LATEX COVER LETTER TEMPLATE:\n");
            prompt.append(latexTemplate).append("\n\n");
        }

        return prompt.toString();
    }
}
