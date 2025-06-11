package com.cvmaker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

public class CVGenerator {
    
    private static final String TEMPLATE_FILE = "cv_template.tex";
    private static final String TARGET_DIR = "target";
    private static final String OUTPUT_FILE = "generated_cv.tex";
    private static final String PDF_FILE = "generated_cv.pdf";
    
    public void generateCV(CVData cvData) throws IOException {
        // Clean and create target directory
        prepareTargetDirectory();
        
        // Read template
        String template = readTemplate();
        
        // Create placeholder map
        Map<String, String> placeholders = createPlaceholderMap(cvData);
        
        // Replace all placeholders dynamically
        String populatedCV = replacePlaceholders(template, placeholders);
        
        // Write generated LaTeX file to target directory
        writeGeneratedFile(populatedCV);
        
        // Compile to PDF in target directory
        compileToPDF();
        
        System.out.println("CV generated in target directory:");
        System.out.println("  LaTeX source: " + TARGET_DIR + "/" + OUTPUT_FILE);
        System.out.println("  PDF output: " + TARGET_DIR + "/" + PDF_FILE);
    }
    
    private void prepareTargetDirectory() throws IOException {
        Path targetPath = Paths.get(TARGET_DIR);
        
        // Delete target directory if it exists
        if (Files.exists(targetPath)) {
            System.out.println("Cleaning target directory...");
            Files.walk(targetPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        
        // Create fresh target directory
        Files.createDirectories(targetPath);
        System.out.println("Created clean target directory: " + TARGET_DIR);
    }
    
    private String readTemplate() throws IOException {
        return new String(Files.readAllBytes(Paths.get(TEMPLATE_FILE)));
    }
    
    private Map<String, String> createPlaceholderMap(CVData cvData) {
        Map<String, String> placeholders = new HashMap<>();
        
        // Personal information - null-safe
        placeholders.put("FULL_NAME", safeString(cvData.getFullName()));
        placeholders.put("FIRST_NAME", extractFirstName(cvData.getFullName()));
        placeholders.put("LAST_NAME", extractLastName(cvData.getFullName()));
        placeholders.put("EMAIL", safeString(cvData.getEmail()));
        placeholders.put("PHONE", safeString(cvData.getPhone()));
        placeholders.put("LOCATION", safeString(cvData.getLocation()));
        placeholders.put("LINKEDIN", safeString(cvData.getLinkedin()));
        
        // Extract additional info from LinkedIn URL if needed
        placeholders.put("LINKEDIN_URL", formatLinkedInUrl(cvData.getLinkedin()));
        placeholders.put("LINKEDIN_USERNAME", extractLinkedInUsername(cvData.getLinkedin()));
        
        // Placeholder for additional fields that might be added
        placeholders.put("JOB_TITLE", ""); // Can be added to CVData later
        placeholders.put("GITHUB_URL", ""); // Can be added to CVData later
        placeholders.put("GITHUB_USERNAME", "");
        placeholders.put("WEBSITE_URL", "");
        placeholders.put("ADDRESS_LINE_1", "");
        placeholders.put("ADDRESS_LINE_2", "");
        placeholders.put("WEBSITE", "");
        
        // Dynamic sections
        placeholders.put("PROFESSIONAL_SUMMARY", ""); // Can be added to CVData
        placeholders.put("CORE_COMPETENCIES", ""); // Can be added to CVData
        placeholders.put("EXPERIENCE_SECTION", generateExperienceSection(cvData));
        placeholders.put("EDUCATION_SECTION", generateEducationSection(cvData));
        placeholders.put("TECHNICAL_SKILLS", generateSkillsSection(cvData));
        placeholders.put("SKILLS_SECTION", generateSkillsSection(cvData)); // Backward compatibility
        placeholders.put("CERTIFICATIONS_SECTION", generateCertificationsSection(cvData));
        
        // Additional sections that can be extended
        placeholders.put("PROJECTS_SECTION", "");
        placeholders.put("AWARDS_SECTION", "");
        placeholders.put("PUBLICATIONS_SECTION", "");
        placeholders.put("LANGUAGES_SECTION", "");
        placeholders.put("VOLUNTEER_SECTION", "");
        placeholders.put("MEMBERSHIPS_SECTION", "");
        placeholders.put("ADDITIONAL_INFO", "");
        
        // Add any additional dynamic content that might be in CVData
        addCustomPlaceholders(placeholders, cvData);
        
        return placeholders;
    }
    
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }
    
    private String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        }
        return "";
    }
    
    private String formatLinkedInUrl(String linkedin) {
        if (linkedin == null || linkedin.trim().isEmpty()) return "";
        if (linkedin.startsWith("http")) return linkedin;
        if (linkedin.startsWith("linkedin.com/")) return "https://" + linkedin;
        if (linkedin.startsWith("www.linkedin.com/")) return "https://" + linkedin;
        return "https://linkedin.com/in/" + linkedin;
    }
    
    private String extractLinkedInUsername(String linkedin) {
        if (linkedin == null || linkedin.trim().isEmpty()) return "";
        // Extract username from various LinkedIn URL formats
        String cleaned = linkedin.replace("https://", "").replace("http://", "")
                               .replace("www.", "").replace("linkedin.com/in/", "");
        return cleaned;
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
            // Replace with empty string
            result = result.replace(remainingPlaceholder, "");
        }
        
        return result;
    }
    
    private void addCustomPlaceholders(Map<String, String> placeholders, CVData cvData) {
        // This method can be extended to handle custom fields
        // For example, if CVData gets new fields in the future:
        
        // Example: If you add additional fields to CVData later
        // placeholders.put("PROFESSIONAL_SUMMARY", generateSummarySection(cvData));
        // placeholders.put("PROJECTS_SECTION", generateProjectsSection(cvData));
    }
    
    private String generateExperienceSection(CVData cvData) {
        if (cvData.getExperiences() == null || cvData.getExperiences().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (CVData.Experience exp : cvData.getExperiences()) {
            section.append("\\experience{").append(escapeLatex(safeString(exp.getJobTitle()))).append("}{")
                   .append(escapeLatex(safeString(exp.getDuration()))).append("}{")
                   .append(escapeLatex(safeString(exp.getCompany()))).append("}{")
                   .append("}{"); // Location placeholder
            
            if (exp.getResponsibilities() != null && !exp.getResponsibilities().isEmpty()) {
                section.append("\n\\begin{itemize}\n");
                for (String responsibility : exp.getResponsibilities()) {
                    section.append("    \\achievement{").append(escapeLatex(safeString(responsibility))).append("}\n");
                }
                section.append("\\end{itemize}");
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
            section.append("\\education{").append(escapeLatex(safeString(edu.getDegree()))).append("}{")
                   .append(escapeLatex(safeString(edu.getDetails()))).append("}{")
                   .append(escapeLatex(safeString(edu.getInstitution()))).append("}{")
                   .append("}\n"); // GPA placeholder
        }
        
        return section.toString();
    }
    
    private String generateSkillsSection(CVData cvData) {
        if (cvData.getSkillCategories() == null || cvData.getSkillCategories().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (CVData.SkillCategory category : cvData.getSkillCategories()) {
            section.append("\\skillcategory{").append(escapeLatex(safeString(category.getCategoryName())))
                   .append("}{").append(escapeLatex(safeString(category.getSkills()))).append("}\n");
        }
        
        return section.toString();
    }
    
    private String generateCertificationsSection(CVData cvData) {
        if (cvData.getCertifications() == null || cvData.getCertifications().isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        
        for (String cert : cvData.getCertifications()) {
            section.append(escapeLatex(safeString(cert))).append("\\\\[2pt]\n");
        }
        
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
                  .replace("~", "\\textasciitilde{}")
                  .replace("\"", "''");
    }
    
    private void writeGeneratedFile(String content) throws IOException {
        Path outputPath = Paths.get(TARGET_DIR, OUTPUT_FILE);
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            writer.write(content);
        }
    }
    
    private void compileToPDF() throws IOException {
        if (!isLatexInstalled()) {
            System.out.println("LaTeX not found. Generated file available at: " + TARGET_DIR + "/" + OUTPUT_FILE);
            return;
        }
        
        try {
            CommandLine cmdLine = new CommandLine("pdflatex");
            cmdLine.addArgument("-interaction=nonstopmode");
            cmdLine.addArgument("-output-directory=" + TARGET_DIR);
            cmdLine.addArgument(TARGET_DIR + "/" + OUTPUT_FILE);
            
            DefaultExecutor executor = new DefaultExecutor();
            executor.setWorkingDirectory(new File("."));
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
            executor.setStreamHandler(streamHandler);
            
            int exitCode = executor.execute(cmdLine);
            
            if (exitCode == 0) {
                System.out.println("PDF compiled successfully!");
                cleanupLatexFiles();
            } else {
                System.err.println("LaTeX compilation failed");
                System.err.println("Output: " + outputStream.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error compiling PDF: " + e.getMessage());
            System.out.println("LaTeX source available at: " + TARGET_DIR + "/" + OUTPUT_FILE);
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
        String[] extensions = {".aux", ".log", ".out", ".fdb_latexmk", ".fls"};
        String baseName = OUTPUT_FILE.replace(".tex", "");
        
        for (String ext : extensions) {
            Path filePath = Paths.get(TARGET_DIR, baseName + ext);
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }
}