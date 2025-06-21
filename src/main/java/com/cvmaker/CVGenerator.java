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

public class CVGenerator {

    private final TemplateLoader templateLoader;
    private final AiService aiService;
    private final JobDataFetcher jobDataFetcher;
    private ConfigManager config;

    public CVGenerator(TemplateLoader loader) {
        this.templateLoader = loader;
        this.aiService = new AiService();
        this.jobDataFetcher = new JobDataFetcher();
        this.config = loadConfigSafely();
    }

    public CVGenerator(TemplateLoader loader, AiService aiService) {
        this.templateLoader = loader;
        this.aiService = aiService;
        this.jobDataFetcher = new JobDataFetcher();
        this.config = loadConfigSafely();
    }

    public CVGenerator(TemplateLoader loader, AiService aiService, ConfigManager config) {
        this.templateLoader = loader;
        this.aiService = aiService;
        this.jobDataFetcher = new JobDataFetcher();
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
     * Generate CV and cover letter from job URL
     */
    public void generateFromJobUrl(String jobUrl, String userDataFile, String cvPromptFile, String coverLetterPromptFile) throws Exception {
        System.out.println("Starting generation from job URL...");

        // Fetch job data
        JobData jobData = jobDataFetcher.fetchJobData(jobUrl);
        System.out.println("Fetched job data: " + jobData);

        // Create configuration with job data
        ConfigManager jobConfig = new ConfigManager(jobUrl, userDataFile, cvPromptFile, coverLetterPromptFile);
        jobConfig.setJobDescriptionContent(jobData.getJobDescription());

        // Create output directory structure
        String outputDir = "generation/" + jobData.getJobName();
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);

        System.out.println("Output directory: " + outputDirPath.toAbsolutePath());

        // Generate CV
        generateCVFromJobData(jobData, jobConfig, outputDir);

        // Generate cover letter if enabled
        if (jobConfig.isGenerateCoverLetter()) {
            generateCoverLetterFromJobData(jobData, jobConfig, outputDir);
        }

        System.out.println("Generation completed for: " + jobData.getJobTitle() + " at " + jobData.getCompanyName());
    }

    /**
     * Generate CV for specific job data
     */
    private void generateCVFromJobData(JobData jobData, ConfigManager jobConfig, String outputDir) throws Exception {
        System.out.println("Generating CV for: " + jobData.getJobTitle());

        // Load template
        String referenceTemplate = null;
        if (jobConfig.getTemplateName() != null && !jobConfig.getTemplateName().isEmpty()) {
            try {
                referenceTemplate = templateLoader.loadTex(jobConfig.getTemplateName());
            } catch (IOException e) {
                System.out.println("No reference template found, proceeding without template.");
            }
        }

        // Generate LaTeX with AI
        System.out.println("Generating CV LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCV(
                jobConfig.getUserDataContent(),
                referenceTemplate,
                jobConfig.getJobDescriptionContent(),
                jobConfig.getCvPromptContent()
        );

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve("cv.tex");
        Files.writeString(texOutputPath, generatedLatex);

        System.out.println("Compiling CV to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, "cv.pdf");

        // Clean up intermediate files
        cleanupIntermediateFiles(outputDirPath, "cv");

        System.out.println("CV generated: " + outputDirPath.resolve("cv.pdf").toAbsolutePath());
    }

    /**
     * Generate cover letter for specific job data
     */
    private void generateCoverLetterFromJobData(JobData jobData, ConfigManager jobConfig, String outputDir) throws Exception {
        System.out.println("Generating cover letter for: " + jobData.getJobTitle());

        // Load template
        String referenceTemplate = null;
        if (jobConfig.getTemplateName() != null && !jobConfig.getTemplateName().isEmpty()) {
            try {
                referenceTemplate = templateLoader.loadCoverLetterTex(jobConfig.getTemplateName());
            } catch (IOException e) {
                System.out.println("No reference cover letter template found, proceeding without template.");
            }
        }

        // Generate LaTeX with AI
        System.out.println("Generating cover letter LaTeX with AI...");
        String generatedLatex = aiService.generateDirectLatexCoverLetter(
                jobConfig.getUserDataContent(),
                referenceTemplate,
                jobConfig.getJobDescriptionContent(),
                jobConfig.getCoverLetterPromptContent()
        );

        // Save and compile
        Path outputDirPath = Paths.get(outputDir);
        Path texOutputPath = outputDirPath.resolve("cover_letter.tex");
        Files.writeString(texOutputPath, generatedLatex);

        System.out.println("Compiling cover letter to PDF...");
        compileLatexWithProgress(outputDirPath, texOutputPath, "cover_letter.pdf");

        // Clean up intermediate files
        cleanupIntermediateFiles(outputDirPath, "cover_letter");

        System.out.println("Cover letter generated: " + outputDirPath.resolve("cover_letter.pdf").toAbsolutePath());
    }

    /**
     * Clean up intermediate LaTeX files, keeping only PDFs
     */
    private void cleanupIntermediateFiles(Path outputDir, String baseName) {
        String[] extensions = {".tex", ".log", ".aux", ".out", ".fdb_latexmk", ".fls", ".synctex.gz"};

        for (String ext : extensions) {
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
     * Main CV generation method - now AI-only
     */
    public void generateCV(String templateName, String outputDir, String outputPdfName) throws Exception {
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

    // Getters
    public ConfigManager getConfig() {
        return config;
    }

    public void setConfig(ConfigManager config) {
        this.config = config;
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
}
