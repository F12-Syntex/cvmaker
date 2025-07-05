package com.cvmaker;

import java.nio.file.Paths;

import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;

public class App {

    public static void main(String[] args) {
        try {
            if (args.length >= 1) {
                // Job URL mode
                String jobUrl = args[0];
                String userDataFile = args.length > 1 ? args[1] : "userdata.txt";
                String cvPromptFile = args.length > 2 ? args[2] : "cv_prompt.txt";
                String coverLetterPromptFile = args.length > 3 ? args[3] : "cover_letter_prompt.txt";

                generateFromJobUrl(jobUrl, userDataFile, cvPromptFile, coverLetterPromptFile);
            } else {
                // Traditional config file mode
                generateFromConfig();
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void generateFromJobUrl(String jobUrl, String userDataFile, String cvPromptFile, String coverLetterPromptFile) throws Exception {
        System.out.println("=== CV Generator - Job URL Mode ===");
        System.out.println("Job URL: " + jobUrl);
        System.out.println("User Data: " + userDataFile);
        System.out.println("CV Prompt: " + cvPromptFile);
        System.out.println("Cover Letter Prompt: " + coverLetterPromptFile);
        System.out.println();

        // Initialize components
        System.out.println("Initializing AI and template systems...");
        TemplateLoader loader = new TemplateLoader(Paths.get("templates"));
        AiService aiService = new AiService(LLMModel.GPT_4_1_MINI);
        CVGenerator generator = new CVGenerator(loader, aiService);

        // Generate from job URL
        long startTime = System.currentTimeMillis();
        generator.generateFromJobUrl(jobUrl, userDataFile, cvPromptFile, coverLetterPromptFile);
        long endTime = System.currentTimeMillis();

        System.out.println();
        System.out.println("=== Generation Complete ===");
        System.out.println("Total time: " + formatDuration(endTime - startTime));

        aiService.shutdown();
    }

    private static void generateFromConfig() throws Exception {
        System.out.println("=== CV Generator - Config File Mode ===");

        // Load configuration
        System.out.println("Loading configuration...");
        ConfigManager config = new ConfigManager();

        // Initialize components
        System.out.println("Initializing AI and template systems...");
        TemplateLoader loader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
        AiService aiService = new AiService(LLMModel.GPT_4_1_MINI);
        CVGenerator generator = new CVGenerator(loader, aiService, config);

        // Check if job URL is provided in config
        if (config.hasJobUrl()) {
            System.out.println("Job URL found in configuration, switching to URL mode...");
            generator.generateFromJobUrl(config.getJobUrl(), config.getUserDataFile(),
                    config.getCvPromptFile(), config.getCoverLetterPromptFile());
        } else {
            // Generate CV and cover letter using traditional method
            System.out.println("Starting CV generation...");
            generateWithAI(config, generator);
        }

        aiService.shutdown();
    }

    public static void generateFromConfigWithDescription(String jobDescription) throws Exception {
        System.out.println("=== CV Generator - Config File Mode ===");

        // Load configuration
        System.out.println("Loading configuration...");
        ConfigManager config = new ConfigManager();
        config.setJobDescriptionContent(jobDescription);

        // Initialize components
        System.out.println("Initializing AI and template systems...");
        TemplateLoader loader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
        AiService aiService = new AiService(LLMModel.GPT_4_1_MINI);
        CVGenerator generator = new CVGenerator(loader, aiService, config);

        // Check if job URL is provided in config
        if (config.hasJobUrl()) {
            System.out.println("Job URL found in configuration, switching to URL mode...");
            generator.generateFromJobUrl(config.getJobUrl(), config.getUserDataFile(),
                    config.getCvPromptFile(), config.getCoverLetterPromptFile());
        } else {
            // Generate CV and cover letter using traditional method
            System.out.println("Starting CV generation...");
            generateWithAI(config, generator);
        }

        aiService.shutdown();
    }

    private static void generateWithAI(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("Generating documents with AI...");
        long startTime = System.currentTimeMillis();

        // Generate CV
        generator.generateCVFromText(
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName()
        );

        // Generate cover letter if enabled
        if (config.isGenerateCoverLetter()) {
            System.out.println("Generating cover letter...");
            generator.generateCoverLetterFromText(
                    config.getTemplateName(),
                    config.getOutputDirectory(),
                    config.getCoverLetterPdfName()
            );
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Document generation completed in " + formatDuration(endTime - startTime));
    }

    /**
     * Format duration in human-readable format
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        } else if (milliseconds < 60000) {
            return String.format("%.1fs", milliseconds / 1000.0);
        } else {
            long minutes = milliseconds / 60000;
            long seconds = (milliseconds % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
