package com.cvmaker;

import java.nio.file.Paths;

import com.cvmaker.configuration.ConfigManager;

public class App {

    public static void main(String[] args) {
        System.out.println("Starting CV Maker Application");

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

            // Generate CV using AI
            System.out.println("Starting CV generation...");
            generateWithAI(config, generator);

            // Generate Cover Letter using AI
            System.out.println("Starting cover letter generation...");
            generateCoverLetterWithAI(config, generator);

            System.out.println("Generation completed successfully!");

            // Cleanup
            aiService.shutdown();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void generateWithAI(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("Generating CV with AI...");
        long startTime = System.currentTimeMillis();

        generator.generateCVFromText(
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName()
        );

        long endTime = System.currentTimeMillis();
        System.out.println("CV generation completed in " + formatDuration(endTime - startTime));
    }

    private static void generateCoverLetterWithAI(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("Generating cover letter with AI...");
        long startTime = System.currentTimeMillis();

        String companyName = System.getenv().getOrDefault("COMPANY_NAME", "Example Company");
        String positionTitle = System.getenv().getOrDefault("POSITION_TITLE", "Software Engineer");
        String applicantName = System.getenv().getOrDefault("APPLICANT_NAME", "John Doe");
        String contactInfo = System.getenv().getOrDefault("CONTACT_INFO", "john.doe@email.com | (123) 456-7890");

        generator.generateCoverLetter(
                config.getUserDataFile(),
                config.getJobDescriptionFile(),
                companyName,
                positionTitle,
                applicantName,
                contactInfo
        );

        long endTime = System.currentTimeMillis();
        System.out.println("Cover letter generation completed in " + formatDuration(endTime - startTime));
    }

    private static void showApplicationHeader() {
        System.out.println("┌─────────────────────────────────────────────────┐");
        System.out.println("│          🤖 AI-Powered CV Generator            │");
        System.out.println("│                                                │");
        System.out.println("│  Transform your CV text into professional     │");
        System.out.println("│  PDFs using AI and LaTeX technology           │");
        System.out.println("└─────────────────────────────────────────────────┘");
        System.out.println();
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

    /**
     * Show system information and requirements check
     */
    private static void showSystemCheck() {
        System.out.println("🔍 System Check:");

        // Check Java version
        String javaVersion = System.getProperty("java.version");
        System.out.printf("   ✓ Java Version: %s\n", javaVersion);

        // Check if LaTeX is available
        try {
            Process proc = new ProcessBuilder("pdflatex", "--version").start();
            proc.waitFor();
            System.out.println("   ✓ LaTeX (pdflatex) is available");
        } catch (Exception e) {
            System.out.println("   ❌ LaTeX (pdflatex) not found - PDF generation will fail");
            System.out.println("      Install LaTeX: https://www.latex-project.org/get/");
        }

        // Check OpenAI API key
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            System.out.println("   ✓ OpenAI API key is configured");
        } else {
            System.out.println("   ❌ OpenAI API key not found (OPENAI_API_KEY environment variable)");
            System.out.println("      Set your API key: export OPENAI_API_KEY='your-key-here'");
        }

        System.out.println();
    }

    /**
     * Enhanced main method with system checks
     */
    public static void mainWithChecks(String[] args) {
        showApplicationHeader();
        showSystemCheck();
        main(args);
    }
}
