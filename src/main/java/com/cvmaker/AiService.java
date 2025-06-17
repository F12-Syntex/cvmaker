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

    /**
     * Creates a new OpenAI client with default settings
     */
    public AiService() {
        this.client = OpenAIOkHttpClient.fromEnv();
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.3;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Creates a new OpenAI client with custom settings
     */
    public AiService(ChatModel model, double temperature) {
        this();
        this.model = model;
        this.temperature = temperature;
    }

    /**
     * Creates a new OpenAI client with a custom client
     */
    public AiService(OpenAIClient client) {
        this.client = client;
        this.model = ChatModel.GPT_4_1_MINI;
        this.temperature = 0.3;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Set the model to use for queries
     */
    public AiService setModel(ChatModel model) {
        this.model = model;
        return this;
    }

    /**
     * Set the temperature for queries
     */
    public AiService setTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    /**
     * Send a query to the OpenAI API with progress tracking
     */
    public String query(String prompt) {
        return queryWithProgress(prompt, null);
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
                System.out.printf("🔢 Token usage - Prompt: %d, Completion: %d, Total: %d\n",
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
     * Generate a complete LaTeX CV directly with enhanced progress tracking
     */
    public String generateDirectLatexCV(String unstructuredText, String referenceTemplate, String jobDescription) {
        try {
            // Show detailed progress for this complex operation
            ProgressTracker cvProgress = ProgressTracker.create("AI LaTeX CV Generation", 4);

            cvProgress.nextStep("Analyzing CV content and requirements");
            Thread.sleep(500); // Small delay to show progress

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

    /**
     * Generate a complete LaTeX template for template creation
     */
    public String generateLatexTemplate(String templateRequirements, String templateStyle) {
        try {
            String prompt = buildTemplateGenerationPrompt(templateRequirements, templateStyle);
            String response = queryWithProgress(prompt, "Generating LaTeX template structure");
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate LaTeX template: " + e.getMessage(), e);
        }
    }

    /**
     * Enhance an existing LaTeX template
     */
    public String enhanceLatexTemplate(String existingTemplate, String enhancementRequests) {
        try {
            String prompt = buildTemplateEnhancementPrompt(existingTemplate, enhancementRequests);
            String response = queryWithProgress(prompt, "Enhancing LaTeX template with AI improvements");
            return extractLatexFromResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enhance LaTeX template: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze a LaTeX template
     */
    public String analyzeLatexTemplate(String latexTemplate, String analysisType) {
        try {
            String prompt = buildTemplateAnalysisPrompt(latexTemplate, analysisType);
            return queryWithProgress(prompt, "Analyzing LaTeX template for improvements");
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze LaTeX template: " + e.getMessage(), e);
        }
    }

    /**
     * Show progress for AI operations with spinner and status updates
     */
    private void showAIOperationProgress(String operation) {
        System.out.printf("🤖 AI Operation: %s\n", operation);

        // Create a simple progress indicator
        CompletableFuture.runAsync(() -> {
            String[] dots = {"", ".", "..", "..."};
            int i = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    System.out.printf("\r   ⏳ Processing%s   ", dots[i % dots.length]);
                    Thread.sleep(500);
                    i++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
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
                    System.out.printf("\r   🧠 Complex AI operation in progress... (%ds elapsed)   ", elapsed / 1000);
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
     * Build the optimized prompt for direct LaTeX generation
     */
    private String buildDirectLatexGenerationPrompt(String unstructuredText, String referenceTemplate, String jobDescription) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert ATS-optimized CV writer and LaTeX developer. ");
        prompt.append("Create a strategic, targeted CV that maximizes relevance while maintaining complete honesty. ");
        prompt.append("Focus on impact, quantifiable achievements, and exact keyword matches from the job requirements.\n\n");

        // Job analysis section
        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("JOB REQUIREMENTS ANALYSIS:\n")
                    .append("First, analyze this job description and extract:\n")
                    .append("- Required technical skills and tools\n")
                    .append("- Preferred qualifications and experience levels\n")
                    .append("- Key responsibilities and competencies\n")
                    .append("- Industry-specific terminology and keywords\n\n")
                    .append("TARGET JOB DESCRIPTION:\n")
                    .append(jobDescription)
                    .append("\n\n");
        }

        prompt.append("CANDIDATE INFORMATION:\n")
                .append(unstructuredText)
                .append("\n\n");

        if (referenceTemplate != null) {
            prompt.append("REFERENCE TEMPLATE (use as structural inspiration):\n")
                    .append(referenceTemplate)
                    .append("\n\n");
        }

        prompt.append("CV OPTIMIZATION STRATEGY:\n");
        prompt.append("1. RELEVANCE FILTERING: Only include experiences, skills, and achievements that directly relate to the target role\n");
        prompt.append("2. KEYWORD OPTIMIZATION: Use exact terminology from the job description where truthfully applicable\n");
        prompt.append("3. QUANTIFIED IMPACT: Present achievements with specific metrics, percentages, or scale indicators\n");
        prompt.append("4. STRATEGIC ORDERING: Lead with most relevant qualifications and experiences\n");
        prompt.append("5. CONCISE PRESENTATION: Limit to 1-2 pages, prioritizing high-impact information\n\n");
        prompt.append("6. RELAVANCE: Infer based on the users data so that additional things not explicitely mentioned can be added to the CV, without lying, simply infering based on the user data\n\n");

        prompt.append("CONTENT REQUIREMENTS:\n");
        prompt.append("• PROFESSIONAL SUMMARY: 3-4 lines highlighting exact role fit and key value propositions\n");
        prompt.append("• CORE COMPETENCIES: Bullet-point list of relevant technical and soft skills using job keywords\n");
        prompt.append("• EXPERIENCE: Focus on achievements over duties, quantify results, emphasize relevant projects\n");
        prompt.append("• EDUCATION: Include relevant coursework, certifications, or academic projects if applicable\n");
        prompt.append("• ADDITIONAL SECTIONS: Only include if directly relevant (certifications, languages, publications)\n\n");

        prompt.append("WRITING GUIDELINES:\n");
        prompt.append("• Use strong action verbs (achieved, optimized, implemented, led, developed)\n");
        prompt.append("• Include specific technologies, methodologies, and tools mentioned in job requirements\n");
        prompt.append("• Quantify achievements with numbers, percentages, timeframes, or scale\n");
        prompt.append("• Match the industry tone and terminology from the job description\n");
        prompt.append("• Eliminate irrelevant information, even if impressive\n");
        prompt.append("• Use consistent formatting and professional language\n\n");

        prompt.append("ATS OPTIMIZATION:\n");
        prompt.append("• Use standard section headers and clean formatting\n");
        prompt.append("• Include exact keyword phrases from job requirements\n");
        prompt.append("• Avoid graphics, tables, or complex formatting that may confuse ATS\n");
        prompt.append("• Use standard fonts and clear hierarchy\n\n");

        prompt.append("HONESTY REQUIREMENT:\n");
        prompt.append("• Never fabricate experiences, skills, or achievements\n");
        prompt.append("• Emphasize and strategically present truthful information\n");
        prompt.append("• Reframe existing experiences to highlight relevant aspects\n");
        prompt.append("• Use confident but accurate language\n\n");

        prompt.append("OUTPUT FORMAT:\n");
        prompt.append("Return ONLY a complete, compilable LaTeX document with:\n");
        prompt.append("• All necessary packages and styling\n");
        prompt.append("• Professional, ATS-friendly formatting\n");
        prompt.append("• Strategic content organization\n");
        prompt.append("• No markdown formatting or explanations\n\n");

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            prompt.append("FINAL INSTRUCTION: Create a CV that would make an ATS system and hiring manager immediately recognize this candidate as a strong match for the specific role described above.\n\n");
        }

        prompt.append("COMPLETE LATEX CV:");

        return prompt.toString();
    }

    /**
     * Build the optimized prompt for LaTeX template generation
     */
    private String buildTemplateGenerationPrompt(String requirements, String style) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert LaTeX developer and modern CV designer specializing in ATS-optimized, recruiter-friendly templates. ");
        prompt.append("Create a strategic CV template that maximizes readability, keyword optimization, and professional impact.\n\n");

        prompt.append("DESIGN REQUIREMENTS:\n")
                .append(requirements)
                .append("\n\n");

        prompt.append("STYLE PREFERENCE: ").append(style != null ? style : "modern professional").append("\n\n");

        prompt.append("TEMPLATE SPECIFICATIONS:\n");
        prompt.append("• STRUCTURE: Clear hierarchy with strategic white space and logical flow\n");
        prompt.append("• ATS COMPATIBILITY: Clean formatting that parses correctly through applicant tracking systems\n");
        prompt.append("• VISUAL IMPACT: Professional appearance that stands out while remaining conservative\n");
        prompt.append("• FLEXIBILITY: Easily customizable sections for different roles and industries\n");
        prompt.append("• EFFICIENCY: Optimized for 1-2 page length with maximum information density\n\n");

        prompt.append("SECTION LAYOUT PRIORITY:\n");
        prompt.append("1. Contact Information + Professional Summary (attention-grabbing opening)\n");
        prompt.append("2. Core Competencies/Skills (keyword-rich section for ATS)\n");
        prompt.append("3. Professional Experience (achievement-focused format)\n");
        prompt.append("4. Education (relevant coursework and certifications)\n");
        prompt.append("5. Additional Sections (projects, certifications, languages - as needed)\n\n");

        prompt.append("FORMATTING REQUIREMENTS:\n");
        prompt.append("• Use standard LaTeX document class with professional margins\n");
        prompt.append("• Include packages for: font selection, spacing control, list formatting, and color accents\n");
        prompt.append("• Create consistent typography hierarchy with clear section breaks\n");
        prompt.append("• Implement subtle design elements that enhance readability\n");
        prompt.append("• Ensure compatibility with standard LaTeX distributions\n\n");

        prompt.append("SAMPLE CONTENT GUIDELINES:\n");
        prompt.append("• Include placeholder text that demonstrates best practices\n");
        prompt.append("• Show examples of quantified achievements and impact statements\n");
        prompt.append("• Use varied action verbs and professional terminology\n");
        prompt.append("• Demonstrate proper keyword integration techniques\n\n");

        prompt.append("TECHNICAL REQUIREMENTS:\n");
        prompt.append("• Compile successfully with pdflatex or xelatex\n");
        prompt.append("• Include comprehensive commenting for easy customization\n");
        prompt.append("• Use semantic structure for maintainability\n");
        prompt.append("• Optimize for both digital viewing and printing\n\n");

        prompt.append("Return ONLY the complete LaTeX template code with no additional explanations.\n\n");
        prompt.append("PROFESSIONAL LATEX TEMPLATE:");

        return prompt.toString();
    }

    /**
     * Build the optimized prompt for template enhancement
     */
    private String buildTemplateEnhancementPrompt(String existingTemplate, String enhancements) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a LaTeX expert and CV optimization specialist. Enhance this template to create more effective, ");
        prompt.append("ATS-friendly, and strategically designed CVs that maximize candidate success rates.\n\n");

        prompt.append("CURRENT TEMPLATE:\n")
                .append(existingTemplate)
                .append("\n\n");

        prompt.append("ENHANCEMENT OBJECTIVES:\n")
                .append(enhancements)
                .append("\n\n");

        prompt.append("OPTIMIZATION FOCUS AREAS:\n");
        prompt.append("• ATS COMPATIBILITY: Ensure parsing accuracy and keyword recognition\n");
        prompt.append("• VISUAL HIERARCHY: Improve readability and information scanning\n");
        prompt.append("• CONTENT DENSITY: Maximize relevant information per page\n");
        prompt.append("• STRATEGIC EMPHASIS: Highlight key qualifications and achievements\n");
        prompt.append("• PROFESSIONAL IMPACT: Enhance overall presentation and credibility\n\n");

        prompt.append("ENHANCEMENT GUIDELINES:\n");
        prompt.append("• Preserve template functionality while improving effectiveness\n");
        prompt.append("• Add strategic design elements that support content goals\n");
        prompt.append("• Improve section organization for better information flow\n");
        prompt.append("• Enhance typography and spacing for professional appearance\n");
        prompt.append("• Include flexibility for different industry requirements\n\n");

        prompt.append("TECHNICAL REQUIREMENTS:\n");
        prompt.append("• Maintain compilation compatibility\n");
        prompt.append("• Add clear documentation for new features\n");
        prompt.append("• Ensure cross-platform LaTeX distribution support\n");
        prompt.append("• Test for ATS parsing reliability\n\n");

        prompt.append("Return ONLY the enhanced LaTeX template code with improved functionality.\n\n");
        prompt.append("ENHANCED LATEX TEMPLATE:");

        return prompt.toString();
    }

    /**
     * Build the optimized prompt for template analysis
     */
    private String buildTemplateAnalysisPrompt(String template, String analysisType) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a CV optimization expert and LaTeX specialist. Conduct a comprehensive analysis of this ");
        prompt.append("template focusing on effectiveness for modern job applications and ATS compatibility.\n\n");

        prompt.append("TEMPLATE TO ANALYZE:\n")
                .append(template)
                .append("\n\n");

        prompt.append("ANALYSIS FOCUS: ").append(analysisType).append("\n\n");

        prompt.append("EVALUATION CRITERIA:\n\n");

        prompt.append("1. ATS OPTIMIZATION ASSESSMENT:\n");
        prompt.append("   • Parsing compatibility and keyword recognition\n");
        prompt.append("   • Section header standardization\n");
        prompt.append("   • Font and formatting choices\n");
        prompt.append("   • Table and graphic usage impact\n\n");

        prompt.append("2. STRATEGIC CONTENT ORGANIZATION:\n");
        prompt.append("   • Information hierarchy and flow\n");
        prompt.append("   • Section prioritization and placement\n");
        prompt.append("   • Space utilization efficiency\n");
        prompt.append("   • Achievement emphasis techniques\n\n");

        prompt.append("3. PROFESSIONAL PRESENTATION:\n");
        prompt.append("   • Visual appeal and readability\n");
        prompt.append("   • Typography and spacing consistency\n");
        prompt.append("   • Industry appropriateness\n");
        prompt.append("   • Print and digital compatibility\n\n");

        prompt.append("4. TECHNICAL IMPLEMENTATION:\n");
        prompt.append("   • LaTeX code quality and best practices\n");
        prompt.append("   • Package usage and dependencies\n");
        prompt.append("   • Compilation reliability\n");
        prompt.append("   • Customization flexibility\n\n");

        prompt.append("5. EFFECTIVENESS METRICS:\n");
        prompt.append("   • Recruiter scanning efficiency\n");
        prompt.append("   • Keyword integration opportunities\n");
        prompt.append("   • Quantified achievement presentation\n");
        prompt.append("   • Role-specific customization potential\n\n");

        prompt.append("ANALYSIS FORMAT:\n");
        prompt.append("• Provide specific, actionable recommendations\n");
        prompt.append("• Include code examples for suggested improvements\n");
        prompt.append("• Rate effectiveness on key metrics (1-10 scale)\n");
        prompt.append("• Prioritize recommendations by impact level\n");
        prompt.append("• Consider modern recruiting and ATS trends\n\n");

        prompt.append("DETAILED ANALYSIS REPORT:");

        return prompt.toString();
    }

}
