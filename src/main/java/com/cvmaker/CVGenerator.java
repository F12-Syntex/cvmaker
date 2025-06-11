package com.cvmaker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

public class CVGenerator {
    
    private static final String TEMPLATE_FILE = "cv_template.tex";
    private static final String OUTPUT_FILE = "generated_cv.tex";
    
    public void generateCV(CVData cvData) throws IOException {
        // Read template
        String template = readTemplate();
        
        // Create placeholder map
        Map<String, String> placeholders = createPlaceholderMap(cvData);
        
        // Replace all placeholders dynamically
        String populatedCV = replacePlaceholders(template, placeholders);
        
        // Write generated LaTeX file
        writeGeneratedFile(populatedCV);
        
        // Compile to PDF
        compileToPDF();
    }
    
    private String readTemplate() throws IOException {
        return new String(Files.readAllBytes(Paths.get(TEMPLATE_FILE)));
    }
    
    private Map<String, String> createPlaceholderMap(CVData cvData) {
        Map<String, String> placeholders = new HashMap<>();
        
        // Personal information - null-safe
        placeholders.put("FULL_NAME", safeString(cvData.getFullName()));
        placeholders.put("EMAIL", safeString(cvData.getEmail()));
        placeholders.put("PHONE", safeString(cvData.getPhone()));
        placeholders.put("LOCATION", safeString(cvData.getLocation()));
        placeholders.put("LINKEDIN", safeString(cvData.getLinkedin()));
        
        // Dynamic sections
        placeholders.put("EXPERIENCE_SECTION", generateExperienceSection(cvData));
        placeholders.put("EDUCATION_SECTION", generateEducationSection(cvData));
        placeholders.put("SKILLS_SECTION", generateSkillsSection(cvData));
        placeholders.put("CERTIFICATIONS_SECTION", generateCertificationsSection(cvData));
        
        // Add any additional dynamic content that might be in CVData
        addCustomPlaceholders(placeholders, cvData);
        
        return placeholders;
    }
    
    private String replacePlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        
        // Replace all known placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        
        // Find and handle any remaining placeholders by removing them or setting to empty
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(result);
        
        while (matcher.find()) {
            String remainingPlaceholder = matcher.group(0);
            String placeholderName = matcher.group(1);
            
            System.out.println("Warning: Unhandled placeholder found: " + placeholderName);
            // Replace with empty string or keep as-is for debugging
            result = result.replace(remainingPlaceholder, "");
        }
        
        return result;
    }
    
    private void addCustomPlaceholders(Map<String, String> placeholders, CVData cvData) {
    }
    
    private String generateExperienceSection(CVData cvData) {
        if (cvData.getExperiences() == null || cvData.getExperiences().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (CVData.Experience exp : cvData.getExperiences()) {
            section.append("\\job{").append(safeString(exp.getJobTitle())).append("}{")
                   .append(safeString(exp.getCompany())).append("}{")
                   .append(safeString(exp.getDuration())).append("}{\n");
            
            if (exp.getResponsibilities() != null && !exp.getResponsibilities().isEmpty()) {
                section.append("\\begin{itemize}[leftmargin=12pt, itemsep=1pt, parsep=0pt]\n");
                for (String responsibility : exp.getResponsibilities()) {
                    section.append("    \\item ").append(escapeLatex(safeString(responsibility))).append("\n");
                }
                section.append("\\end{itemize}\n");
            }
            section.append("}\n\n");
        }
        
        return section.toString();
    }
    
    private String generateEducationSection(CVData cvData) {
        if (cvData.getEducations() == null || cvData.getEducations().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (CVData.Education edu : cvData.getEducations()) {
            section.append("\\education{").append(safeString(edu.getDegree())).append("}{")
                   .append(safeString(edu.getInstitution())).append("}{")
                   .append(safeString(edu.getDetails())).append("}\n\n");
        }
        
        section.append("\\vspace{8pt}\n");
        return section.toString();
    }
    
    private String generateSkillsSection(CVData cvData) {
        if (cvData.getSkillCategories() == null || cvData.getSkillCategories().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (CVData.SkillCategory category : cvData.getSkillCategories()) {
            section.append("\\textbf{").append(safeString(category.getCategoryName())).append(":} ")
                   .append(safeString(category.getSkills())).append("\\\\\n");
        }
        
        section.append("\n\\vspace{8pt}\n");
        return section.toString();
    }
    
    private String generateCertificationsSection(CVData cvData) {
        if (cvData.getCertifications() == null || cvData.getCertifications().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (int i = 0; i < cvData.getCertifications().size(); i++) {
            section.append(safeString(cvData.getCertifications().get(i)));
            if (i < cvData.getCertifications().size() - 1) {
                section.append(" $|$ ");
            }
        }
        
        section.append("\n");
        return section.toString();
    }
    
    // Utility methods
    private String safeString(String str) {
        return str != null ? str : "";
    }
    
    private String escapeLatex(String text) {
        if (text == null) return "";
        
        return text.replace("\\", "\\textbackslash{}")
                  .replace("%", "\\%")
                  .replace("&", "\\&")
                  .replace("#", "\\#")
                  .replace("$", "\\$")
                  .replace("_", "\\_")
                  .replace("^", "\\textasciicircum{}")
                  .replace("{", "\\{")
                  .replace("}", "\\}")
                  .replace("~", "\\textasciitilde{}");
    }
    
    private void writeGeneratedFile(String content) throws IOException {
        try (FileWriter writer = new FileWriter(OUTPUT_FILE)) {
            writer.write(content);
        }
    }
    
    private void compileToPDF() throws IOException {
        if (!isLatexInstalled()) {
            System.out.println("LaTeX not found. Generated file: " + OUTPUT_FILE);
            return;
        }
        
        try {
            CommandLine cmdLine = new CommandLine("pdflatex");
            cmdLine.addArgument("-interaction=nonstopmode");
            cmdLine.addArgument(OUTPUT_FILE);
            
            DefaultExecutor executor = new DefaultExecutor();
            executor.setWorkingDirectory(new File("."));
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
            executor.setStreamHandler(streamHandler);
            
            int exitCode = executor.execute(cmdLine);
            
            if (exitCode == 0) {
                System.out.println("PDF compiled successfully: generated_cv.pdf");
                cleanupLatexFiles();
            } else {
                System.err.println("LaTeX compilation failed");
                System.err.println("Output: " + outputStream.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error compiling PDF: " + e.getMessage());
        }
    }
    
    private boolean isLatexInstalled() {
        try {
            CommandLine cmdLine = new CommandLine("pdflatex");
            cmdLine.addArgument("--version");
            DefaultExecutor executor = new DefaultExecutor();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
            executor.setStreamHandler(streamHandler);
            executor.execute(cmdLine);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void cleanupLatexFiles() {
        String[] extensions = {".aux", ".log", ".out"};
        String baseName = OUTPUT_FILE.replace(".tex", "");
        for (String ext : extensions) {
            File file = new File(baseName + ext);
            if (file.exists()) {
                file.delete();
            }
        }
    }
}