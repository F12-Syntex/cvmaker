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
        return queryWithProgress(prompt, null);
    }

    public String queryWithProgress(String prompt, String operationDescription) {
        try {
            if (operationDescription != null) {
                showAIOperationProgress(operationDescription);
            }

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

            ChatCompletion completion = waitForCompletionWithProgress(future, operationDescription);

            if (completion.usage() != null) {
                var usage = completion.usage().get();
                System.out.printf("≡ƒöó Token usage - Prompt: %d, Completion: %d, Total: %d\n",
                        usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
            }

            Optional<String> responseContent = completion.choices().get(0).message().content();

            if (!responseContent.isPresent()) {
                throw new RuntimeException("Empty response from OpenAI API");
            }

            return responseContent.get();
        } catch (Exception e) {
            throw new RuntimeException("Error while querying OpenAI API", e);
        }
    }

    /**
     * Generate a complete LaTeX CV directly with enhanced progress tracking
     */
    public String generateDirectLatexCV(String unstructuredText, String referenceTemplate, String jobDescription) {
        try {
            ProgressTracker cvProgress = ProgressTracker.create("AI LaTeX CV Generation", 4);

            cvProgress.nextStep("Analyzing CV content and requirements");
            Thread.sleep(500);

            cvProgress.nextStep("Processing job description and tailoring content");
            Thread.sleep(500);

            cvProgress.startStep("Generating complete LaTeX document");
            String prompt = buildDirectLatexGenerationPrompt(unstructuredText, referenceTemplate, jobDescription);
            String response = queryWithProgress(prompt, "Creating professional LaTeX CV document");
            cvProgress.completeStep("LaTeX document generated successfully");

            cvProgress.nextStep("Finalizing CV formatting and structure");
            String result = extractLatexFromResponse(response);

            cvProgress.complete();
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX CV: " + e.getMessage(), e);
        }
    }

    private void showAIOperationProgress(String operation) {
        System.out.printf("≡ƒñû AI Operation: %s\n", operation);
    }

    private ChatCompletion waitForCompletionWithProgress(CompletableFuture<ChatCompletion> future, String operation) {
        try {
            long startTime = System.currentTimeMillis();

            while (!future.isDone()) {
                Thread.sleep(1000);
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed > 5000) {
                    System.out.printf("\r   ΓÅ│ AI processing... (%ds elapsed)   ", elapsed / 1000);
                }

                if (elapsed > 30000) {
                    System.out.printf("\r   ≡ƒºá Complex AI operation in progress... (%ds elapsed)   ", elapsed / 1000);
                }
            }

            System.out.print("\r   Γ£à AI operation completed!             \n");
            return future.get(60, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.out.print("\r   Γ¥î AI operation failed!               \n");
            throw new RuntimeException("AI operation timed out or failed: " + e.getMessage(), e);
        }
    }

    private String extractLatexFromResponse(String response) {
        String cleaned = response.trim();

        if (cleaned.startsWith("```latex") || cleaned.startsWith("```tex")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
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

    private String buildDirectLatexGenerationPrompt(String userData, String latexTemplate, String jobDescription) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an elite CV writer and a LaTeX expert. Your job is to craft a compelling, tailored CV for a top-tier job application, using the provided LaTeX template. ");
        prompt.append("Your output must be precise, achievement-focused, and highly relevant to the specific job and company—no generic or exaggerated claims, no filler, no buzzwords. ");
        prompt.append("Every line must show clear, concrete value, using only the candidate's real experience and verifiable achievements. ");
        prompt.append("Never fabricate or embellish. Only include content that is truthful, evidence-based, and directly relevant.\n\n");

        // Deep analysis of the job description
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

        prompt.append("INSTRUCTIONS:\n");
        prompt.append("1. Analyze the job description to understand the company's true priorities and what would impress a top decision-maker.\n");
        prompt.append("2. Carefully review the candidate information. Select only the most significant, measurable, and relevant achievements. If you can truthfully infer additional relevant impact, do so—but never invent or exaggerate.\n");
        prompt.append("3. For each section, include only what directly matches the job requirements and would be valued by a hiring manager. Omit everything else.\n");
        prompt.append("4. Use action-driven, specific language. Quantify achievements (with numbers, percentages, or tangible results) wherever possible.\n");
        prompt.append("5. Avoid all weak, vague, or cliché phrases hiring managers dislike: do NOT use words like 'responsible for', 'hard worker', 'team player', 'detail-oriented', 'self-motivated', 'results-driven', 'go-getter', 'people person', or similar. Never use buzzwords, empty adjectives, or filler. \n");
        prompt.append("6. Instead, use concise, evidence-based sentences that demonstrate real impact, ownership, and results. Begin each bullet or line with a strong action verb that shows direct contribution (examples: 'Increased', 'Led', 'Designed', 'Optimized', 'Reduced', 'Launched', 'Developed', 'Generated', 'Streamlined', 'Built', 'Improved').\n");
        prompt.append("7. Ensure the CV tells a clear story of high impact and fit for the specific role. Each line must answer the question: 'How did this person drive results or solve key problems relevant to this job?'\n");
        prompt.append("8. Output ONLY a complete, ready-to-compile LaTeX CV using the provided template and structure. Do NOT include markdown, explanations, or commentary. Sections not relevant should be omitted or left empty as the template allows.\n");
        prompt.append("9. The result must be visually clean, ATS-friendly, and professional, using only the given LaTeX formatting and structure. No extra formatting or code.\n\n");

        prompt.append("FINAL OUTPUT:\n");
        prompt.append("Return ONLY the complete LaTeX CV code, ready for compilation. No markdown, no explanations, no commentary. No extra content.\n");

        return prompt.toString();
    }

}
