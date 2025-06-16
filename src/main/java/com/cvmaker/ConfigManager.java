package com.cvmaker;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.openai.models.ChatModel;

public class ConfigManager {

    private static final String DEFAULT_CONFIG_FILE = "config.properties";
    private final Properties properties;

    public ConfigManager() throws IOException {
        this(DEFAULT_CONFIG_FILE);
    }

    public ConfigManager(String configFilePath) throws IOException {
        this.properties = new Properties();
        loadConfiguration(configFilePath);
    }

    private void loadConfiguration(String configFilePath) throws IOException {
        Path configPath = Paths.get(configFilePath);

        if (!Files.exists(configPath)) {
            throw new IOException("Configuration file not found: " + configFilePath);
        }

        try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
            properties.load(fis);
        }

        System.out.println("Configuration loaded from: " + configPath.toAbsolutePath());
    }

    // Template Settings
    public String getTemplateName() {
        return properties.getProperty("template.name", "classic");
    }

    public String getTemplateDirectory() {
        return properties.getProperty("template.directory", "templates");
    }

    // Input Files
    public String getUserDataFile() {
        return properties.getProperty("input.user.data.file", "userdata.txt");
    }

    public String getJobDescriptionFile() {
        String jobDesc = properties.getProperty("input.job.description.file", "").trim();
        return jobDesc.isEmpty() ? null : jobDesc;
    }

    // Output Settings
    public String getOutputDirectory() {
        return properties.getProperty("output.directory", "target");
    }

    public String getOutputPdfName() {
        return properties.getProperty("output.pdf.name", "generated_cv.pdf");
    }

    // AI Settings
    public ChatModel getAiModel() {
        String modelName = properties.getProperty("ai.model", "GPT_4_1_MINI");
        try {
            return ChatModel.of(modelName);
        } catch (IllegalArgumentException e) {
            System.out.println("Warning: Invalid AI model '" + modelName + "', using default GPT_4_1_MINI");
            return ChatModel.GPT_4_1_MINI;
        }
    }

    public double getAiTemperature() {
        String tempStr = properties.getProperty("ai.temperature", "0.3");
        try {
            double temp = Double.parseDouble(tempStr);
            if (temp < 0.0 || temp > 1.0) {
                System.out.println("Warning: AI temperature should be between 0.0 and 1.0, using 0.3");
                return 0.3;
            }
            return temp;
        } catch (NumberFormatException e) {
            System.out.println("Warning: Invalid AI temperature value, using 0.3");
            return 0.3;
        }
    }

    // Generation Mode - Simplified to only support AI generation
    public GenerationMode getGenerationMode() {
        String mode = properties.getProperty("generation.mode", "ai").toLowerCase();

        if ("traditional".equals(mode)) {
            System.out.println("Warning: Traditional mode is deprecated. Using AI mode instead.");
            return GenerationMode.AI;
        } else {
            return GenerationMode.AI; // Only AI mode supported now
        }
    }

    // Debug Settings - Simplified
    public boolean shouldSaveDebugFiles() {
        return Boolean.parseBoolean(properties.getProperty("debug.save.files", "true"));
    }

    // Validation methods - Simplified for AI-only mode
    public void validateConfiguration() throws IOException {
        // Check if user data file exists
        if (!Files.exists(Paths.get(getUserDataFile()))) {
            throw new IOException("User data file not found: " + getUserDataFile());
        }

        // Check if job description file exists (if specified)
        String jobDescFile = getJobDescriptionFile();
        if (jobDescFile != null && !Files.exists(Paths.get(jobDescFile))) {
            throw new IOException("Job description file not found: " + jobDescFile);
        }

        // Check if template directory exists (optional - for reference templates)
        Path templateDir = Paths.get(getTemplateDirectory());
        if (!Files.exists(templateDir)) {
            System.out.println("Warning: Template directory not found: " + templateDir + " (will generate without reference template)");
        }

        // Check if specific template exists (optional)
        String templateName = getTemplateName();
        if (templateName != null && !templateName.isEmpty() && Files.exists(templateDir)) {
            Path templatePath = templateDir.resolve(templateName);
            if (!Files.exists(templatePath)) {
                System.out.println("Warning: Template not found: " + templatePath + " (will generate without reference template)");
            } else {
                // Check if template has a template.tex file (optional reference)
                Path templateTexPath = templatePath.resolve("template.tex");
                if (!Files.exists(templateTexPath)) {
                    System.out.println("Warning: Template LaTeX file not found: " + templateTexPath + " (will generate without reference)");
                }
            }
        }

        System.out.println("Configuration validation passed");
    }

    public void printConfiguration() {
        System.out.println("\n=== Configuration Summary ===");
        System.out.println("Generation Mode: AI-Powered LaTeX Generation");
        System.out.println("Template (Reference): " + getTemplateName());
        System.out.println("Template Directory: " + getTemplateDirectory());
        System.out.println("User Data File: " + getUserDataFile());
        System.out.println("Job Description File: " + (getJobDescriptionFile() != null ? getJobDescriptionFile() : "None"));
        System.out.println("Output Directory: " + getOutputDirectory());
        System.out.println("Output PDF Name: " + getOutputPdfName());
        System.out.println("AI Model: " + getAiModel());
        System.out.println("AI Temperature: " + getAiTemperature());
        System.out.println("Save Debug Files: " + shouldSaveDebugFiles());
        System.out.println("=============================\n");
    }

    public enum GenerationMode {
        AI  // Only AI mode - generates complete LaTeX directly
    }

    // Cover Letter Settings
    public String getCoverLetterPdfName() {
        return properties.getProperty("output.cover_letter.pdf.name", "generated_cover_letter.pdf");
    }

    public CoverLetterService.CoverLetterStyle getCoverLetterStyle() {
        String style = properties.getProperty("cover_letter.style", "MODERN").toUpperCase();
        try {
            return CoverLetterService.CoverLetterStyle.valueOf(style);
        } catch (IllegalArgumentException e) {
            System.out.println("Warning: Invalid cover letter style '" + style + "', using MODERN");
            return CoverLetterService.CoverLetterStyle.MODERN;
        }
    }
}
