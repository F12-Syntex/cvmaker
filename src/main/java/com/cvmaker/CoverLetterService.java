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

/**
 * AI-powered cover letter generation service that creates targeted,
 * professional cover letters tailored to specific job applications and
 * integrated with CV content.
 */
public class CoverLetterService {

    private final OpenAIClient client;
    private ChatModel model;
    private double temperature;
    private final ExecutorService executorService;

    /**
     * Creates a new cover letter service with default settings
     */
    public CoverLetterService() {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.4; // Slightly higher for more creative writing
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Creates a new cover letter service with custom settings
     */
    public CoverLetterService(ChatModel model, double temperature) {
        this();
        this.model = model;
        this.temperature = temperature;
    }

    /**
     * Creates a new cover letter service with a custom client
     */
    public CoverLetterService(OpenAIClient client) {
        this.client = client;
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.4;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Set the model to use for cover letter generation
     */
    public CoverLetterService setModel(ChatModel model) {
        this.model = model;
        return this;
    }

    /**
     * Set the temperature for cover letter generation
     */
    public CoverLetterService setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    /**
     * Generate a complete LaTeX cover letter directly with enhanced progress
     * tracking
     */
    public String generateDirectLatexCoverLetter(String cvContent, String jobDescription,
            String companyName, String positionTitle, String applicantName, String contactInfo) {
        try {
            // Show detailed progress for this complex operation
            ProgressTracker coverLetterProgress = ProgressTracker.create("AI Cover Letter Generation", 4);

            coverLetterProgress.nextStep("Analyzing CV content and job requirements");
            coverLetterProgress.nextStep("Identifying key qualifications and achievements");

            coverLetterProgress.startStep("Generating personalized cover letter content");
            String prompt = buildCoverLetterGenerationPrompt(cvContent, jobDescription,
                    companyName, positionTitle, applicantName, contactInfo);
            String response = queryWithProgress(prompt, "Creating professional cover letter");
            coverLetterProgress.completeStep("Cover letter content generated successfully");

            coverLetterProgress.nextStep("Finalizing cover letter formatting and structure");
            String result = extractLatexFromResponse(response);

            coverLetterProgress.complete();
            return result;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate cover letter: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a cover letter that complements an existing CV
     */
    public String generateComplementaryCoverLetter(String cvLatexContent, String jobDescription,
            String companyName, String positionTitle) {
        try {
            String prompt = buildComplementaryPrompt(cvLatexContent, jobDescription,
                    companyName, positionTitle);
            String response = queryWithProgress(prompt, "Creating CV-complementary cover letter");
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate complementary cover letter: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a cover letter with specific tone and style
     */
    public String generateStyledCoverLetter(String cvContent, String jobDescription,
            String companyName, String positionTitle, String applicantName,
            String contactInfo, CoverLetterStyle style) {
        try {
            String prompt = buildStyledPrompt(cvContent, jobDescription, companyName,
                    positionTitle, applicantName, contactInfo, style);
            String response = queryWithProgress(prompt, "Creating " + style.getDescription() + " cover letter");
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate styled cover letter: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze a job description and suggest cover letter focus areas
     */
    public String analyzeCoverLetterStrategy(String jobDescription, String cvContent) {
        try {
            String prompt = buildAnalysisPrompt(jobDescription, cvContent);
            return queryWithProgress(prompt, "Analyzing cover letter strategy");
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze cover letter strategy: " + e.getMessage(), e);
        }
    }

    /**
     * Send a query to the OpenAI API with optional progress tracking
     */
    public String queryWithProgress(String prompt, String operationDescription) {
        try {
            // Show progress if description provided
            if (operationDescription != null) {
                showAIOperationProgress(operationDescription);
            }

            // Create parameters for the ChatCompletion request
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addUserMessage(prompt)
                    .model(model)
                    .temperature(temperature)
                    .build();

            // Execute the API call with timeout handling
            CompletableFuture<ChatCompletion> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return client.chat().completions().create(params);
                } catch (Exception e) {
                    throw new RuntimeException("AI API call failed: " + e.getMessage(), e);
                }
            }, executorService);

            // Wait for completion with progress updates
            ChatCompletion completion = waitForCompletionWithProgress(future, operationDescription);

            // Extract and log token usage
            if (completion.usage() != null) {
                var usage = completion.usage().get();
                System.out.printf("🔤 Token usage - Prompt: %d, Completion: %d, Total: %d\n",
                        usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
            }

            // Extract the response content
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
     * Build the optimized prompt for cover letter generation
     */
    private String buildCoverLetterGenerationPrompt(String cvContent, String jobDescription,
            String companyName, String positionTitle, String applicantName, String contactInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert cover letter writer and career strategist specializing in creating compelling, ");
        prompt.append("ATS-optimized cover letters that maximize interview callbacks. Create a strategic cover letter ");
        prompt.append("that tells a cohesive story connecting the candidate's background to the specific role.\n\n");

        // Job analysis section
        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("TARGET JOB ANALYSIS:\n")
                    .append("Analyze this job description and identify:\n")
                    .append("- Core responsibilities and required skills\n")
                    .append("- Company culture indicators and values\n")
                    .append("- Key challenges the role will address\n")
                    .append("- Opportunity for candidate impact\n\n")
                    .append("TARGET JOB DESCRIPTION:\n")
                    .append(jobDescription)
                    .append("\n\n");
        }

        prompt.append("CANDIDATE BACKGROUND:\n")
                .append(cvContent)
                .append("\n\n");

        prompt.append("APPLICATION DETAILS:\n");
        if (companyName != null) {
            prompt.append("Company: ").append(companyName).append("\n");
        }
        if (positionTitle != null) {
            prompt.append("Position: ").append(positionTitle).append("\n");
        }
        if (applicantName != null) {
            prompt.append("Applicant: ").append(applicantName).append("\n");
        }
        if (contactInfo != null) {
            prompt.append("Contact: ").append(contactInfo).append("\n");
        }
        prompt.append("\n");

        prompt.append("COVER LETTER STRATEGY:\n");
        prompt.append("1. COMPELLING OPENING: Hook the reader with immediate value proposition and role connection\n");
        prompt.append("2. EVIDENCE-BASED NARRATIVE: Use specific achievements from CV to demonstrate capability\n");
        prompt.append("3. COMPANY ALIGNMENT: Show understanding of company needs and how candidate addresses them\n");
        prompt.append("4. QUANTIFIED IMPACT: Include metrics and results that prove candidate effectiveness\n");
        prompt.append("5. FUTURE CONTRIBUTION: Articulate specific ways candidate will add value in the role\n");
        prompt.append("6. PROFESSIONAL CLOSE: Clear call to action with confidence and enthusiasm\n\n");

        prompt.append("CONTENT REQUIREMENTS:\n");
        prompt.append("• OPENING PARAGRAPH: Strong hook that immediately connects candidate to role\n");
        prompt.append("• BODY PARAGRAPHS: 2-3 focused paragraphs with specific examples and achievements\n");
        prompt.append("• COMPANY CONNECTION: Demonstrate research and understanding of company/role\n");
        prompt.append("• QUANTIFIED RESULTS: Include specific numbers, percentages, or impact metrics\n");
        prompt.append("• CLOSING PARAGRAPH: Confident call to action with next steps\n\n");

        prompt.append("WRITING GUIDELINES:\n");
        prompt.append("• Use engaging, confident tone while remaining professional\n");
        prompt.append("• Include specific examples from CV that relate to job requirements\n");
        prompt.append("• Show personality and enthusiasm for the role and company\n");
        prompt.append("• Use keywords from job description naturally throughout\n");
        prompt.append("• Maintain focus on value proposition and mutual benefit\n");
        prompt.append("• Keep to 3-4 paragraphs, approximately 250-350 words\n\n");

        prompt.append("ATS OPTIMIZATION:\n");
        prompt.append("• Use clean, professional formatting\n");
        prompt.append("• Include relevant keywords from job posting\n");
        prompt.append("• Maintain consistent font and styling\n");
        prompt.append("• Avoid complex formatting that may confuse ATS\n\n");

        prompt.append("DIFFERENTIATION FACTORS:\n");
        prompt.append("• Highlight unique combination of skills and experience\n");
        prompt.append("• Emphasize achievements that set candidate apart\n");
        prompt.append("• Connect diverse background to role advantages\n");
        prompt.append("• Show understanding of industry trends and challenges\n\n");

        prompt.append("OUTPUT FORMAT:\n");
        prompt.append("Return ONLY a complete, professional LaTeX cover letter with:\n");
        prompt.append("• Professional letterhead and formatting\n");
        prompt.append("• Proper business letter structure\n");
        prompt.append("• All necessary LaTeX packages and styling\n");
        prompt.append("• No markdown formatting or explanations\n\n");

        prompt.append("FINAL INSTRUCTION: Create a cover letter that would make a hiring manager ");
        prompt.append("immediately want to interview this candidate for the specific role described.\n\n");

        prompt.append("COMPLETE LATEX COVER LETTER:");

        return prompt.toString();
    }

    /**
     * Build prompt for generating complementary cover letter based on existing
     * CV
     */
    private String buildComplementaryPrompt(String cvLatexContent, String jobDescription,
            String companyName, String positionTitle) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a professional cover letter specialist. Create a cover letter that perfectly ");
        prompt.append("complements the provided CV, expanding on key achievements and creating a compelling ");
        prompt.append("narrative for the specific role.\n\n");

        prompt.append("EXISTING CV CONTENT:\n")
                .append(cvLatexContent)
                .append("\n\n");

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("TARGET ROLE:\n")
                    .append("Position: ").append(positionTitle != null ? positionTitle : "Target Role").append("\n")
                    .append("Company: ").append(companyName != null ? companyName : "Target Company").append("\n\n")
                    .append("JOB REQUIREMENTS:\n")
                    .append(jobDescription)
                    .append("\n\n");
        }

        prompt.append("COMPLEMENTARY STRATEGY:\n");
        prompt.append("• Extract key achievements from CV and expand with context and impact\n");
        prompt.append("• Highlight connections between CV content and job requirements\n");
        prompt.append("• Add personality and motivation not captured in CV format\n");
        prompt.append("• Create narrative flow that enhances CV presentation\n");
        prompt.append("• Demonstrate enthusiasm and cultural fit\n\n");

        prompt.append("Return ONLY a LaTeX cover letter that works seamlessly with the provided CV.\n\n");
        prompt.append("COMPLEMENTARY LATEX COVER LETTER:");

        return prompt.toString();
    }

    /**
     * Build prompt for styled cover letter generation
     */
    private String buildStyledPrompt(String cvContent, String jobDescription, String companyName,
            String positionTitle, String applicantName, String contactInfo, CoverLetterStyle style) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert cover letter writer specializing in different communication styles. ");
        prompt.append("Create a ").append(style.getDescription()).append(" cover letter that maintains ");
        prompt.append("professionalism while reflecting the requested tone and approach.\n\n");

        // Add style-specific guidelines
        prompt.append("STYLE REQUIREMENTS (").append(style.name()).append("):\n");
        switch (style) {
            case TRADITIONAL:
                prompt.append("• Formal, conservative tone and structure\n");
                prompt.append("• Classic business letter format\n");
                prompt.append("• Emphasis on credentials and qualifications\n");
                prompt.append("• Respectful, deferential language\n");
                break;
            case MODERN:
                prompt.append("• Contemporary, confident tone\n");
                prompt.append("• Clear, concise communication\n");
                prompt.append("• Focus on value proposition and results\n");
                prompt.append("• Engaging but professional approach\n");
                break;
            case CREATIVE:
                prompt.append("• Innovative approach while maintaining professionalism\n");
                prompt.append("• Unique opening that captures attention\n");
                prompt.append("• Storytelling elements and personality\n");
                prompt.append("• Creative formatting within professional bounds\n");
                break;
            case TECHNICAL:
                prompt.append("• Technical accuracy and precision\n");
                prompt.append("• Emphasis on specific skills and technologies\n");
                prompt.append("• Quantified achievements and metrics\n");
                prompt.append("• Clear demonstration of technical competency\n");
                break;
            case EXECUTIVE:
                prompt.append("• Senior-level, strategic tone\n");
                prompt.append("• Focus on leadership and business impact\n");
                prompt.append("• High-level achievements and vision\n");
                prompt.append("• Confident, authoritative communication\n");
                break;
        }
        prompt.append("\n");

        // Add common content sections
        prompt.append("CANDIDATE INFORMATION:\n")
                .append(cvContent)
                .append("\n\n");

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("TARGET POSITION:\n")
                    .append("Role: ").append(positionTitle != null ? positionTitle : "Target Position").append("\n")
                    .append("Company: ").append(companyName != null ? companyName : "Target Company").append("\n\n")
                    .append("JOB DESCRIPTION:\n")
                    .append(jobDescription)
                    .append("\n\n");
        }

        prompt.append("Return ONLY a complete LaTeX cover letter in the specified style.\n\n");
        prompt.append("STYLED LATEX COVER LETTER:");

        return prompt.toString();
    }

    /**
     * Build prompt for cover letter strategy analysis
     */
    private String buildAnalysisPrompt(String jobDescription, String cvContent) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a career strategy expert. Analyze the job description and candidate background ");
        prompt.append("to provide strategic recommendations for an effective cover letter.\n\n");

        prompt.append("JOB DESCRIPTION:\n")
                .append(jobDescription)
                .append("\n\n");

        prompt.append("CANDIDATE BACKGROUND:\n")
                .append(cvContent)
                .append("\n\n");

        prompt.append("ANALYSIS FRAMEWORK:\n");
        prompt.append("1. KEY REQUIREMENTS ANALYSIS:\n");
        prompt.append("   • Identify must-have vs. nice-to-have qualifications\n");
        prompt.append("   • Highlight critical skills and experience areas\n");
        prompt.append("   • Note any gaps or stretch requirements\n\n");

        prompt.append("2. CANDIDATE STRENGTH MAPPING:\n");
        prompt.append("   • Match candidate experience to job requirements\n");
        prompt.append("   • Identify standout qualifications and achievements\n");
        prompt.append("   • Highlight unique value propositions\n\n");

        prompt.append("3. COVER LETTER STRATEGY:\n");
        prompt.append("   • Recommend key points to emphasize\n");
        prompt.append("   • Suggest specific examples to include\n");
        prompt.append("   • Identify potential concerns to address\n");
        prompt.append("   • Recommend optimal tone and approach\n\n");

        prompt.append("4. COMPANY RESEARCH SUGGESTIONS:\n");
        prompt.append("   • Key areas to research about the company\n");
        prompt.append("   • Potential connection points to mention\n");
        prompt.append("   • Industry trends to reference\n\n");

        prompt.append("Provide a comprehensive analysis with specific, actionable recommendations.\n\n");
        prompt.append("COVER LETTER STRATEGY ANALYSIS:");

        return prompt.toString();
    }

    /**
     * Show progress for AI operations with spinner and status updates
     */
    private void showAIOperationProgress(String operation) {
        System.out.printf("🤖 AI Operation: %s\n", operation);
    }

    /**
     * Wait for AI completion with progress updates
     */
    private ChatCompletion waitForCompletionWithProgress(CompletableFuture<ChatCompletion> future, String operation) {
        try {
            // Show progress while waiting
            long startTime = System.currentTimeMillis();

            while (!future.isDone()) {
                Thread.sleep(1000);
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed > 5000) { // After 5 seconds, show time elapsed
                    System.out.printf("\r   ⏳ AI processing... (%ds elapsed)   ", elapsed / 1000);
                }

                if (elapsed > 30000) { // After 30 seconds, show extended message
                    System.out.printf("\r   🔄 Complex AI operation in progress... (%ds elapsed)   ", elapsed / 1000);
                }
            }

            System.out.print("\r   ✅ AI operation completed!             \n");
            return future.get(60, TimeUnit.SECONDS); // 60 second timeout

        } catch (Exception e) {
            System.out.print("\r   ❌ AI operation failed!               \n");
            throw new RuntimeException("AI operation timed out or failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract LaTeX from the AI response, removing any markdown or extra text
     */
    private String extractLatexFromResponse(String response) {
        String cleaned = response.trim();

        // Remove markdown code blocks if present
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

    /**
     * Shutdown the executor service
     */
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

    /**
     * Cover letter style enumeration
     */
    public enum CoverLetterStyle {
        TRADITIONAL("traditional and formal"),
        MODERN("modern and professional"),
        CREATIVE("creative and engaging"),
        TECHNICAL("technical and precise"),
        EXECUTIVE("executive and strategic");

        private final String description;

        CoverLetterStyle(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
