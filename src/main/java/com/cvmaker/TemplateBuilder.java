package com.cvmaker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.json.JSONObject;

/**
 * AI-powered template builder that can create, enhance, and analyze LaTeX CV templates
 */
public class TemplateBuilder {

    private final AiService aiService;
    private final Path templatesRoot;
    private ConfigManager config;

    public TemplateBuilder(AiService aiService, Path templatesRoot) {
        this.aiService = aiService;
        this.templatesRoot = templatesRoot;
        this.config = loadConfigSafely();
    }

    public TemplateBuilder(AiService aiService, Path templatesRoot, ConfigManager config) {
        this.aiService = aiService;
        this.templatesRoot = templatesRoot;
        this.config = config;
    }

    private ConfigManager loadConfigSafely() {
        try {
            return new ConfigManager();
        } catch (Exception e) {
            System.out.println("Warning: Could not load configuration for template builder");
            return null;
        }
    }

    /**
     * Create a completely new template using AI
     *
     * @param templateName Name of the new template
     * @param requirements Description of template requirements
     * @param style Style preference (modern, classic, minimal, etc.)
     * @param sampleData Optional sample data to structure the template around
     * @return Path to the created template directory
     * @throws Exception If creation fails
     */
    public Path createTemplate(String templateName, String requirements, String style, JSONObject sampleData) throws Exception {
        System.out.println("Creating new AI-generated template: " + templateName);
        System.out.println("Requirements: " + requirements);
        System.out.println("Style: " + style);

        // 1. Generate template schema if sample data not provided
        JSONObject schema = sampleData;
        if (schema == null) {
            System.out.println("Generating template schema...");
            schema = aiService.generateTemplateSchema(requirements, true);
            System.out.println("Schema generated successfully");
        }

        // 2. Generate LaTeX template
        System.out.println("Generating LaTeX template...");
        String latexTemplate = aiService.generateLatexTemplate(requirements, schema, style);
        System.out.println("LaTeX template generated successfully");

        // 3. Create template directory structure
        Path templateDir = templatesRoot.resolve(templateName);
        Files.createDirectories(templateDir);

        // 4. Save the LaTeX template
        Path texPath = templateDir.resolve("template.tex");
        Files.writeString(texPath, latexTemplate, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("LaTeX template saved to: " + texPath);

        // 5. Save the schema
        Path schemaPath = templateDir.resolve("template.json");
        Files.writeString(schemaPath, schema.toString(2), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Template schema saved to: " + schemaPath);

        // 6. Create a README for the template
        String readme = generateTemplateReadme(templateName, requirements, style, schema);
        Path readmePath = templateDir.resolve("README.md");
        Files.writeString(readmePath, readme, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Template documentation saved to: " + readmePath);

        // 7. Validate the template by testing compilation (if possible)
        if (config != null && config.shouldSaveFilledTemplate()) {
            try {
                validateTemplate(templateDir, schema);
            } catch (Exception e) {
                System.out.println("Warning: Template validation failed: " + e.getMessage());
                System.out.println("Template created but may need manual review");
            }
        }

        System.out.println("Template creation completed: " + templateDir);
        return templateDir;
    }

    /**
     * Enhance an existing template using AI
     *
     * @param templateName Name of existing template
     * @param enhancementRequests What improvements to make
     * @param backupOriginal Whether to backup the original template
     * @return Path to the enhanced template
     * @throws Exception If enhancement fails
     */
    public Path enhanceTemplate(String templateName, String enhancementRequests, boolean backupOriginal) throws Exception {
        System.out.println("Enhancing template: " + templateName);
        System.out.println("Enhancements requested: " + enhancementRequests);

        Path templateDir = templatesRoot.resolve(templateName);
        if (!Files.exists(templateDir)) {
            throw new IOException("Template not found: " + templateName);
        }

        // 1. Load existing template and schema
        Path texPath = templateDir.resolve("template.tex");
        Path schemaPath = templateDir.resolve("template.json");

        if (!Files.exists(texPath)) {
            throw new IOException("Template LaTeX file not found: " + texPath);
        }

        String existingTemplate = Files.readString(texPath);
        JSONObject schema = null;

        if (Files.exists(schemaPath)) {
            String schemaString = Files.readString(schemaPath);
            schema = new JSONObject(schemaString);
        }

        // 2. Backup original if requested
        if (backupOriginal) {
            Path backupPath = templateDir.resolve("template.tex.backup." + System.currentTimeMillis());
            Files.copy(texPath, backupPath);
            System.out.println("Original template backed up to: " + backupPath);
        }

        // 3. Enhance the template
        System.out.println("Processing enhancements with AI...");
        String enhancedTemplate = aiService.enhanceLatexTemplate(existingTemplate, enhancementRequests, schema);
        System.out.println("Template enhancement completed");

        // 4. Save the enhanced template
        Files.writeString(texPath, enhancedTemplate, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Enhanced template saved to: " + texPath);

        // 5. Update README with enhancement history
        updateTemplateHistory(templateDir, "Enhanced: " + enhancementRequests);

        System.out.println("Template enhancement completed: " + templateDir);
        return templateDir;
    }

    /**
     * Analyze an existing template and provide improvement suggestions
     *
     * @param templateName Name of template to analyze
     * @param analysisType Type of analysis to perform
     * @return Analysis results and suggestions
     * @throws Exception If analysis fails
     */
    public String analyzeTemplate(String templateName, String analysisType) throws Exception {
        System.out.println("Analyzing template: " + templateName);
        System.out.println("Analysis type: " + analysisType);

        Path templateDir = templatesRoot.resolve(templateName);
        Path texPath = templateDir.resolve("template.tex");

        if (!Files.exists(texPath)) {
            throw new IOException("Template not found: " + texPath);
        }

        String template = Files.readString(texPath);
        String analysis = aiService.analyzeLatexTemplate(template, analysisType);

        // Save analysis results
        if (config != null && config.shouldSaveStructuredData()) {
            Path analysisPath = templateDir.resolve("analysis_" + System.currentTimeMillis() + ".md");
            Files.writeString(analysisPath, analysis);
            System.out.println("Analysis results saved to: " + analysisPath);
        }

        System.out.println("Template analysis completed");
        return analysis;
    }

    /**
     * Generate a custom template based on user data and job requirements
     *
     * @param templateName Name for the new template
     * @param userData User's CV data
     * @param jobDescription Target job description
     * @param stylePreferences Style preferences
     * @return Path to created template
     * @throws Exception If creation fails
     */
    public Path createCustomTemplate(String templateName, JSONObject userData, String jobDescription, String stylePreferences) throws Exception {
        System.out.println("Creating custom template for specific user and job");

        // Build requirements based on user data and job
        StringBuilder requirements = new StringBuilder();
        requirements.append("Create a CV template optimized for the following profile:\n\n");

        // Analyze user data to understand their background
        if (userData.has("summary") || userData.has("objective")) {
            String summary = userData.optString("summary", userData.optString("objective", ""));
            requirements.append("Professional Summary: ").append(summary).append("\n\n");
        }

        if (userData.has("experience")) {
            requirements.append("Experience Level: Professional with work history\n");
        }

        if (userData.has("education")) {
            requirements.append("Education: Include education section\n");
        }

        if (userData.has("skills")) {
            requirements.append("Skills: Technical and professional skills section needed\n");
        }

        if (jobDescription != null && !jobDescription.trim().isEmpty()) {
            requirements.append("Target Role: ").append(jobDescription.substring(0, Math.min(200, jobDescription.length()))).append("...\n\n");
            requirements.append("Template should emphasize relevant experience and skills for this role.\n");
        }

        return createTemplate(templateName, requirements.toString(), stylePreferences, userData);
    }

    /**
     * Batch create multiple template variations
     *
     * @param baseName Base name for templates
     * @param requirements Common requirements
     * @param styles Array of different styles to create
     * @param sampleData Sample data structure
     * @return Array of paths to created templates
     * @throws Exception If creation fails
     */
    public Path[] createTemplateVariations(String baseName, String requirements, String[] styles, JSONObject sampleData) throws Exception {
        System.out.println("Creating " + styles.length + " template variations");

        Path[] results = new Path[styles.length];

        for (int i = 0; i < styles.length; i++) {
            String templateName = baseName + "_" + styles[i].toLowerCase().replace(" ", "_");
            System.out.println("Creating variation " + (i + 1) + "/" + styles.length + ": " + templateName);

            results[i] = createTemplate(templateName, requirements, styles[i], sampleData);

            // Small delay between generations to avoid rate limiting
            if (i < styles.length - 1) {
                Thread.sleep(1000);
            }
        }

        System.out.println("All template variations created successfully");
        return results;
    }

    /**
     * Validate a template by testing it with sample data
     */
    private void validateTemplate(Path templateDir, JSONObject sampleData) throws Exception {
        System.out.println("Validating template...");

        // Create a minimal test to see if template structure is valid
        Path texPath = templateDir.resolve("template.tex");
        String template = Files.readString(texPath);

        // Basic validation checks
        if (!template.contains("\\documentclass")) {
            throw new Exception("Template missing document class declaration");
        }

        if (!template.contains("\\begin{document}")) {
            throw new Exception("Template missing document begin");
        }

        if (!template.contains("\\end{document}")) {
            throw new Exception("Template missing document end");
        }

        // Check if placeholders exist
        if (!template.contains("{{")) {
            System.out.println("Warning: Template may not contain placeholders for data substitution");
        }

        System.out.println("Template validation passed");
    }

    /**
     * Generate README documentation for a template
     */
    private String generateTemplateReadme(String templateName, String requirements, String style, JSONObject schema) {
        StringBuilder readme = new StringBuilder();

        readme.append("# ").append(templateName.toUpperCase()).append(" Template\n\n");
        readme.append("**AI-Generated CV Template**\n\n");
        readme.append("## Description\n");
        readme.append("This template was automatically generated using AI based on the following requirements:\n\n");
        readme.append(requirements).append("\n\n");
        readme.append("**Style:** ").append(style).append("\n\n");

        readme.append("## Template Structure\n");
        readme.append("This template expects data in the following JSON structure:\n\n");
        readme.append("```json\n");
        readme.append(schema.toString(2));
        readme.append("\n```\n\n");

        readme.append("## Usage\n");
        readme.append("1. Place your data in the format above\n");
        readme.append("2. Use the CVGenerator to process your data with this template\n");
        readme.append("3. The template will automatically fill placeholders with your information\n\n");

        readme.append("## Files\n");
        readme.append("- `template.tex` - Main LaTeX template file\n");
        readme.append("- `template.json` - JSON schema defining expected data structure\n");
        readme.append("- `README.md` - This documentation file\n\n");

        readme.append("## Customization\n");
        readme.append("You can enhance this template using the TemplateBuilder's enhanceTemplate method.\n\n");

        readme.append("## Generated\n");
        readme.append("- **Created:** ").append(java.time.LocalDateTime.now().toString()).append("\n");
        readme.append("- **AI Model:** GPT-4\n");
        readme.append("- **Generator:** CVMaker AI Template Builder\n");

        return readme.toString();
    }

    /**
     * Update template history in README
     */
    private void updateTemplateHistory(Path templateDir, String change) throws IOException {
        Path readmePath = templateDir.resolve("README.md");
        
        if (Files.exists(readmePath)) {
            String readme = Files.readString(readmePath);
            
            // Add to history section or create it
            if (readme.contains("## History")) {
                readme = readme.replace("## History\n", 
                    "## History\n- " + java.time.LocalDateTime.now() + ": " + change + "\n");
            } else {
                readme += "\n## History\n- " + java.time.LocalDateTime.now() + ": " + change + "\n";
            }
            
            Files.writeString(readmePath, readme, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /**
     * Get AI service instance
     */
    public AiService getAiService() {
        return aiService;
    }

    /**
     * Get templates root path
     */
    public Path getTemplatesRoot() {
        return templatesRoot;
    }

    /**
     * Set configuration manager
     */
    public void setConfig(ConfigManager config) {
        this.config = config;
    }

    /**
     * Get current configuration
     */
    public ConfigManager getConfig() {
        return config;
    }
}