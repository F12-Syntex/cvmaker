package com.cvmaker;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JobDataFetcher {
    
    public static class JobData {
        private final String jobTitle;
        private final String companyName;
        private final String jobDescription;
        private final String jobName;
        
        public JobData(String jobTitle, String companyName, String jobDescription) {
            this.jobTitle = jobTitle;
            this.companyName = companyName;
            this.jobDescription = jobDescription;
            this.jobName = generateJobName(jobTitle, companyName);
        }
        
        private String generateJobName(String title, String company) {
            String safeName = (title + "_" + company)
                .replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .toLowerCase();
            return safeName.length() > 50 ? safeName.substring(0, 50) : safeName;
        }
        
        public String getJobTitle() { return jobTitle; }
        public String getCompanyName() { return companyName; }
        public String getJobDescription() { return jobDescription; }
        public String getJobName() { return jobName; }
    }
    
    public JobData fetchJobData(String source) throws IOException {
        // Check if source is a file path or URL
        if (isFilePath(source)) {
            return fetchFromFile(source);
        } else {
            return fetchFromUrl(source);
        }
    }
    
    private boolean isFilePath(String source) {
        // Check if it's a local file path (contains backslashes or doesn't start with http)
        return source.contains("\\") || 
               source.contains("/") && !source.startsWith("http") ||
               Paths.get(source).toFile().exists();
    }
    
    private JobData fetchFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        
        String content = Files.readString(path);
        
        // Extract job details from file content
        // For now, using placeholder values - you may want to parse the content
        String jobTitle = extractJobTitle(content);
        String companyName = extractCompanyName(content);
        
        return new JobData(jobTitle, companyName, content);
    }
    
    private JobData fetchFromUrl(String url) throws IOException {
        try {
            URI uri = URI.create(url);
            // Your existing URL fetching logic here
            // This is where you'd implement web scraping
            
            // Placeholder implementation
            return new JobData("Software Engineer", "Tech Company", "Job description from URL: " + url);
            
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL format: " + url, e);
        }
    }
    
    private String extractJobTitle(String content) {
        // Simple extraction - you may want to improve this
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("title:") || 
                line.toLowerCase().contains("position:") ||
                line.toLowerCase().contains("role:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return "Software Developer"; // Default
    }
    
    private String extractCompanyName(String content) {
        // Simple extraction - you may want to improve this
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("company:") || 
                line.toLowerCase().contains("employer:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return "Unknown Company"; // Default
    }
}