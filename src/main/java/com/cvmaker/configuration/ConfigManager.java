package com.cvmaker.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.cvmaker.service.ai.LLMModel;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class ConfigManager {

    private static final String DEFAULT_CONFIG_FILE = "configuration/config.properties";

    // Template settings
    private String templateName;
    private String templateDirectory;

    // Input settings
    private String userDataFile;
    private String jobUrl;
    private String jobDescriptionFile;
    private String cvPromptFile;
    private String coverLetterPromptFile;

    // Content (loaded from files)
    private String userDataContent;
    private String jobDescriptionContent;
    private String cvPromptContent;
    private String coverLetterPromptContent;

    // Output settings
    private String outputDirectory;
    private String outputPdfName;
    private String coverLetterPdfName;
    private String cvTexFilename;
    private String coverLetterTexFilename;

    // AI settings
    private LLMModel aiModel;
    private double aiTemperature;
    private int aiRequestDelayMs;
    private int aiMaxRetries;
    private int aiTimeoutSeconds;

    // Debug settings
    private boolean saveGeneratedLatex;
    private boolean saveAiResponses;
    private boolean generateCoverLetter;

    // LaTeX compilation settings
    private String latexCompiler;
    private List<String> latexCompilerArgs;
    private List<String> cleanupExtensions;
    private int progressReportInterval;

    public ConfigManager() throws IOException {
        this(DEFAULT_CONFIG_FILE);
    }

    public ConfigManager(String configFilePath) throws IOException {
        loadConfiguration(configFilePath);
    }

    // Constructor for programmatic configuration with job URL
    public ConfigManager(String jobUrl, String userDataFile, String cvPromptFile, String coverLetterPromptFile) throws IOException {
        setDefaults();

        // Set provided values
        this.jobUrl = jobUrl;
        this.userDataFile = userDataFile;
        this.cvPromptFile = cvPromptFile;
        this.coverLetterPromptFile = coverLetterPromptFile;
        this.jobDescriptionFile = "";

        // Load file contents
        loadFileContents();
    }

    private void setDefaults() {
        // Template defaults
        this.templateName = "classic";
        this.templateDirectory = "templates";

        // Input defaults
        this.userDataFile = "userdata.txt";
        this.jobUrl = "";
        this.jobDescriptionFile = "";
        this.cvPromptFile = "cv_prompt.txt";
        this.coverLetterPromptFile = "cover_letter_prompt.txt";

        // Output defaults
        this.outputDirectory = "generation";
        this.outputPdfName = "cv.pdf";
        this.coverLetterPdfName = "cover_letter.pdf";
        this.cvTexFilename = "cv.tex";
        this.coverLetterTexFilename = "cover_letter.tex";

        // AI defaults
        this.aiModel = LLMModel.GPT_4_1_MINI;
        this.aiTemperature = 0.3;
        this.aiRequestDelayMs = 1000;
        this.aiMaxRetries = 3;
        this.aiTimeoutSeconds = 60;

        // Debug defaults
        this.saveGeneratedLatex = false;
        this.saveAiResponses = false;
        this.generateCoverLetter = true;

        // LaTeX compilation defaults
        this.latexCompiler = "pdflatex";
        this.latexCompilerArgs = Arrays.asList("-interaction=nonstopmode");
        this.cleanupExtensions = Arrays.asList(".tex", ".log", ".aux", ".out", ".fdb_latexmk", ".fls", ".synctex.gz");
        this.progressReportInterval = 5;
    }

    private void loadConfiguration(String configFilePath) throws IOException {
        setDefaults();

        Properties properties = new Properties();
        Path configPath = Paths.get(configFilePath);

        if (!Files.exists(configPath)) {
            throw new IOException("Configuration file not found: " + configFilePath);
        }

        try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
            properties.load(fis);
        }

        // Load all properties into fields
        loadTemplateSettings(properties);
        loadInputSettings(properties);
        loadOutputSettings(properties);
        loadAiSettings(properties);
        loadDebugSettings(properties);
        loadPerformanceSettings(properties);
        loadLatexSettings(properties);

        // Load file contents
        loadFileContents();

        System.out.println("Configuration loaded from: " + configPath.toAbsolutePath());
    }

    private void loadTemplateSettings(Properties properties) {
        this.templateName = properties.getProperty("template.name", this.templateName);
        this.templateDirectory = properties.getProperty("template.directory", this.templateDirectory);
    }

    private void loadInputSettings(Properties properties) {
        this.userDataFile = properties.getProperty("input.user.data.file", this.userDataFile);
        this.jobUrl = properties.getProperty("input.job.url", this.jobUrl).trim();
        this.jobDescriptionFile = properties.getProperty("input.job.description.file", this.jobDescriptionFile).trim();
        this.cvPromptFile = properties.getProperty("input.cv.prompt.file", this.cvPromptFile);
        this.coverLetterPromptFile = properties.getProperty("input.cover.letter.prompt.file", this.coverLetterPromptFile);
    }

    private void loadOutputSettings(Properties properties) {
        this.outputDirectory = properties.getProperty("output.directory", this.outputDirectory);
        this.outputPdfName = properties.getProperty("output.pdf.name", this.outputPdfName);
        this.coverLetterPdfName = properties.getProperty("output.cover.letter.pdf.name", this.coverLetterPdfName);
        this.cvTexFilename = properties.getProperty("output.cv.tex.filename", this.cvTexFilename);
        this.coverLetterTexFilename = properties.getProperty("output.cover.letter.tex.filename", this.coverLetterTexFilename);
        this.generateCoverLetter = Boolean.parseBoolean(properties.getProperty("output.generate.cover.letter", String.valueOf(this.generateCoverLetter)));
    }

    private void loadAiSettings(Properties properties) {
        String modelName = properties.getProperty("ai.model", this.aiModel.toString());
        try {
            this.aiModel = LLMModel.valueOf(modelName);
        } catch (IllegalArgumentException e) {
            System.out.println("Warning: Invalid AI model '" + modelName + "', using default " + this.aiModel);
        }

        try {
            this.aiTemperature = Double.parseDouble(properties.getProperty("ai.temperature", String.valueOf(this.aiTemperature)));
            if (this.aiTemperature < 0.0 || this.aiTemperature > 1.0) {
                System.out.println("Warning: AI temperature should be between 0.0 and 1.0, using " + this.aiTemperature);
                this.aiTemperature = 0.3;
            }
        } catch (NumberFormatException e) {
            System.out.println("Warning: Invalid AI temperature value, using " + this.aiTemperature);
        }
    }

    private void loadDebugSettings(Properties properties) {
        this.saveGeneratedLatex = Boolean.parseBoolean(properties.getProperty("debug.save.generated.latex", String.valueOf(this.saveGeneratedLatex)));
        this.saveAiResponses = Boolean.parseBoolean(properties.getProperty("debug.save.ai.responses", String.valueOf(this.saveAiResponses)));
    }

    private void loadPerformanceSettings(Properties properties) {
        this.aiRequestDelayMs = Integer.parseInt(properties.getProperty("ai.request_delay_ms", String.valueOf(this.aiRequestDelayMs)));
        this.aiMaxRetries = Integer.parseInt(properties.getProperty("ai.max_retries", String.valueOf(this.aiMaxRetries)));
        this.aiTimeoutSeconds = Integer.parseInt(properties.getProperty("ai.timeout_seconds", String.valueOf(this.aiTimeoutSeconds)));
    }

    private void loadLatexSettings(Properties properties) {
        this.latexCompiler = properties.getProperty("latex.compiler", this.latexCompiler);

        String compilerArgsStr = properties.getProperty("latex.compiler.args", String.join(",", this.latexCompilerArgs));
        this.latexCompilerArgs = Arrays.asList(compilerArgsStr.split(","));

        String cleanupExtStr = properties.getProperty("latex.cleanup.extensions", String.join(",", this.cleanupExtensions));
        this.cleanupExtensions = Arrays.asList(cleanupExtStr.split(","));

        this.progressReportInterval = Integer.parseInt(properties.getProperty("latex.progress.report.interval", String.valueOf(this.progressReportInterval)));
    }

    private void loadFileContents() throws IOException {
        // Load user data content
        this.userDataContent = loadFileContent(userDataFile, "User data file");

        // Load job description content if file exists (skip if using URL)
        if (!jobDescriptionFile.isEmpty() && jobUrl.isEmpty()) {
            this.jobDescriptionContent = loadFileContentOptional(jobDescriptionFile, "Job description file");
        } else if (!jobUrl.isEmpty()) {
            this.jobDescriptionContent = "";
        }

        // Load CV prompt content
        this.cvPromptContent = loadFileContentOptional(cvPromptFile, "CV prompt file");

        // Load cover letter prompt content
        this.coverLetterPromptContent = loadFileContentOptional(coverLetterPromptFile, "Cover letter prompt file");
    }

    private String loadFileContent(String filePath, String description) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException(description + " not found: " + filePath);
        }
        return Files.readString(path);
    }

    private String loadFileContentOptional(String filePath, String description) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return Files.readString(path);
            } else {
                System.out.println("Warning: " + description + " not found: " + filePath);
                return "";
            }
        } catch (IOException e) {
            System.out.println("Warning: Could not read " + description + ": " + filePath);
            return "";
        }
    }

    // Method to set job description content (used when fetching from URL)
    public void setJobDescriptionContent(String jobDescriptionContent) {
        this.jobDescriptionContent = jobDescriptionContent;
    }

    // Method to reload file contents
    public void reloadFileContents() throws IOException {
        loadFileContents();
    }

    // Check if job URL is provided
    public boolean hasJobUrl() {
        return jobUrl != null && !jobUrl.trim().isEmpty();
    }
}
