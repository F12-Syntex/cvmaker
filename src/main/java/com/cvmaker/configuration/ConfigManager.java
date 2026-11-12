package com.cvmaker.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.cvmaker.service.ai.LLMModel;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class ConfigManager {

    private static final String DEFAULT_CONFIG_FILE = "configuration/config.properties";

    /** Default AI temperature, also used when the configured value is malformed or out of range. */
    static final double DEFAULT_AI_TEMPERATURE = 0.3;
    /** OpenAI accepts temperatures from 0 to 2. */
    static final double MAX_AI_TEMPERATURE = 2.0;

    // Template settings
    private String templateName;
    private String templateDirectory;

    // Input settings
    private String userDataFile;
    private String jobUrl;
    private String jobDescriptionFile;
    private String cvPromptFile;
    private String coverLetterPromptFile;

    // Content (loaded from files). Excluded from toString so logging the config never prints the
    // user's CV or prompts.
    @ToString.Exclude
    private String userDataContent;
    @ToString.Exclude
    private String jobDescriptionContent;
    @ToString.Exclude
    private String cvPromptContent;
    @ToString.Exclude
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
        this.jobUrl = jobUrl == null ? "" : jobUrl.trim();
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
        this.aiTemperature = DEFAULT_AI_TEMPERATURE;
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
        this.templateName = TypedProperties.stringProp(properties, "template.name", this.templateName);
        this.templateDirectory = TypedProperties.stringProp(properties, "template.directory", this.templateDirectory);
    }

    private void loadInputSettings(Properties properties) {
        this.userDataFile = TypedProperties.stringProp(properties, "input.user.data.file", this.userDataFile);
        this.jobUrl = TypedProperties.stringProp(properties, "input.job.url", this.jobUrl);
        this.jobDescriptionFile = TypedProperties.stringProp(properties, "input.job.description.file", this.jobDescriptionFile);
        this.cvPromptFile = TypedProperties.stringProp(properties, "input.cv.prompt.file", this.cvPromptFile);
        this.coverLetterPromptFile = TypedProperties.stringProp(properties, "input.cover.letter.prompt.file", this.coverLetterPromptFile);
    }

    private void loadOutputSettings(Properties properties) {
        this.outputDirectory = TypedProperties.stringProp(properties, "output.directory", this.outputDirectory);
        this.outputPdfName = TypedProperties.stringProp(properties, "output.pdf.name", this.outputPdfName);
        this.coverLetterPdfName = TypedProperties.stringProp(properties, "output.cover.letter.pdf.name", this.coverLetterPdfName);
        this.cvTexFilename = TypedProperties.stringProp(properties, "output.cv.tex.filename", this.cvTexFilename);
        this.coverLetterTexFilename = TypedProperties.stringProp(properties, "output.cover.letter.tex.filename", this.coverLetterTexFilename);
        this.generateCoverLetter = TypedProperties.boolProp(properties, "output.generate.cover.letter", this.generateCoverLetter);
    }

    private void loadAiSettings(Properties properties) {
        String modelName = properties.getProperty("ai.model");
        if (modelName != null) {
            String normalized = modelName.trim().toUpperCase(Locale.ROOT);
            try {
                this.aiModel = LLMModel.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                System.out.println("Warning: Invalid AI model '" + modelName.trim() + "', using default " + this.aiModel);
            }
        }

        this.aiTemperature = TypedProperties.doubleProp(properties, "ai.temperature",
                DEFAULT_AI_TEMPERATURE, 0.0, MAX_AI_TEMPERATURE);
    }

    private void loadDebugSettings(Properties properties) {
        this.saveGeneratedLatex = TypedProperties.boolProp(properties, "debug.save.generated.latex", this.saveGeneratedLatex);
        this.saveAiResponses = TypedProperties.boolProp(properties, "debug.save.ai.responses", this.saveAiResponses);
    }

    private void loadPerformanceSettings(Properties properties) {
        this.aiRequestDelayMs = TypedProperties.intProp(properties, "ai.request_delay_ms", this.aiRequestDelayMs, 0);
        this.aiMaxRetries = TypedProperties.intProp(properties, "ai.max_retries", this.aiMaxRetries, 0);
        this.aiTimeoutSeconds = TypedProperties.intProp(properties, "ai.timeout_seconds", this.aiTimeoutSeconds, 1);
    }

    private void loadLatexSettings(Properties properties) {
        this.latexCompiler = TypedProperties.stringProp(properties, "latex.compiler", this.latexCompiler);
        this.latexCompilerArgs = TypedProperties.listProp(properties, "latex.compiler.args", this.latexCompilerArgs);
        this.cleanupExtensions = TypedProperties.listProp(properties, "latex.cleanup.extensions", this.cleanupExtensions);
        this.progressReportInterval = TypedProperties.intProp(properties, "latex.progress.report.interval", this.progressReportInterval, 1);
    }

    private void loadFileContents() throws IOException {
        // Load user data content
        this.userDataContent = loadFileContent(userDataFile, "User data file");

        // Load job description content if file exists (skip if using URL)
        // (never left null: empty when there is neither a file nor a URL)
        if (jobDescriptionFile != null && !jobDescriptionFile.isEmpty() && !hasJobUrl()) {
            this.jobDescriptionContent = loadFileContentOptional(jobDescriptionFile, "Job description file");
        } else {
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
