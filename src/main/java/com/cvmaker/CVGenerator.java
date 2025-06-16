package com.cvmaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CVGenerator {

    private final TemplateLoader templateLoader;
    private final AiService aiService;
    private ConfigManager config;

    public CVGenerator(TemplateLoader loader) {
        this.templateLoader = loader;
        this.aiService = new AiService();
        this.config = loadConfigSafely();
    }

    public CVGenerator(TemplateLoader loader, AiService aiService) {
        this.templateLoader = loader;
        this.aiService = aiService;
        this.config = loadConfigSafely();
    }

    public CVGenerator(TemplateLoader loader, AiService aiService, ConfigManager config) {
        this.templateLoader = loader;
        this.aiService = aiService;
        this.config = config;
    }

    private ConfigManager loadConfigSafely() {
        try {
            return new ConfigManager();
        } catch (Exception e) {
            System.out.println("Warning: Could not load configuration, using defaults for debug settings");
            return null;
        }
    }

    /**
     * Main CV generation method - now AI-only
     */
    public void generateCV(String userDataPath, String templateName, String outputDir, String outputPdfName) throws Exception {
        // Redirect to AI generation since we only support that now
        generateCVFromText(userDataPath, templateName, outputDir, outputPdfName, null);
    }

    /**
     * Generate CV from unstructured text using AI
     */
    public void generateCVFromText(String unstructuredTextPath, String templateName,
            String outputDir, String outputPdfName, String jobDescriptionPath) throws Exception {

        ProgressTracker progress = ProgressTracker.forAIGeneration();

        try {
            // Step 1: Load input files
            progress.nextStep("Loading input files");
            String unstructuredText = Files.readString(Paths.get(unstructuredTextPath));

            String jobDescription = "";
            if (jobDescriptionPath != null && !jobDescriptionPath.trim().isEmpty()) {
                try {
                    jobDescription = Files.readString(Paths.get(jobDescriptionPath));
                } catch (IOException e) {
                    System.out.println("Warning: Could not load job description file. Proceeding without it.");
                }
            }

            // Step 2: Load reference template (if available)
            progress.nextStep("Loading reference template (optional)");
            String referenceTemplate = null;
            if (templateName != null && !templateName.isEmpty()) {
                try {
                    referenceTemplate = templateLoader.loadTex(templateName);
                    System.out.println("✅ Reference template loaded: " + templateName);
                } catch (IOException e) {
                    System.out.println("ℹ️ No reference template found. AI will generate from scratch.");
                }
            }

            // Step 3: Generate LaTeX with AI
            progress.startStep("Generating LaTeX with AI (this may take 30-60 seconds)");
            progress.showAIProgress("Analyzing your CV content and generating professional LaTeX");
            String generatedLatex = aiService.generateDirectLatexCV(unstructuredText, referenceTemplate, jobDescription);
            progress.completeStep("AI LaTeX generation completed");

            // Step 4: Create output directory and save files
            progress.nextStep("Saving generated files");
            Path outputDirPath = Paths.get(outputDir);
            Files.createDirectories(outputDirPath);
            Path texOutputPath = outputDirPath.resolve("generated_cv.tex");
            Files.writeString(texOutputPath, generatedLatex);

            // Optional debug file saving
            if (config != null && config.shouldSaveDebugFiles()) {
                Path debugTexPath = outputDirPath.resolve("debug_ai_generated.tex");
                Files.writeString(debugTexPath, generatedLatex);
                System.out.println("📄 Debug LaTeX saved to: " + debugTexPath);
            }

            // Step 5: Compile to PDF
            progress.nextStep("Compiling LaTeX to PDF");
            compileLatexWithProgress(outputDirPath, texOutputPath, outputPdfName);

            // Step 6: Finalize
            progress.nextStep("Finalizing CV generation");

            progress.complete();
        } catch (Exception e) {
            progress.fail(e.getMessage());
            throw e;
        }
    }

    /**
     * Compile LaTeX to PDF with progress tracking
     */
    private void compileLatexWithProgress(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
        // Show LaTeX compilation progress
        ProgressTracker latexProgress = ProgressTracker.create("LaTeX Compilation", 4);

        try {
            latexProgress.nextStep("Preparing compilation environment");

            String texFileName = texFile.getFileName().toString();
            ProcessBuilder pb = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    texFileName
            );
            pb.redirectErrorStream(true);
            pb.directory(dir.toFile());

            latexProgress.nextStep("Starting pdflatex process");
            Process proc = pb.start();

            latexProgress.nextStep("Processing LaTeX document");

            // Read the output to monitor progress
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            boolean hasError = false;
            StringBuilder output = new StringBuilder();
            int pageCount = 0;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");

                // Track page processing
                if (line.contains("[") && line.matches(".*\\[\\d+.*")) {
                    pageCount++;
                    if (pageCount % 5 == 0) {
                        System.out.printf("   📄 Processing page %d...\n", pageCount);
                    }
                }

                // Check for errors
                if (line.toLowerCase().contains("error") && !line.toLowerCase().contains("rerun")) {
                    hasError = true;
                }
                if (line.toLowerCase().contains("fatal")) {
                    hasError = true;
                }
            }

            int exitCode = proc.waitFor();

            latexProgress.nextStep("Finalizing PDF generation");

            // Check if PDF was generated
            String pdfFileName = texFileName.replace(".tex", ".pdf");
            Path pdfPath = dir.resolve(pdfFileName);
            boolean pdfExists = Files.exists(pdfPath);

            if (exitCode != 0 && !pdfExists) {
                latexProgress.fail("LaTeX compilation failed - check syntax and dependencies");
                System.err.println("LaTeX output:");
                System.err.println(output.toString());
                throw new RuntimeException("LaTeX compilation failed");
            }

            if (pdfExists) {
                // Move the PDF to the desired name if needed
                if (!pdfFileName.equals(outputPdfName)) {
                    Files.move(pdfPath, dir.resolve(outputPdfName), StandardCopyOption.REPLACE_EXISTING);
                }
                latexProgress.complete();
            } else {
                latexProgress.fail("PDF file was not generated");
                throw new RuntimeException("PDF file was not generated");
            }

        } catch (Exception e) {
            latexProgress.fail(e.getMessage());
            throw e;
        }
    }

    /**
     * Generate CV using configuration settings
     */
    public void generateCVFromConfig() throws Exception {
        if (config == null) {
            throw new IllegalStateException("No configuration loaded. Please provide a ConfigManager instance.");
        }

        config.validateConfiguration();

        // Always use AI generation since that's the only mode we support now
        generateCVFromText(
                config.getUserDataFile(),
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName(),
                config.getJobDescriptionFile()
        );
    }

    /**
     * Get the current configuration manager
     */
    public ConfigManager getConfig() {
        return config;
    }

    /**
     * Set a new configuration manager
     */
    public void setConfig(ConfigManager config) {
        this.config = config;
    }

    /**
     * Get the AI service instance
     */
    public AiService getAiService() {
        return aiService;
    }

    /**
     * Get the template loader instance
     */
    public TemplateLoader getTemplateLoader() {
        return templateLoader;
    }

    /**
     * Generate a cover letter using AI
     */
    public void generateCoverLetter(String unstructuredTextPath, String jobDescriptionPath,
            String companyName, String positionTitle, String applicantName,
            String contactInfo) throws Exception {

        ProgressTracker progress = ProgressTracker.create("Cover Letter Generation", 4);

        try {
            // Step 1: Load input files
            progress.nextStep("Loading input files");
            String unstructuredText = Files.readString(Paths.get(unstructuredTextPath));

            String jobDescription = "";
            if (jobDescriptionPath != null && !jobDescriptionPath.trim().isEmpty()) {
                try {
                    jobDescription = Files.readString(Paths.get(jobDescriptionPath));
                } catch (IOException e) {
                    System.out.println("Warning: Could not load job description file. Proceeding without it.");
                }
            }

            // Step 2: Initialize cover letter service
            progress.nextStep("Initializing cover letter service");
            CoverLetterService coverLetterService = new CoverLetterService(
                    config.getAiModel(),
                    config.getAiTemperature()
            );

            // Step 3: Generate LaTeX with AI
            progress.startStep("Generating cover letter with AI");
            progress.showAIProgress("Creating personalized cover letter");
            String generatedLatex = coverLetterService.generateStyledCoverLetter(
                    unstructuredText,
                    jobDescription,
                    companyName,
                    positionTitle,
                    applicantName,
                    contactInfo,
                    config.getCoverLetterStyle()
            );
            progress.completeStep("Cover letter generation completed");

            // Step 4: Create output directory and save files
            progress.nextStep("Saving generated files");
            Path outputDirPath = Paths.get(config.getOutputDirectory());
            Files.createDirectories(outputDirPath);
            Path texOutputPath = outputDirPath.resolve("generated_cover_letter.tex");
            Files.writeString(texOutputPath, generatedLatex);

            // Optional debug file saving
            if (config.shouldSaveDebugFiles()) {
                Path debugTexPath = outputDirPath.resolve("debug_ai_cover_letter.tex");
                Files.writeString(debugTexPath, generatedLatex);
                System.out.println("≡ƒôä Debug LaTeX saved to: " + debugTexPath);
            }

            // Step 5: Compile to PDF
            progress.nextStep("Compiling LaTeX to PDF");
            compileLatexWithProgress(outputDirPath, texOutputPath, config.getCoverLetterPdfName());

            progress.complete();
            coverLetterService.shutdown();

        } catch (Exception e) {
            progress.fail(e.getMessage());
            throw e;
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

        // You would need to get these values from somewhere (command line args, GUI, etc.)
        String companyName = "Example Company";
        String positionTitle = "Software Engineer";
        String applicantName = "John Doe";
        String contactInfo = "john.doe@email.com | (123) 456-7890";

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
                App.formatDuration(endTime - startTime));
    }
}
