package com.cvmaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.cvmaker.configuration.ConfigManager;

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
        } catch (IOException e) {
            System.out.println("Warning: Could not load configuration, using defaults for debug settings");
            return null;
        }
    }

    /**
     * Main CV generation method - now AI-only
     */
    public void generateCV(String templateName, String outputDir, String outputPdfName) throws Exception {
        // Redirect to AI generation since we only support that now
        generateCVFromText(templateName, outputDir, outputPdfName);
    }

    public void generateCVFromText(String templateName, String outputDir, String outputPdfName) throws Exception {

        System.out.println("Loading template...");
        String referenceTemplate = null;
        if (templateName != null && !templateName.isEmpty()) {
            try {
                referenceTemplate = templateLoader.loadTex(templateName);
            } catch (IOException e) {
                System.out.println("No reference template found.");
            }
        }

        System.out.println("Generating LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCV(config.getUserDataContent(), referenceTemplate, config.getJobDescriptionContent(), config.getCvPromptContent());

        System.out.println("Saving files...");
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path texOutputPath = outputDirPath.resolve("generated_cv.tex");
        Files.writeString(texOutputPath, generatedLatex);

        System.out.println("Compiling to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, outputPdfName);

        System.out.println("CV generation completed.");
    }

    /**
     * Generate cover letter from text using AI
     */
    public void generateCoverLetterFromText(String templateName, String outputDir, String outputPdfName) throws Exception {
        System.out.println("Loading cover letter template...");
        String referenceTemplate = null;
        if (templateName != null && !templateName.isEmpty()) {
            try {
                referenceTemplate = templateLoader.loadCoverLetterTex(templateName);
            } catch (IOException e) {
                System.out.println("No reference cover letter template found.");
            }
        }

        System.out.println("Generating cover letter LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCoverLetter(
            config.getUserDataContent(), 
            referenceTemplate, 
            config.getJobDescriptionContent(), 
            config.getCoverLetterPromptContent()
        );

        System.out.println("Saving cover letter files...");
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path texOutputPath = outputDirPath.resolve("generated_cover_letter.tex");
        Files.writeString(texOutputPath, generatedLatex);

        System.out.println("Compiling cover letter to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, outputPdfName);

        System.out.println("Cover letter generation completed.");
    }

    /**
     * Compile LaTeX to PDF with progress tracking
     */
    private void compileLatexWithProgress(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
        try {

            String texFileName = texFile.getFileName().toString();
            ProcessBuilder pb = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    texFileName
            );
            pb.redirectErrorStream(true);
            pb.directory(dir.toFile());

            Process proc = pb.start();

            // Read the output to monitor progress
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
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
            }

            int exitCode = proc.waitFor();

            // Check if PDF was generated
            String pdfFileName = texFileName.replace(".tex", ".pdf");
            Path pdfPath = dir.resolve(pdfFileName);
            boolean pdfExists = Files.exists(pdfPath);

            if (exitCode != 0 && !pdfExists) {
                System.err.println("LaTeX output:");
                System.err.println(output.toString());
                throw new RuntimeException("LaTeX compilation failed");
            }

            if (pdfExists) {
                if (!pdfFileName.equals(outputPdfName)) {
                    Files.move(pdfPath, dir.resolve(outputPdfName), StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new RuntimeException("PDF file was not generated");
            }

        } catch (IOException | InterruptedException | RuntimeException e) {
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

        generateCVFromText(
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getOutputPdfName()
        );
    }

    /**
     * Generate cover letter using configuration settings
     */
    public void generateCoverLetterFromConfig() throws Exception {
        if (config == null) {
            throw new IllegalStateException("No configuration loaded. Please provide a ConfigManager instance.");
        }

        generateCoverLetterFromText(
                config.getTemplateName(),
                config.getOutputDirectory(),
                config.getCoverLetterPdfName()
        );
    }

    /**
     * Generate both CV and cover letter using configuration settings
     */
    public void generateBothFromConfig() throws Exception {
        if (config == null) {
            throw new IllegalStateException("No configuration loaded. Please provide a ConfigManager instance.");
        }

        // Generate CV
        generateCVFromConfig();
        
        // Generate cover letter if enabled
        if (config.isGenerateCoverLetter()) {
            generateCoverLetterFromConfig();
        }
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
}