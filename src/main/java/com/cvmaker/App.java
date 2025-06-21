package com.cvmaker;

import java.nio.file.Paths;

import com.cvmaker.configuration.ConfigManager;

public class App {

    public static void main(String[] args) {
        try {
            // Load configuration
            System.out.println("Loading configuration...");
            ConfigManager config = new ConfigManager();
            System.out.println(config);

            // Initialize components
            System.out.println("Initializing AI and template systems...");
            TemplateLoader loader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
            AiService aiService = new AiService(config.getAiModel(), config.getAiTemperature());
            CVGenerator generator = new CVGenerator(loader, aiService, config);

            // Generate CV and cover letter using AI
            System.out.println("Starting CV generation...");
            generateWithAI(config, generator);

            aiService.shutdown();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
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