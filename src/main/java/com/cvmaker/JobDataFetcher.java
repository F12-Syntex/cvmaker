package com.cvmaker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class JobDataFetcher {
    
    private final HttpClient httpClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    public JobDataFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }
    
    public JobData fetchJobData(String jobUrl) throws IOException, InterruptedException {
        System.out.println("Fetching job data from: " + jobUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jobUrl))
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch job data. HTTP status: " + response.statusCode());
        }
        
        return parseJobData(response.body(), jobUrl);
    }
    
    private JobData parseJobData(String html, String jobUrl) {
        Document doc = Jsoup.parse(html);
        
        // Extract job title
        String jobTitle = extractJobTitle(doc);
        
        // Extract job description
        String jobDescription = extractJobDescription(doc);
        
        // Extract company name
        String companyName = extractCompanyName(doc);
        
        // Generate a clean job name for folder structure
        String jobName = generateJobName(jobTitle, companyName);
        
        return new JobData(jobTitle, companyName, jobDescription, jobUrl, jobName);
    }
    
    private String extractJobTitle(Document doc) {
        // Try multiple common selectors for job titles
        String[] titleSelectors = {
            "h1[data-testid='job-title']",
            "h1.job-title",
            "h1[class*='title']",
            ".job-header h1",
            "h1",
            "[data-testid='jobTitle']",
            ".jobsearch-JobInfoHeader-title"
        };
        
        for (String selector : titleSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                String title = elements.first().text().trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }
        
        // Fallback to page title
        String pageTitle = doc.title();
        if (pageTitle != null && !pageTitle.isEmpty()) {
            return pageTitle.replaceAll("\\s*-\\s*.*", "").trim();
        }
        
        return "Unknown Position";
    }
    
    private String extractCompanyName(Document doc) {
        // Try multiple common selectors for company names
        String[] companySelectors = {
            "[data-testid='company-name']",
            ".company-name",
            "[class*='company']",
            ".job-header .company",
            "[data-testid='companyName']",
            ".jobsearch-InlineCompanyRating"
        };
        
        for (String selector : companySelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                String company = elements.first().text().trim();
                if (!company.isEmpty()) {
                    return company;
                }
            }
        }
        
        return "Unknown Company";
    }
    
    private String extractJobDescription(Document doc) {
        // Try multiple common selectors for job descriptions
        String[] descriptionSelectors = {
            "[data-testid='job-description']",
            ".job-description",
            ".jobsearch-jobDescriptionText",
            "[class*='description']",
            ".job-details",
            "main",
            ".content"
        };
        
        for (String selector : descriptionSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                String description = elements.first().text().trim();
                if (description.length() > 100) { // Ensure it's substantial content
                    return description;
                }
            }
        }
        
        // Fallback to body text (filtered)
        String bodyText = doc.body().text();
        if (bodyText.length() > 500) {
            return bodyText;
        }
        
        return "Job description could not be extracted from the provided URL.";
    }
    
    private String generateJobName(String jobTitle, String companyName) {
        String combined = jobTitle + " " + companyName;
        
        // Clean the string for use as folder name
        String cleaned = combined.replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("_{2,}", "_")
                .trim();
        
        // Limit length
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        
        // Remove trailing underscores
        cleaned = cleaned.replaceAll("_+$", "");
        
        return cleaned.isEmpty() ? "Unknown_Job" : cleaned;
    }
    
    public static class JobData {
        private final String jobTitle;
        private final String companyName;
        private final String jobDescription;
        private final String jobUrl;
        private final String jobName;
        
        public JobData(String jobTitle, String companyName, String jobDescription, String jobUrl, String jobName) {
            this.jobTitle = jobTitle;
            this.companyName = companyName;
            this.jobDescription = jobDescription;
            this.jobUrl = jobUrl;
            this.jobName = jobName;
        }
        
        // Getters
        public String getJobTitle() { return jobTitle; }
        public String getCompanyName() { return companyName; }
        public String getJobDescription() { return jobDescription; }
        public String getJobUrl() { return jobUrl; }
        public String getJobName() { return jobName; }
        
        @Override
        public String toString() {
            return String.format("JobData{title='%s', company='%s', name='%s', url='%s'}", 
                    jobTitle, companyName, jobName, jobUrl);
        }
    }
}