package com.cvmaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

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
        } catch (Exception e) {
            System.out.println("Warning: Could not load configuration, using defaults for debug settings");
            return null;
        }
    }

    public void generateCV(String userDataJsonPath, String templateName, String outputDir, String outputPdfName) throws Exception {
        System.out.println("Starting traditional CV generation...");

        // 1. Load template
        System.out.println("Loading template: " + templateName);
        String templateTex = templateLoader.loadTex(templateName);

        // 2. Load user data
        System.out.println("Loading user data from: " + userDataJsonPath);
        String userDataString = Files.readString(Paths.get(userDataJsonPath));
        JSONObject userData = new JSONObject(userDataString);

        // 3. Fill template placeholders
        System.out.println("Filling template with user data...");
        String filledTex = fillTemplate(templateTex, userData);

        // 4. Write .tex file
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path texOutputPath = outputDirPath.resolve("generated_cv.tex");
        Files.writeString(texOutputPath, filledTex);
        System.out.println("LaTeX file written to: " + texOutputPath);

        // 5. Optional: Save debug template
        if (config != null && config.shouldSaveFilledTemplate()) {
            Path debugTexPath = outputDirPath.resolve("debug_traditional_template.tex");
            Files.writeString(debugTexPath, filledTex);
            System.out.println("Debug: Filled template saved to: " + debugTexPath);
        }

        // 6. Compile LaTeX to PDF
        System.out.println("Compiling LaTeX to PDF...");
        compileLatex(outputDirPath, texOutputPath, outputPdfName);
        System.out.println("Traditional CV generation completed successfully!");
    }

    public void generateCVFromText(String unstructuredTextPath, String templateName,
            String outputDir, String outputPdfName, String jobDescriptionPath) throws Exception {

        System.out.println("Starting AI-powered CV generation...");

        // 1. Load unstructured user input
        System.out.println("Loading unstructured input text from: " + unstructuredTextPath);
        String unstructuredText = Files.readString(Paths.get(unstructuredTextPath));

        // 2. Load job description if provided
        String jobDescription = "";
        if (jobDescriptionPath != null && !jobDescriptionPath.trim().isEmpty()) {
            try {
                System.out.println("Loading job description from: " + jobDescriptionPath);
                jobDescription = Files.readString(Paths.get(jobDescriptionPath));
            } catch (IOException e) {
                System.out.println("Warning: Could not load job description file. Proceeding without it.");
            }
        }

        // 3. Load template as reference (if available)
        String referenceTemplate = null;
        if (templateName != null && !templateName.isEmpty()) {
            try {
                System.out.println("Loading reference template: " + templateName);
                referenceTemplate = templateLoader.loadTex(templateName);
            } catch (IOException e) {
                System.out.println("Warning: Could not load template. Proceeding with AI-generated style.");
            }
        }

        // 4. Generate LaTeX directly using AI
        System.out.println("Generating LaTeX with AI (this may take a moment)...");
        String generatedLatex = aiService.generateDirectLatexCV(unstructuredText, referenceTemplate, jobDescription);

        // 5. Create output directory
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);

        // 6. Save the LaTeX file
        Path texOutputPath = outputDirPath.resolve("generated_cv.tex");
        Files.writeString(texOutputPath, generatedLatex);
        System.out.println("LaTeX file written to: " + texOutputPath);

        // 7. Compile to PDF
        System.out.println("Compiling LaTeX to PDF...");
        compileLatex(outputDirPath, texOutputPath, outputPdfName);

        System.out.println("CV generation completed successfully!");
    }

    /**
     * Generate CV directly from a JSONObject (used internally for AI-generated
     * data)
     */
    private void generateCVFromJsonObject(JSONObject userData, String templateName, String outputDir, String outputPdfName) throws Exception {
        System.out.println("Loading template: " + templateName);

        // 1. Load template
        String templateTex = templateLoader.loadTex(templateName);
        System.out.println("Template loaded successfully");

        // 2. Fill template placeholders
        System.out.println("Filling template with user data...");
        String filledTex = fillTemplate(templateTex, userData);
        System.out.println("Template filled successfully");

        // 3. Create output directory
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);

        // 4. Optional: Save the filled template for inspection (if enabled in config)
        if (config != null && config.shouldSaveFilledTemplate()) {
            Path debugTexPath = outputDirPath.resolve("debug_ai_filled_template.tex");
            Files.writeString(debugTexPath, filledTex);
            System.out.println("Debug: Filled template saved to: " + debugTexPath);
        }

        // 5. Write .tex file
        Path texOutputPath = outputDirPath.resolve("ai_generated_cv.tex");
        Files.writeString(texOutputPath, filledTex);
        System.out.println("LaTeX file written to: " + texOutputPath);

        // 6. Compile LaTeX to PDF
        System.out.println("Compiling LaTeX to PDF...");
        compileLatex(outputDirPath, texOutputPath, outputPdfName);
        System.out.println("PDF compilation completed");
    }

    private String fillTemplate(String templateTex, JSONObject userData) {
        String result = templateTex;

        System.out.println("Starting template filling...");
        System.out.println("Available keys in userData: " + userData.keySet());

        // Replace all string fields (flat)
        for (String key : userData.keySet()) {
            Object value = userData.get(key);
            if (value instanceof String) {
                String placeholder = "{{" + key + "}}";
                String replacement = escapeLatex((String) value);
                result = result.replace(placeholder, replacement);

                // Only log replacements if debug is enabled
                if (config != null && config.shouldSaveFilledTemplate()) {
                    System.out.println("Replaced " + placeholder + " with: " + replacement);
                }
            }
        }

        // Handle arrays (objects and strings)
        Pattern sectionPattern = Pattern.compile("\\{\\{#(\\w+)}}([\\s\\S]*?)\\{\\{/\\1}}", Pattern.DOTALL);
        Matcher matcher = sectionPattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String sectionKey = matcher.group(1);
            String sectionTemplate = matcher.group(2);

            if (config != null && config.shouldSaveFilledTemplate()) {
                System.out.println("Processing section: " + sectionKey);
                System.out.println("Section template: " + sectionTemplate.substring(0, Math.min(100, sectionTemplate.length())) + "...");
            }

            if (userData.has(sectionKey) && userData.get(sectionKey) instanceof JSONArray) {
                JSONArray arr = userData.getJSONArray(sectionKey);
                StringBuilder sectionFilled = new StringBuilder();

                if (config != null && config.shouldSaveFilledTemplate()) {
                    System.out.println("Found array with " + arr.length() + " items");
                }

                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);

                    if (item instanceof JSONObject) {
                        // Array of objects: replace keys
                        String itemStr = sectionTemplate;
                        JSONObject itemObj = (JSONObject) item;

                        if (config != null && config.shouldSaveFilledTemplate()) {
                            System.out.println("Processing object item " + i + " with keys: " + itemObj.keySet());
                        }

                        for (String k : itemObj.keySet()) {
                            String placeholder = "{{" + k + "}}";
                            String replacement = escapeLatex(itemObj.optString(k, ""));
                            itemStr = itemStr.replace(placeholder, replacement);

                            if (config != null && config.shouldSaveFilledTemplate()) {
                                System.out.println("  Replaced " + placeholder + " with: " + replacement);
                            }
                        }
                        sectionFilled.append(itemStr);
                    } else {
                        // Array of strings: replace {{.}}
                        String itemStr = sectionTemplate.replace("{{.}}", escapeLatex(item.toString()));
                        sectionFilled.append(itemStr);

                        if (config != null && config.shouldSaveFilledTemplate()) {
                            System.out.println("  Replaced {{.}} with: " + item.toString());
                        }
                    }
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(sectionFilled.toString()));
            } else {
                if (config != null && config.shouldSaveFilledTemplate()) {
                    System.out.println("Section " + sectionKey + " not found in data or not an array");
                }
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);

        // Final pass: remove any remaining placeholders
        String finalResult = sb.toString();

        // Remove any unreplaced placeholders to prevent LaTeX errors
        finalResult = finalResult.replaceAll("\\{\\{\\w+\\}\\}", "");
        finalResult = finalResult.replaceAll("\\{\\{\\{\\w+\\}\\}\\}", "");

        System.out.println("Template filling completed");

        return finalResult;
    }

    private String escapeLatex(String str) {
        if (str == null) {
            return "";
        }
        // Basic escaping for LaTeX special chars
        return str.replace("\\", "\\textbackslash{}")
                .replace("$", "\\$")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}");
    }

    private void compileLatex(Path dir, Path texFile, String outputPdfName) throws IOException, InterruptedException {
        System.out.println("Starting LaTeX compilation...");
        System.out.println("Working directory: " + dir.toAbsolutePath());
        System.out.println("TeX file: " + texFile.toAbsolutePath());

        // Get just the filename for pdflatex (without full path)
        String texFileName = texFile.getFileName().toString();

        // Run pdflatex from the output directory with just the filename
        ProcessBuilder pb = new ProcessBuilder(
                "pdflatex",
                "-interaction=nonstopmode",
                texFileName // Just the filename, not the full path
        );
        pb.redirectErrorStream(true);
        pb.directory(dir.toFile());  // Set working directory to output directory

        try {
            Process proc = pb.start();

            // Read the output to see what's happening
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            boolean hasError = false;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (line.toLowerCase().contains("error") && !line.toLowerCase().contains("rerun")) {
                    hasError = true;
                }
                if (line.toLowerCase().contains("fatal")) {
                    hasError = true;
                }
            }

            int exitCode = proc.waitFor();

            // Check if PDF was actually generated despite errors
            String pdfFileName = texFileName.replace(".tex", ".pdf");
            Path pdfPath = dir.resolve(pdfFileName);
            boolean pdfExists = Files.exists(pdfPath);

            if (exitCode != 0 && !pdfExists) {
                System.err.println("LaTeX compilation failed with exit code: " + exitCode);
                System.err.println("LaTeX output:");
                System.err.println(output.toString());

                if (output.toString().contains("File") && output.toString().contains("not found")) {
                    throw new RuntimeException("LaTeX template file issue. Please check your template.tex file exists and is valid.");
                }

                throw new RuntimeException("LaTeX compilation failed - check LaTeX syntax and dependencies");
            }

            if (pdfExists) {
                System.out.println("LaTeX compilation successful (PDF generated despite warnings)");

                // Move the PDF to the desired name if needed
                if (!pdfFileName.equals(outputPdfName)) {
                    Files.move(pdfPath, dir.resolve(outputPdfName), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("PDF renamed to: " + outputPdfName);
                } else {
                    System.out.println("PDF generated: " + pdfPath.toAbsolutePath());
                }
            } else {
                throw new RuntimeException("PDF file was not generated - check LaTeX template and data");
            }

            // Optional: Show compilation details if debug is enabled
            if (config != null && config.shouldSaveFilledTemplate() && exitCode == 0) {
                System.out.println("LaTeX compilation details available in log files");
            }

        } catch (Exception e) {
            System.err.println("Error during LaTeX compilation: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Get the current configuration manager
     *
     * @return The configuration manager instance, or null if not loaded
     */
    public ConfigManager getConfig() {
        return config;
    }

    /**
     * Set a new configuration manager
     *
     * @param config The new configuration manager
     */
    public void setConfig(ConfigManager config) {
        this.config = config;
    }

    /**
     * Generate CV using configuration settings
     *
     * @throws Exception If generation fails
     */
    public void generateCVFromConfig() throws Exception {
        if (config == null) {
            throw new IllegalStateException("No configuration loaded. Please provide a ConfigManager instance.");
        }

        config.validateConfiguration();

        if (config.getGenerationMode() == ConfigManager.GenerationMode.TRADITIONAL) {
            generateCV(
                    config.getUserDataFile(),
                    config.getTemplateName(),
                    config.getOutputDirectory(),
                    config.getOutputPdfName()
            );
        } else {
            generateCVFromText(
                    config.getUserDataFile(),
                    config.getTemplateName(),
                    config.getOutputDirectory(),
                    config.getOutputPdfName(),
                    config.getJobDescriptionFile()
            );
        }
    }
}
