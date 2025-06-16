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

    // Generation Mode
    public GenerationMode getGenerationMode() {
        String mode = properties.getProperty("generation.mode", "ai").toLowerCase();
        
        if ("traditional".equals(mode)) {
            return GenerationMode.TRADITIONAL;
        } else if ("direct_latex".equals(mode) || "directlatex".equals(mode)) {
            return GenerationMode.DIRECT_LATEX;
        } else {
            return GenerationMode.AI; // Default mode
        }
    }

    // Debug Settings
    public boolean shouldSaveStructuredData() {
        return Boolean.parseBoolean(properties.getProperty("debug.save.structured.data", "true"));
    }

    public boolean shouldSaveFilledTemplate() {
        return Boolean.parseBoolean(properties.getProperty("debug.save.filled.template", "true"));
    }

    // Validation methods
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

        // Check if template directory exists
        if (!Files.exists(Paths.get(getTemplateDirectory()))) {
            throw new IOException("Template directory not found: " + getTemplateDirectory());
        }

        // Check if specific template exists
        Path templatePath = Paths.get(getTemplateDirectory()).resolve(getTemplateName());
        if (!Files.exists(templatePath)) {
            throw new IOException("Template not found: " + templatePath);
        }

        // Verify that the template has a template.tex file
        Path templateTexPath = templatePath.resolve("template.tex");
        if (!Files.exists(templateTexPath)) {
            throw new IOException("Template LaTeX file not found: " + templateTexPath);
        }

        // If not using direct LaTeX mode, verify that the template has a template.json file
        if (getGenerationMode() != GenerationMode.DIRECT_LATEX) {
            Path templateJsonPath = templatePath.resolve("template.json");
            if (!Files.exists(templateJsonPath)) {
                throw new IOException("Template JSON schema not found: " + templateJsonPath);
            }
        }

        System.out.println("Configuration validation passed");
    }

    public void printConfiguration() {
        System.out.println("\n=== Configuration Summary ===");
        System.out.println("Generation Mode: " + getGenerationMode());
        System.out.println("Template: " + getTemplateName());
        System.out.println("Template Directory: " + getTemplateDirectory());
        System.out.println("User Data File: " + getUserDataFile());
        System.out.println("Job Description File: " + (getJobDescriptionFile() != null ? getJobDescriptionFile() : "None"));
        System.out.println("Output Directory: " + getOutputDirectory());
        System.out.println("Output PDF Name: " + getOutputPdfName());
        System.out.println("AI Model: " + getAiModel());
        System.out.println("AI Temperature: " + getAiTemperature());
        System.out.println("Save Debug Files: " + (shouldSaveStructuredData() && shouldSaveFilledTemplate()));
        System.out.println("=============================\n");
    }

    public enum GenerationMode {
        TRADITIONAL,  // Use existing JSON data with template filling
        AI,           // Use AI to extract structured data from text, then fill template
        DIRECT_LATEX  // Use AI to generate complete LaTeX document directly
    }
}