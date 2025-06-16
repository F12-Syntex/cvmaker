package com.cvmaker;

import java.nio.file.Paths;

public class App {

    public static void main(String[] args) {
        System.out.println("==== AI-Powered Modular CV Generator ====");

        try {
            // Load configuration
            ConfigManager config = new ConfigManager();
            config.printConfiguration();

            // Initialize components
            TemplateLoader loader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
            AiService aiService = new AiService(config.getAiModel(), config.getAiTemperature());
            CVGenerator generator = new CVGenerator(loader, aiService);

            ConfigManager.GenerationMode mode = config.getGenerationMode();

            if (mode == ConfigManager.GenerationMode.TRADITIONAL) {
                generateTraditional(config, generator);
            } else {
                generateWithAI(config, generator);
            }

        } catch (Exception e) {
            System.err.println("Failed to generate CV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void generateTraditional(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("\n=== Traditional Mode ===");

        generator.generateCV(
                config.getUserDataFile(),
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName()
        );

        System.out.println("Done! Output PDF: " + config.getOutputDirectory() + "/" + config.getOutputPdfName());
    }

    private static void generateWithAI(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("\n=== AI-Powered Mode ===");

        generator.generateCVFromText(
                config.getUserDataFile(),
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName(),
                config.getJobDescriptionFile()
        );

        System.out.println("Done! Output PDF: " + config.getOutputDirectory() + "/" + config.getOutputPdfName());
    }
}
