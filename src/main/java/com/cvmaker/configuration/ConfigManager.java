package com.cvmaker.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.cvmaker.CoverLetterService;
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
    private String jobDescriptionFile;
    private String cvPromptFile;

    private String userDataContent;
    private String jobDescriptionContent;
    private String cvPromptContent;

    private String outputDirectory;
    private String outputPdfName;

    private ChatModel aiModel;
    private double aiTemperature;

    private boolean saveGeneratedLatex;
    private boolean saveAiResponses;

    private int aiRequestDelayMs;
    private int aiMaxRetries;
    private int aiTimeoutSeconds;

    private String coverLetterPdfName;
    private CoverLetterService.CoverLetterStyle coverLetterStyle;

    public ConfigManager() throws IOException {
        this(DEFAULT_CONFIG_FILE);
    }

    public ConfigManager(String configFilePath) throws IOException {
        loadConfiguration(configFilePath);
    }

    private void loadTemplateSettings(Properties properties) {
        this.templateName = properties.getProperty("template.name", "classic");
        this.templateDirectory = properties.getProperty("template.directory", "templates");
    }

    private void loadInputSettings(Properties properties) {
        this.userDataFile = properties.getProperty("input.user.data.file", "userdata.txt");
        this.jobDescriptionFile = properties.getProperty("input.job.description.file", "").trim();
        this.cvPromptFile = properties.getProperty("input.cv.prompt.file", "cv_prompt.txt");
    }

    private void loadOutputSettings(Properties properties) {
        this.outputDirectory = properties.getProperty("output.directory", "target");
        this.outputPdfName = properties.getProperty("output.pdf.name", "generated_cv.pdf");
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
        this.saveGeneratedLatex = Boolean.parseBoolean(properties.getProperty("debug.save.generated.latex", "true"));
        this.saveAiResponses = Boolean.parseBoolean(properties.getProperty("debug.save.ai.responses", "false"));
    }

    private void loadPerformanceSettings(Properties properties) {
        this.aiRequestDelayMs = Integer.parseInt(properties.getProperty("ai.request_delay_ms", "1000"));
        this.aiMaxRetries = Integer.parseInt(properties.getProperty("ai.max_retries", "3"));
        this.aiTimeoutSeconds = Integer.parseInt(properties.getProperty("ai.timeout_seconds", "60"));
    }

    private void loadCoverLetterSettings(Properties properties) {
        this.coverLetterPdfName = properties.getProperty("output.cover_letter.pdf.name", "generated_cover_letter.pdf");
        String style = properties.getProperty("cover_letter.style", "MODERN").toUpperCase();
        try {
            this.coverLetterStyle = CoverLetterService.CoverLetterStyle.valueOf(style);
        } catch (IllegalArgumentException e) {
            System.out.println("Warning: Invalid cover letter style '" + style + "', using MODERN");
            this.coverLetterStyle = CoverLetterService.CoverLetterStyle.MODERN;
        }
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
        loadCoverLetterSettings(properties);

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

        // Load job description content if file exists
        if (!jobDescriptionFile.isEmpty()) {
            Path jobDescPath = Paths.get(jobDescriptionFile);
            if (Files.exists(jobDescPath)) {
                jobDescriptionContent = Files.readString(jobDescPath);
            } else {
                System.out.println("Warning: Job description file not found: " + jobDescriptionFile);
                jobDescriptionContent = "";
            }
        }

        // Load CV prompt content if file exists
        Path cvPromptPath = Paths.get(cvPromptFile);
        if (Files.exists(cvPromptPath)) {
            cvPromptContent = Files.readString(cvPromptPath);
        } else {
            System.out.println("Warning: CV prompt file not found: " + cvPromptFile);
            cvPromptContent = "";
        }
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

    // Method to reload file contents
    public void reloadFileContents() throws IOException {
        loadFileContents();
    }
}
