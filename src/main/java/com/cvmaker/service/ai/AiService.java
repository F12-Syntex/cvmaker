package com.cvmaker.service.ai;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AiService {

    /**
     * How often the wait for an answer wakes up to print progress.
     */
    private static final long PROGRESS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    /**
     * Waits between attempts. Tests replace it so that they do not sleep.
     */
    @FunctionalInterface
    interface Sleeper {

        void sleep(long millis) throws InterruptedException;
    }

    private final LLMClient llmClient;
    private final ExecutorService executorService;
    private final AiOptions options;
    private final Sleeper sleeper;
    private LLMModel model;
    private double temperature;

    public AiService(LLMModel model) {
        this(model, 0.3);
    }

    public AiService(LLMModel model, double temperature) {
        this(model, temperature, AiOptions.defaults());
    }

    /**
     * @param options timeout, retries and backoff for every query; see
     * {@link AiOptions#of(int, int, int)} for the values from
     * {@code config.properties}
     */
    public AiService(LLMModel model, double temperature, AiOptions options) {
        this(LLMClientFactory.createClient(Objects.requireNonNull(model, "model")), model, temperature, options);

        System.out.println("Using model: " + model.getModelName() + " (" + model.getEndpoint() + ")");
    }

    /**
     * Uses the default model of the given provider, see
     * {@link LLMModel#defaultFor(LLMProvider)}.
     */
    public AiService(LLMProvider provider) {
        this(LLMModel.defaultFor(Objects.requireNonNull(provider, "provider")));
    }

    /**
     * Uses the given client instead of creating one. The service owns the
     * client from here on: {@link #shutdown()} shuts it down too.
     */
    public AiService(LLMClient llmClient, LLMModel model, double temperature, AiOptions options) {
        this(llmClient, model, temperature, options, Thread::sleep);
    }

    AiService(LLMClient llmClient, LLMModel model, double temperature, AiOptions options, Sleeper sleeper) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.model = Objects.requireNonNull(model, "model");
        this.temperature = temperature;
        this.options = options != null ? options : AiOptions.defaults();
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        // One thread per call in flight: an attempt that timed out may still
        // hold its thread until the HTTP client gives up, and the retry must
        // not queue behind it.
        this.executorService = Executors.newCachedThreadPool();
    }

    public AiService setModel(LLMModel model) {
        this.model = model;
        return this;
    }

    public AiService setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public LLMModel getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public AiOptions getOptions() {
        return options;
    }

    public String query(String prompt) {
        return queryWithProgress(prompt);
    }

    public String queryWithProgress(String prompt) {
        try {
            LLMResponse response = completeWithRetries(buildRequest(prompt));

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
            String prompt = buildLatexPrompt(ai_prompt, unstructuredText, referenceTemplate, jobDescription,
                    "LATEX TEMPLATE");
            return requireLatex(extractLatexFromResponse(queryWithProgress(prompt)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX CV: " + e.getMessage(), e);
        }
    }

    public String generateDirectLatexCoverLetter(String unstructuredText, String referenceTemplate, String jobDescription, String coverLetterPrompt) {
        try {
            String prompt = buildLatexPrompt(coverLetterPrompt, unstructuredText, referenceTemplate, jobDescription,
                    "LATEX COVER LETTER TEMPLATE");
            return requireLatex(extractLatexFromResponse(queryWithProgress(prompt)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX cover letter: " + e.getMessage(), e);
        }
    }

    /**
     * The request for one query: current model and temperature, plus the token
     * limit from the options when one is set.
     */
    LLMRequest buildRequest(String prompt) {
        LLMRequest.Builder request = LLMRequest.builder()
                .prompt(prompt)
                .model(model.getModelName())
                .temperature(temperature);
        if (options.getMaxTokens() != null) {
            request.maxTokens(options.getMaxTokens());
        }
        return request.build();
    }

    /**
     * Runs the request, trying again after a failure up to
     * {@link AiOptions#getMaxRetries()} times with a growing wait in between.
     * A failure that cannot succeed on a second try (see
     * {@link LLMException#isRetryable(Throwable)}) ends the loop at once.
     */
    private LLMResponse completeWithRetries(LLMRequest request) {
        int maxAttempts = options.getMaxRetries() + 1;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return completeOnce(request);
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt == maxAttempts || !LLMException.isRetryable(e)) {
                    break;
                }
                long waitMillis = options.backoffMillis(attempt);
                System.out.printf("   ⚠️ AI call failed (attempt %d of %d): %s. Retrying in %d ms%n",
                        attempt, maxAttempts, e.getMessage(), waitMillis);
                try {
                    sleeper.sleep(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw lastFailure;
    }

    /**
     * Runs the request once on the executor and waits for it, printing
     * progress, for at most {@link AiOptions#getTimeout()}.
     */
    private LLMResponse completeOnce(LLMRequest request) {
        Future<LLMResponse> future = executorService.submit(() -> llmClient.complete(request));
        long startNanos = System.nanoTime();
        long timeoutNanos = options.getTimeout().toNanos();

        try {
            while (true) {
                long elapsedNanos = System.nanoTime() - startNanos;
                long waitNanos = PROGRESS_INTERVAL_NANOS;
                if (options.hasTimeout()) {
                    long remainingNanos = timeoutNanos - elapsedNanos;
                    if (remainingNanos <= 0) {
                        future.cancel(true);
                        System.out.print("\r   ❌ AI operation timed out!            \n");
                        throw new LLMException("AI operation timed out after "
                                + formatSeconds(timeoutNanos) + " seconds", true);
                    }
                    waitNanos = Math.min(waitNanos, remainingNanos);
                }

                try {
                    LLMResponse response = future.get(waitNanos, TimeUnit.NANOSECONDS);
                    System.out.print("\r   ✅ AI operation completed!             \n");
                    return response;
                } catch (TimeoutException stillRunning) {
                    printProgress(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos));
                }
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.out.print("\r   ❌ AI operation failed!               \n");
            throw new RuntimeException("AI operation failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new LLMException("AI operation was interrupted", e, false);
        }
    }

    private static void printProgress(long elapsedSeconds) {
        if (elapsedSeconds > 30) {
            System.out.printf("\r   ⏳ Complex AI operation in progress... (%ds elapsed)   ", elapsedSeconds);
        } else if (elapsedSeconds > 5) {
            System.out.printf("\r   🤖 AI processing... (%ds elapsed)   ", elapsedSeconds);
        }
    }

    private static String formatSeconds(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        return millis % 1000 == 0 ? String.valueOf(millis / 1000) : String.valueOf(millis / 1000.0);
    }

    private static String requireLatex(String latex) {
        if (latex.isEmpty()) {
            throw new IllegalStateException("the AI returned an empty response");
        }
        return latex;
    }

    /**
     * Pulls the LaTeX document out of a model answer: drops a surrounding
     * Markdown code fence, any text before {@code \documentclass} and any text
     * after the last {@code \end{document}}.
     *
     * @return the document, or an empty string for a null or blank answer
     */
    static String extractLatexFromResponse(String response) {
        if (response == null) {
            return "";
        }
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

        int docStart = cleaned.indexOf("\\documentclass");
        if (docStart > 0) {
            cleaned = cleaned.substring(docStart);
        }

        String documentEnd = "\\end{document}";
        int docEnd = cleaned.lastIndexOf(documentEnd);
        if (docStart >= 0 && docEnd >= 0) {
            cleaned = cleaned.substring(0, docEnd + documentEnd.length());
        }

        return cleaned.trim();
    }

    /**
     * Builds the prompt for a CV or a cover letter: the instructions, then the
     * labelled sections. Null or blank parts are left out, and a blank line
     * separates the instructions from the first section.
     *
     * @param templateLabel heading for the template section, without the colon
     */
    static String buildLatexPrompt(String instructions, String userData, String latexTemplate,
            String jobDescription, String templateLabel) {
        StringBuilder prompt = new StringBuilder();

        if (!isBlank(instructions)) {
            prompt.append(instructions.stripTrailing()).append("\n\n");
        }

        if (!isBlank(jobDescription)) {
            prompt.append("JOB DESCRIPTION:\n");
            prompt.append(jobDescription).append("\n\n");
        }

        prompt.append("CANDIDATE INFORMATION:\n");
        prompt.append(userData == null ? "" : userData).append("\n\n");

        if (!isBlank(latexTemplate)) {
            prompt.append(templateLabel).append(":\n");
            prompt.append(latexTemplate).append("\n\n");
        }

        return prompt.toString();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
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
}
