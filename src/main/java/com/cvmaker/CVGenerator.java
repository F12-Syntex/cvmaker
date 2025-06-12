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

    public CVGenerator(TemplateLoader loader) {
        this.templateLoader = loader;
        this.aiService = new AiService();
    }

    public CVGenerator(TemplateLoader loader, AiService aiService) {
        this.templateLoader = loader;
        this.aiService = aiService;
    }

    public void generateCV(String userDataJsonPath, String templateName, String outputDir, String outputPdfName) throws Exception {
        // 1. Load template
        String templateTex = templateLoader.loadTex(templateName);

        // 2. Load user data
        String userDataString = Files.readString(Paths.get(userDataJsonPath));
        JSONObject userData = new JSONObject(userDataString);

        // 3. Fill template placeholders
        String filledTex = fillTemplate(templateTex, userData);

        // 4. Write .tex file
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path texOutputPath = outputDirPath.resolve("generated_cv.tex");
        Files.writeString(texOutputPath, filledTex);

        // 5. Compile LaTeX to PDF
        compileLatex(outputDirPath, texOutputPath, outputPdfName);
    }

    /**
     * Generate CV from unstructured text using AI
     */
    public void generateCVFromText(String unstructuredTextPath, String templateName, 
                                   String outputDir, String outputPdfName, String jobDescriptionPath) throws Exception {
        
        System.out.println("Starting AI-powered CV generation...");
        
        // 1. Load unstructured user input
        String unstructuredText = Files.readString(Paths.get(unstructuredTextPath));
        System.out.println("Loaded unstructured input text (" + unstructuredText.length() + " characters)");
        
        // 2. Load job description if provided
        String jobDescription = "";
        if (jobDescriptionPath != null && !jobDescriptionPath.trim().isEmpty()) {
            try {
                jobDescription = Files.readString(Paths.get(jobDescriptionPath));
                System.out.println("Loaded job description (" + jobDescription.length() + " characters)");
            } catch (IOException e) {
                System.out.println("Warning: Could not load job description file. Proceeding without it.");
            }
        }
        
        // 3. Load template schema
        JSONObject templateSchema = templateLoader.loadTemplateJson(templateName);
        String schemaString = templateSchema.toString(2);
        System.out.println("Loaded template schema for: " + templateName);
        
        // 4. Use AI to extract and organize data
        System.out.println("Processing with AI (this may take a moment)...");
        JSONObject structuredData = aiService.extractCVData(unstructuredText, schemaString, jobDescription);
        
        // 5. Save the structured data for reference
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path structuredDataPath = outputDirPath.resolve("ai_structured_data.json");
        Files.writeString(structuredDataPath, structuredData.toString(2));
        System.out.println("AI-generated structured data saved to: " + structuredDataPath);
        
        // Debug: Print the structured data
        System.out.println("\n=== AI-Generated Data Debug ===");
        System.out.println(structuredData.toString(2));
        System.out.println("===============================\n");
        
        // 6. Generate CV using the structured data
        System.out.println("Generating PDF from AI-structured data...");
        generateCVFromJsonObject(structuredData, templateName, outputDir, outputPdfName);
        
        System.out.println("AI-powered CV generation completed successfully!");
    }

    /**
     * Generate CV directly from a JSONObject (used internally for AI-generated data)
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

        // Debug: Save the filled template for inspection
        Path outputDirPath = Paths.get(outputDir);
        Files.createDirectories(outputDirPath);
        Path debugTexPath = outputDirPath.resolve("debug_filled_template.tex");
        Files.writeString(debugTexPath, filledTex);
        System.out.println("Debug: Filled template saved to: " + debugTexPath);

        // 3. Write .tex file
        Path texOutputPath = outputDirPath.resolve("ai_generated_cv.tex");
        Files.writeString(texOutputPath, filledTex);
        System.out.println("LaTeX file written to: " + texOutputPath);

        // 4. Compile LaTeX to PDF
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
                System.out.println("Replaced " + placeholder + " with: " + replacement);
            }
        }

        // Handle arrays (objects and strings)
        Pattern sectionPattern = Pattern.compile("\\{\\{#(\\w+)}}([\\s\\S]*?)\\{\\{/\\1}}", Pattern.DOTALL);
        Matcher matcher = sectionPattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String sectionKey = matcher.group(1);
            String sectionTemplate = matcher.group(2);
            
            System.out.println("Processing section: " + sectionKey);
            System.out.println("Section template: " + sectionTemplate.substring(0, Math.min(100, sectionTemplate.length())) + "...");

            if (userData.has(sectionKey) && userData.get(sectionKey) instanceof JSONArray) {
                JSONArray arr = userData.getJSONArray(sectionKey);
                StringBuilder sectionFilled = new StringBuilder();
                
                System.out.println("Found array with " + arr.length() + " items");

                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.get(i);

                    if (item instanceof JSONObject) {
                        // Array of objects: replace keys
                        String itemStr = sectionTemplate;
                        JSONObject itemObj = (JSONObject) item;
                        
                        System.out.println("Processing object item " + i + " with keys: " + itemObj.keySet());
                        
                        for (String k : itemObj.keySet()) {
                            String placeholder = "{{" + k + "}}";
                            String replacement = escapeLatex(itemObj.optString(k, ""));
                            itemStr = itemStr.replace(placeholder, replacement);
                            System.out.println("  Replaced " + placeholder + " with: " + replacement);
                        }
                        sectionFilled.append(itemStr);
                    } else {
                        // Array of strings: replace {{.}}
                        String itemStr = sectionTemplate.replace("{{.}}", escapeLatex(item.toString()));
                        sectionFilled.append(itemStr);
                        System.out.println("  Replaced {{.}} with: " + item.toString());
                    }
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(sectionFilled.toString()));
            } else {
                System.out.println("Section " + sectionKey + " not found in data or not an array");
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
        if (str == null) return "";
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
                texFileName  // Just the filename, not the full path
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
            
        } catch (Exception e) {
            System.err.println("Error during LaTeX compilation: " + e.getMessage());
            throw e;
        }
    }
}