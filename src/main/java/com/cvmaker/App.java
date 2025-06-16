package com.cvmaker;

import java.nio.file.Paths;

public class App {

    public static void main(String[] args) {
        // Show application startup
        showApplicationHeader();

        ProgressTracker appProgress = ProgressTracker.create("CV Maker Application", 5);

        try {
            // Step 1: Load configuration
            appProgress.nextStep("Loading configuration");
            ConfigManager config = new ConfigManager();
            config.printConfiguration();

            // Step 2: Initialize components
            appProgress.nextStep("Initializing AI and template systems");
            TemplateLoader loader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
            AiService aiService = new AiService(config.getAiModel(), config.getAiTemperature());
            CVGenerator generator = new CVGenerator(loader, aiService, config);

            // Step 3: Validate configuration
            appProgress.nextStep("Validating configuration and files");
            config.validateConfiguration();

            // Step 4: Generate CV using AI
            appProgress.nextStep("Starting AI-powered CV generation");
            generateWithAI(config, generator);

            // Step 5: Generate Cover Letter using AI
            appProgress.nextStep("Starting AI-powered cover letter generation");
            generateCoverLetterWithAI(config, generator);

            appProgress.complete();
            showSuccessMessage(config);

            // Cleanup
            aiService.shutdown();

        } catch (Exception e) {
            appProgress.fail(e.getMessage());
            showErrorMessage(e);
            System.exit(1);
        }
    }

    /**
     * Generate a cover letter based on configuration
     */
    private static void generateCoverLetterWithAI(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("\n≡ƒñû Starting AI-Powered Cover Letter Generation");
        System.out.println("ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ");
        System.out.println("Γä╣∩╕Å  Note: AI generation may take 1-2 minutes for a personalized cover letter");

        long startTime = System.currentTimeMillis();
        System.out.println("≡ƒôè Estimated time: 30-60 seconds for AI cover letter generation");

        // Get company details from configuration or environment
        String companyName = System.getenv().getOrDefault("COMPANY_NAME", "Example Company");
        String positionTitle = System.getenv().getOrDefault("POSITION_TITLE", "Software Engineer");
        String applicantName = System.getenv().getOrDefault("APPLICANT_NAME", "John Doe");
        String contactInfo = System.getenv().getOrDefault("CONTACT_INFO", "john.doe@email.com | (123) 456-7890");

        // Generate the cover letter
        generator.generateCoverLetter(
                config.getUserDataFile(),
                config.getJobDescriptionFile(),
                companyName,
                positionTitle,
                applicantName,
                contactInfo
        );

        long endTime = System.currentTimeMillis();
        System.out.printf("Γ£à AI cover letter generation completed in %s\n",
                formatDuration(endTime - startTime));

        // Show cover letter specific success message
        System.out.println("\n≡ƒôï Cover Letter Output Location:");
        System.out.printf("   %s/%s\n", config.getOutputDirectory(), config.getCoverLetterPdfName());
        System.out.println("   ΓÇó LaTeX source: generated_cover_letter.tex");

        if (config.shouldSaveDebugFiles()) {
            System.out.println("   ΓÇó Debug file: debug_ai_cover_letter.tex");
        }
    }
    

    private static void generateWithAI(ConfigManager config, CVGenerator generator) throws Exception {
        System.out.println("\n🤖 Starting AI-Powered CV Generation");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("ℹ️  Note: AI generation may take 1-3 minutes depending on content complexity");

        long startTime = System.currentTimeMillis();
        System.out.println("📊 Estimated time: 60-120 seconds for AI LaTeX generation");

        generator.generateCVFromText(
                config.getUserDataFile(),
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName(),
                config.getJobDescriptionFile()
        );

        long endTime = System.currentTimeMillis();
        System.out.printf("✅ AI generation completed in %s\n",
                formatDuration(endTime - startTime));
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
    

    private static void showSuccessMessage(ConfigManager config) {
        System.out.println("\n┌─────────────────────────────────────────────────┐");
        System.out.println("│                🎉 SUCCESS! 🎉                 │");
        System.out.println("└─────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("📋 Your professional CV has been generated successfully!");
        System.out.println();
        System.out.println("📂 Output Location:");
        System.out.printf("   %s/%s\n", config.getOutputDirectory(), config.getOutputPdfName());
        System.out.println();
        System.out.println("📁 Generated Files:");
        System.out.printf("   • %s/%s (PDF)\n", config.getOutputDirectory(), config.getOutputPdfName());
        System.out.printf("   • %s/generated_cv.tex (LaTeX source)\n", config.getOutputDirectory());

        if (config.shouldSaveDebugFiles()) {
            System.out.println("   • Debug files (for troubleshooting)");
        }

        System.out.println();
        System.out.println("💡 Tips:");
        System.out.println("   • Review the generated PDF");
        System.out.println("   • Customize the LaTeX source if needed");
        System.out.println("   • Use different reference templates for various styles");
        System.out.println("   • Tailor your input text for different job applications");
        System.out.println();
    }

    private static void showErrorMessage(Exception e) {
        System.out.println("\n┌─────────────────────────────────────────────────┐");
        System.out.println("│                ❌ ERROR ❌                    │");
        System.out.println("└─────────────────────────────────────────────────┘");
        System.out.println();
        System.out.printf("⚠️ Generation failed: %s\n", e.getMessage());
        System.out.println();
        System.out.println("🔧 Troubleshooting:");

        if (e.getMessage().contains("Configuration file not found")) {
            System.out.println("   • Ensure config.properties exists in the project root");
            System.out.println("   • Check the configuration file path");
        } else if (e.getMessage().contains("User data file not found")) {
            System.out.println("   • Check that your input data file exists");
            System.out.println("   • Verify the file path in config.properties");
        } else if (e.getMessage().contains("LaTeX compilation failed")) {
            System.out.println("   • Ensure LaTeX is installed (pdflatex command available)");
            System.out.println("   • Check LaTeX syntax in generated template");
            System.out.println("   • Review the LaTeX error log for details");
        } else if (e.getMessage().contains("AI") || e.getMessage().contains("OpenAI")) {
            System.out.println("   • Check your OpenAI API key is set (OPENAI_API_KEY)");
            System.out.println("   • Verify internet connection");
            System.out.println("   • Ensure you have sufficient API credits");
            System.out.println("   • Try reducing the input text size if very large");
        } else {
            System.out.println("   • Review the error message above");
            System.out.println("   • Check log files for detailed information");
            System.out.println("   • Verify all required files and dependencies");
        }

        System.out.println();
        System.out.println("📖 For more help:");
        System.out.println("   • Check the README.md file");
        System.out.println("   • Review configuration examples");
        System.out.println("   • Enable debug mode for more details");
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
