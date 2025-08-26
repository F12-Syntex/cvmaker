package com.cvmaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import com.cvmaker.JobDataFetcher.JobData;
import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.service.ai.AiService;

public class CVGenerator {

    private final TemplateLoader templateLoader;
    private final AiService aiService;
    private final JobDataFetcher jobDataFetcher;
    private final ConfigManager config;

    public CVGenerator(ConfigManager config) {
        this.config = config;
        this.templateLoader = new TemplateLoader(Paths.get(config.getTemplateDirectory()));
        this.aiService = new AiService(config.getAiModel(), config.getAiTemperature());
        this.jobDataFetcher = new JobDataFetcher();
    }

    /**
     * Main entry point - generates everything based on config
     */
    public void generate() throws Exception {
        System.out.println("Starting CV generation...");

        if (config.hasJobUrl()) {
            generateFromJobUrl();
        } else {
            generateFromText();
        }

        System.out.println("Generation completed successfully!");
    }

    /**
     * Generate CV and cover letter from job URL
     */
    private void generateFromJobUrl() throws Exception {
        System.out.println("Generating from job URL: " + config.getJobUrl());

        // Fetch job data
        JobData jobData = jobDataFetcher.fetchJobData(config.getJobUrl());
        System.out.println("Fetched job data: " + jobData);

        // Update config with job description
        config.setJobDescriptionContent(jobData.getJobDescription());

        // Create output directory structure
        String outputDir = createOutputDirectory(jobData.getJobName());
        System.out.println("Output directory: " + Paths.get(outputDir).toAbsolutePath());

        // Generate CV
        generateCV(outputDir, config.getOutputPdfName());

        // Generate cover letter if enabled
        if (config.isGenerateCoverLetter()) {
            generateCoverLetter(outputDir, config.getCoverLetterPdfName());
        }

        System.out.println("Generation completed for: " + jobData.getJobTitle() + " at " + jobData.getCompanyName());
    }

    /**
     * Generate CV and cover letter from text files
     */
    private void generateFromText() throws Exception {
        System.out.println("Generating from configuration files...");

        String outputDir = createOutputDirectory();

        // Generate CV
        generateCV(outputDir, config.getOutputPdfName());

        // Generate cover letter if enabled
        if (config.isGenerateCoverLetter()) {
            generateCoverLetter(outputDir, config.getCoverLetterPdfName());
        }
    }

    /**
     * Generate CV
     */
    private void generateCV(String outputDir, String pdfName) throws Exception {
        System.out.println("Generating CV...");

        // Load template if specified
        String referenceTemplate = loadTemplate(config.getTemplateName(), false);

        // Generate LaTeX with AI
        System.out.println("Generating CV LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCV(
                config.getUserDataContent(),
                referenceTemplate,
                config.getJobDescriptionContent(),
                config.getCvPromptContent()
        );

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve(config.getCvTexFilename());
        Files.writeString(texOutputPath, generatedLatex);

        if (config.isSaveGeneratedLatex()) {
            System.out.println("LaTeX saved: " + texOutputPath.toAbsolutePath());
        }

        System.out.println("Compiling CV to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, pdfName);

        // Clean up intermediate files if not in debug mode
        if (!config.isSaveGeneratedLatex()) {
            cleanupIntermediateFiles(outputDirPath, getBaseName(texOutputPath));
        }

        System.out.println("CV generated: " + outputDirPath.resolve(pdfName).toAbsolutePath());
    }

    /**
     * Generate cover letter
     */
    private void generateCoverLetter(String outputDir, String pdfName) throws Exception {
        System.out.println("Generating cover letter...");

        // Load template if specified
        String referenceTemplate = loadTemplate(config.getTemplateName(), true);

        // Generate LaTeX with AI
        System.out.println("Generating cover letter LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCoverLetter(
                config.getUserDataContent(),
                referenceTemplate,
                config.getJobDescriptionContent(),
                config.getCoverLetterPromptContent()
        );

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve(config.getCoverLetterTexFilename());
        Files.writeString(texOutputPath, generatedLatex);

        if (config.isSaveGeneratedLatex()) {
            System.out.println("LaTeX saved: " + texOutputPath.toAbsolutePath());
        }

        System.out.println("Compiling cover letter to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, pdfName);

        // Clean up intermediate files if not in debug mode
        if (!config.isSaveGeneratedLatex()) {
            cleanupIntermediateFiles(outputDirPath, getBaseName(texOutputPath));
        }

        System.out.println("Cover letter generated: " + outputDirPath.resolve(pdfName).toAbsolutePath());
    }

    /**
     * Load template with error handling
     */
    private String loadTemplate(String templateName, boolean isCoverLetter) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return null;
        }

        try {
            if (isCoverLetter) {
                return templateLoader.loadCoverLetterTex(templateName);
            } else {
                return templateLoader.loadTex(templateName);
            }
        } catch (IOException e) {
            String templateType = isCoverLetter ? "cover letter template" : "template";
            System.out.println("Warning: No " + templateType + " found for '" + templateName + "', proceeding without template.");
            return null;
        }
    }

    /**
     * Create output directory
     */
    private String createOutputDirectory() throws IOException {
        return createOutputDirectory(null);
    }

    private String createOutputDirectory(String jobName) throws IOException {
        String outputDir;
        if (jobName != null && !jobName.trim().isEmpty()) {
            outputDir = Paths.get(config.getOutputDirectory(), jobName).toString();
        } else {
            outputDir = config.getOutputDirectory();
        }

        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        return outputDir;
    }

    /**
     * Get base name from file path
     */
    private String getBaseName(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * Clean up intermediate LaTeX files
     */
    private void cleanupIntermediateFiles(Path outputDir, String baseName) {
        for (String ext : config.getCleanupExtensions()) {
            try {
                Path file = outputDir.resolve(baseName + ext);
                if (Files.exists(file)) {
                    Files.delete(file);
                }
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Compile LaTeX to PDF with progress tracking
     */
    private void compileLatexWithProgress(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
        String texFileName = texFile.getFileName().toString();
        // Build command array: compiler + args + tex file
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(config.getLatexCompiler());
        command.addAll(config.getLatexCompilerArgs());
        command.add(texFileName);
        ProcessBuilder pb = new ProcessBuilder(command);

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
                if (pageCount % config.getProgressReportInterval() == 0) {
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
            if (config.isSaveAiResponses()) {
                System.out.println("LaTeX compilation output:");
                System.out.println(output.toString());
            }
            throw new RuntimeException("LaTeX compilation failed");
        }

        if (pdfExists) {
            if (!pdfFileName.equals(outputPdfName)) {
                Files.move(pdfPath, dir.resolve(outputPdfName), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            throw new RuntimeException("PDF file was not generated");
        }
    }

    // Getters
    public ConfigManager getConfig() {
        return config;
    }

    public AiService getAiService() {
        return aiService;
    }

    public TemplateLoader getTemplateLoader() {
        return templateLoader;
    }

    public JobDataFetcher getJobDataFetcher() {
        return jobDataFetcher;
    }

    public void shutdown() {
        if (aiService != null) {
            aiService.shutdown();
        }
    }
}
