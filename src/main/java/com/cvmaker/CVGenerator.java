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
        } catch (IOException e) {
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

    public void generateCVFromText(String unstructuredTextPath, String templateName,
            String outputDir, String outputPdfName, String jobDescriptionPath) throws Exception {

        System.out.println("Loading input files...");
        String unstructuredText = Files.readString(Paths.get(unstructuredTextPath));

        String jobDescription = "";
        if (jobDescriptionPath != null && !jobDescriptionPath.trim().isEmpty()) {
            try {
                jobDescription = Files.readString(Paths.get(jobDescriptionPath));
            } catch (IOException e) {
                System.out.println("Warning: Could not load job description file.");
            }
        }

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
        String generatedLatex = aiService.generateDirectLatexCV(unstructuredText, referenceTemplate, jobDescription);

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

        try {
            String unstructuredText = Files.readString(Paths.get(unstructuredTextPath));

            String jobDescription = "";
            if (jobDescriptionPath != null && !jobDescriptionPath.trim().isEmpty()) {
                try {
                    jobDescription = Files.readString(Paths.get(jobDescriptionPath));
                } catch (IOException e) {
                    System.out.println("Warning: Could not load job description file. Proceeding without it.");
                }
            }

            CoverLetterService coverLetterService = new CoverLetterService(
                    config.getAiModel(),
                    config.getAiTemperature()
            );

            String generatedLatex = coverLetterService.generateStyledCoverLetter(
                    unstructuredText,
                    jobDescription,
                    companyName,
                    positionTitle,
                    applicantName,
                    contactInfo,
                    config.getCoverLetterStyle()
            );

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

            compileLatexWithProgress(outputDirPath, texOutputPath, config.getCoverLetterPdfName());

            coverLetterService.shutdown();

        } catch (IOException | InterruptedException e) {
            throw e;
        }
    }

}
