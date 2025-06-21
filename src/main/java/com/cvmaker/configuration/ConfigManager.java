package com.cvmaker.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.openai.models.ChatModel;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class ConfigManager {

    private static final String DEFAULT_CONFIG_FILE = "config.properties";

    private String templateName;
    private String templateDirectory;

    private String userDataFile;
    private String jobUrl; // New field for job URL
    private String jobDescriptionFile;
    private String cvPromptFile;
    private String coverLetterPromptFile;

    private String userDataContent;
    private String jobDescriptionContent;
    private String cvPromptContent;
    private String coverLetterPromptContent;

    private String outputDirectory;
    private String outputPdfName;
    private String coverLetterPdfName;

    private ChatModel aiModel;
    private double aiTemperature;

    private boolean saveGeneratedLatex;
    private boolean saveAiResponses;
    private boolean generateCoverLetter;

    private int aiRequestDelayMs;
    private int aiMaxRetries;
    private int aiTimeoutSeconds;

    public ConfigManager() throws IOException {
        this(DEFAULT_CONFIG_FILE);
    }

    public ConfigManager(String configFilePath) throws IOException {
        loadConfiguration(configFilePath);
    }

    // Constructor for programmatic configuration with job URL
    public ConfigManager(String jobUrl, String userDataFile, String cvPromptFile, String coverLetterPromptFile) throws IOException {
        // Set defaults
        this.templateName = "classic";
        this.templateDirectory = "templates";
        this.outputDirectory = "generation";
        this.outputPdfName = "cv.pdf";
        this.coverLetterPdfName = "cover_letter.pdf";
        this.aiModel = ChatModel.GPT_4_1_MINI;
        this.aiTemperature = 0.3;
        this.saveGeneratedLatex = false;
        this.saveAiResponses = false;
        this.generateCoverLetter = true;
        this.aiRequestDelayMs = 1000;
        this.aiMaxRetries = 3;
        this.aiTimeoutSeconds = 60;
        
        // Set provided values
        this.jobUrl = jobUrl;
        this.userDataFile = userDataFile;
        this.cvPromptFile = cvPromptFile;
        this.coverLetterPromptFile = coverLetterPromptFile;
        this.jobDescriptionFile = ""; // Will be populated from URL
        
        // Load file contents
        loadFileContents();
    }

    private void loadTemplateSettings(Properties properties) {
        this.templateName = properties.getProperty("template.name", "classic");
        this.templateDirectory = properties.getProperty("template.directory", "templates");
    }

    private void loadInputSettings(Properties properties) {
        this.userDataFile = properties.getProperty("input.user.data.file", "userdata.txt");
        this.jobUrl = properties.getProperty("input.job.url", "").trim(); // New property
        this.jobDescriptionFile = properties.getProperty("input.job.description.file", "").trim();
        this.cvPromptFile = properties.getProperty("input.cv.prompt.file", "cv_prompt.txt");
        this.coverLetterPromptFile = properties.getProperty("input.cover.letter.prompt.file", "cover_letter_prompt.txt");
    }

    private void loadOutputSettings(Properties properties) {
        this.outputDirectory = properties.getProperty("output.directory", "generation");
        this.outputPdfName = properties.getProperty("output.pdf.name", "cv.pdf");
        this.coverLetterPdfName = properties.getProperty("output.cover.letter.pdf.name", "cover_letter.pdf");
        this.generateCoverLetter = Boolean.parseBoolean(properties.getProperty("output.generate.cover.letter", "true"));
    }

    private void loadAiSettings(Properties properties) {
        String modelName = properties.getProperty("ai.model", "GPT_4_1_MINI");
        try {
            this.aiModel = ChatModel.of(modelName);
        } catch (IllegalArgumentException e) {
            System.out.println("Warning: Invalid AI model '" + modelName + "', using default GPT_4_1_MINI");
            this.aiModel = ChatModel.GPT_4_1_MINI;
        }

        try {
            this.aiTemperature = Double.parseDouble(properties.getProperty("ai.temperature", "0.3"));
            if (this.aiTemperature < 0.0 || this.aiTemperature > 1.0) {
                System.out.println("Warning: AI temperature should be between 0.0 and 1.0, using 0.3");
                this.aiTemperature = 0.3;
            }
        } catch (NumberFormatException e) {
            System.out.println("Warning: Invalid AI temperature value, using 0.3");
            this.aiTemperature = 0.3;
        }
    }

    private void loadDebugSettings(Properties properties) {
        this.saveGeneratedLatex = Boolean.parseBoolean(properties.getProperty("debug.save.generated.latex", "false"));
        this.saveAiResponses = Boolean.parseBoolean(properties.getProperty("debug.save.ai.responses", "false"));
    }

    private void loadPerformanceSettings(Properties properties) {
        this.aiRequestDelayMs = Integer.parseInt(properties.getProperty("ai.request_delay_ms", "1000"));
        this.aiMaxRetries = Integer.parseInt(properties.getProperty("ai.max_retries", "3"));
        this.aiTimeoutSeconds = Integer.parseInt(properties.getProperty("ai.timeout_seconds", "60"));
    }

    private void loadConfiguration(String configFilePath) throws IOException {
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

        // Load file contents
        loadFileContents();

        System.out.println("Configuration loaded from: " + configPath.toAbsolutePath());
    }

    private void loadFileContents() throws IOException {
        // Load user data content
        Path userDataPath = Paths.get(userDataFile);
        if (Files.exists(userDataPath)) {
            userDataContent = Files.readString(userDataPath);
        } else {
            throw new IOException("User data file not found: " + userDataFile);
        }

        // Load job description content if file exists (skip if using URL)
        if (!jobDescriptionFile.isEmpty() && jobUrl.isEmpty()) {
            Path jobDescPath = Paths.get(jobDescriptionFile);
            if (Files.exists(jobDescPath)) {
                jobDescriptionContent = Files.readString(jobDescPath);
            } else {
                System.out.println("Warning: Job description file not found: " + jobDescriptionFile);
                jobDescriptionContent = "";
            }
        } else if (!jobUrl.isEmpty()) {
            // Job description will be fetched from URL
            jobDescriptionContent = "";
        }

        // Load CV prompt content if file exists
        Path cvPromptPath = Paths.get(cvPromptFile);
        if (Files.exists(cvPromptPath)) {
            cvPromptContent = Files.readString(cvPromptPath);
        } else {
            System.out.println("Warning: CV prompt file not found: " + cvPromptFile);
            cvPromptContent = "";
        }

        // Load cover letter prompt content if file exists
        Path coverLetterPromptPath = Paths.get(coverLetterPromptFile);
        if (Files.exists(coverLetterPromptPath)) {
            coverLetterPromptContent = Files.readString(coverLetterPromptPath);
        } else {
            if (generateCoverLetter) {
                System.out.println("Warning: Cover letter prompt file not found: " + coverLetterPromptFile);
            }
            coverLetterPromptContent = "";
        }
    }

    // Method to set job description content (used when fetching from URL)
    public void setJobDescriptionContent(String jobDescriptionContent) {
        this.jobDescriptionContent = jobDescriptionContent;
    }

    // Additional getters for file contents
    public String getUserDataContent() {
        return userDataContent;
    }

    public String getJobDescriptionContent() {
        return jobDescriptionContent;
    }

    public String getCvPromptContent() {
        return cvPromptContent;
    }

    public String getCoverLetterPromptContent() {
        return coverLetterPromptContent;
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