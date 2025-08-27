package com.cvmaker.service.ai;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AiService {

    private final LLMClient llmClient;
    private final ExecutorService executorService;
    private LLMModel model;
    private double temperature;

    public AiService(LLMModel model) {
        this.temperature = 0.3;
        this.executorService = Executors.newSingleThreadExecutor();

        this.model = model;
        this.llmClient = LLMClientFactory.createClient(this.model);

        System.out.println("Using default model: " + model.getModelName());
        System.out.println("url: " + Endpoint.OLLAMA_API.getUrl());
    }

    public AiService(LLMModel model, double temperature) {
        this(model);
        this.model = model;
        this.temperature = temperature;
    }

    public AiService(LLMProvider provider) {
        this.model = LLMModel.GPT_5_1_MINI;
        this.llmClient = LLMClientFactory.createClient(this.model);
        this.temperature = 0.3;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public AiService setModel(LLMModel model) {
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
            LLMRequest request = LLMRequest.builder()
                    .prompt(prompt)
                    .model(model.getModelName())
                    .temperature(temperature)
                    .build();

            CompletableFuture<LLMResponse> future = CompletableFuture.supplyAsync(
                    () -> llmClient.complete(request),
                    executorService
            );

            LLMResponse response = waitForCompletionWithProgress(future);

            UsageStats usage = response.getUsage();
            if (usage != null) {
                System.out.printf("📊 Token usage - Prompt: %d, Completion: %d, Total: %d\n",
                        usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
            }

            return response.getContent();
        } catch (Exception e) {
            throw new RuntimeException("Query failed: " + e.getMessage(), e);
        }
    }

    public String generateDirectLatexCV(String unstructuredText, String referenceTemplate, String jobDescription, String ai_prompt) {
        try {
            String prompt = buildDirectLatexGenerationPrompt(unstructuredText, referenceTemplate, jobDescription, ai_prompt);
            String response = queryWithProgress(prompt);
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX CV: " + e.getMessage(), e);
        }
    }

    public String generateDirectLatexCoverLetter(String unstructuredText, String referenceTemplate, String jobDescription, String coverLetterPrompt) {
        try {
            String prompt = buildDirectLatexCoverLetterPrompt(unstructuredText, referenceTemplate, jobDescription, coverLetterPrompt);
            String response = queryWithProgress(prompt);
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX cover letter: " + e.getMessage(), e);
        }
    }

    private LLMResponse waitForCompletionWithProgress(CompletableFuture<LLMResponse> future) {
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

        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
        }

        cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "")
                .replaceAll("```$", "")
                .replaceAll("^\\s*\\n+", "");

        if (!cleaned.startsWith("\\documentclass")) {
            int docStart = cleaned.indexOf("\\documentclass");
            if (docStart > 0) {
                cleaned = cleaned.substring(docStart);
            }
        }

        return cleaned.trim();
    }

    private String buildDirectLatexGenerationPrompt(String userData, String latexTemplate, String jobDescription, String ai_prompt) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(ai_prompt);
        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("JOB DESCRIPTION:\n");
            prompt.append(jobDescription).append("\n\n");
        }

        prompt.append("CANDIDATE INFORMATION:\n");
        prompt.append(userData).append("\n\n");

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

        prompt.append("CANDIDATE INFORMATION:\n");
        prompt.append(userData).append("\n\n");

        if (latexTemplate != null && !latexTemplate.trim().isEmpty()) {
            prompt.append("LATEX COVER LETTER TEMPLATE:\n");
            prompt.append(latexTemplate).append("\n\n");
        }

        return prompt.toString();
    }

    public void shutdown() {
        if (llmClient != null) {
            llmClient.shutdown();
        }
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

    public String answerApplicationQuestions(String userData, Map<String, Object> modalStructure) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are assisting with a job application on Reed.co.uk.\n");
        prompt.append("Candidate profile:\n").append(userData).append("\n\n");
        prompt.append("Modal/questions:\n").append(modalStructure.toString()).append("\n\n");
        prompt.append("Provide answers in JSON array of actions, e.g.:\n");
        prompt.append("[ {\"type\":\"SELECT_OPTION\",\"field\":\"19996198\",\"value\":\"Yes\"},\n");
        prompt.append("  {\"type\":\"CLICK_BUTTON\",\"value\":\"Continue\"} ]");

        return queryWithProgress(prompt.toString());
    }
}
