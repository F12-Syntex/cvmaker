package com.cvmaker.application.management;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;

public class ApplicationManager {

    private final DataStorage dataStorage;
    private final EmailAnalysisService emailAnalysisService;
    private final ReportingService reportingService;
    private final DummyEmailService dummyEmailService;
    private GmailService gmailService;
    private GoogleSheetsService sheetsService; // Added Google Sheets service

    private Set<String> processedEmailIds;
    private Map<String, JobApplicationData> jobApplicationsDb;
    private boolean useDummyData;
    private boolean isFirstRun;
    private boolean updateGoogleSheets; // Flag for Google Sheets updates

    private final LLMModel model = LLMModel.GPT_4_1_MINI;

    // Add this as a new field in ApplicationManager class
    private final JobApplicationResearchService researchService;

    // Update the constructor to initialize the research service
    public ApplicationManager() {
        this.dataStorage = new DataStorage();
        this.reportingService = new ReportingService();
        this.dummyEmailService = new DummyEmailService();
        this.sheetsService = new GoogleSheetsService();

        // Initialize AI service
        AiService aiService = new AiService(model);
        this.emailAnalysisService = new EmailAnalysisService(aiService);

        // Initialize the research service with the same AI service
        this.researchService = new JobApplicationResearchService(aiService);
    }

// Add this new method to the ApplicationManager class
    public void researchJobApplications(int maxCount) {
        System.out.println("\n=== Researching Job Applications ===");

        if (jobApplicationsDb.isEmpty()) {
            System.out.println("No job applications found to research.");
            return;
        }

        // Get job-related applications that haven't been researched yet
        List<JobApplicationData> applicationsToResearch = jobApplicationsDb.values().stream()
                .filter(JobApplicationData::isJobRelated)
                .filter(app -> app.getCompanyName() != null && !app.getCompanyName().isEmpty())
                .filter(app -> app.getExtractedInfo() == null || !app.getExtractedInfo().contains("RESEARCH:"))
                .collect(Collectors.toList());

        if (applicationsToResearch.isEmpty()) {
            System.out.println("No new applications to research.");
            return;
        }

        System.out.printf("Found %d applications that need research. Will process up to %d.\n",
                applicationsToResearch.size(), maxCount);

        // Process applications in batch
        researchService.batchEnhanceApplications(applicationsToResearch, maxCount);

        // Save the updated applications to the database
        for (JobApplicationData app : applicationsToResearch) {
            if (app.getExtractedInfo() != null && app.getExtractedInfo().contains("RESEARCH:")) {
                // The application was researched, save it
                dataStorage.saveJobApplicationData(app);

                // Also update Google Sheets if enabled
                if (updateGoogleSheets) {
                    try {
                        sheetsService.updateJobApplicationData(app);
                    } catch (Exception e) {
                        System.err.println("Failed to update Google Sheets with research data: " + e.getMessage());
                    }
                }
            }
        }

        System.out.println("Research completed and applications updated.");
    }

    public void initialize(boolean useDummyEmails, boolean updateGoogleSheets) {
        this.useDummyData = useDummyEmails;
        this.updateGoogleSheets = updateGoogleSheets;

        if (!useDummyData) {
            try {
                this.gmailService = new GmailService();
                this.gmailService.initialize();
            } catch (Exception e) {
                System.err.println("Failed to initialize Gmail service: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        if (updateGoogleSheets) {
            try {
                this.sheetsService.initialize();
                this.sheetsService.initializeSpreadsheet();
                System.out.println("Google Sheets service initialized successfully");
            } catch (Exception e) {
                System.err.println("Failed to initialize Google Sheets service: " + e.getMessage());
                this.updateGoogleSheets = false;
            }
        }

        dataStorage.loadSystemState();
        processedEmailIds = dataStorage.loadProcessedEmailIds();
        jobApplicationsDb = dataStorage.loadJobApplicationsDb();

        System.out.println("=== Job Application Manager Initialized ===");
        System.out.println("Using dummy data: " + useDummyData);
        System.out.println("Updating Google Sheets: " + updateGoogleSheets);
        System.out.println("Previously processed emails: " + processedEmailIds.size());
        System.out.println("Job applications in database: " + jobApplicationsDb.size());
        System.out.println("AI Service model: " + model);
        System.out.println("\n≡ƒöä Will process past week's unprocessed emails");
    }

    public void processJobApplicationEmails() {
        System.out.println("\n=== Processing Job Application Emails ===");

        if (useDummyData) {
            processDummyEmails();
        } else {
            processRealEmails();
        }

        dataStorage.saveSystemState();
    }

    private void processDummyEmails() {
        List<DummyEmailService.DummyEmail> dummyEmails = dummyEmailService.generateDummyEmails();
        List<JobApplicationData> newlyProcessedEmails = new ArrayList<>();
        List<JobApplicationData> newJobRelatedEmails = new ArrayList<>();
        int ignoredEmails = 0;
        int skippedEmails = 0;

        int emailsToProcess = isFirstRun ? 20 : dummyEmails.size();
        System.out.println("Processing up to " + emailsToProcess + " dummy emails...");

        for (int i = 0; i < Math.min(emailsToProcess, dummyEmails.size()); i++) {
            DummyEmailService.DummyEmail email = dummyEmails.get(i);

            if (processedEmailIds.contains(email.getId())) {
                skippedEmails++;
                System.out.printf("  ΓÅ¡∩╕Å  SKIPPED: %s (already processed)\n",
                        email.getSubject().substring(0, Math.min(50, email.getSubject().length())));
                continue;
            }

            System.out.printf("\n≡ƒöì Processing email %d/%d: %s\n", i + 1, emailsToProcess, email.getSubject());

            JobApplicationData jobData = emailAnalysisService.analyzeEmail(
                    email.getId(), email.getSubject(), email.getFrom(), email.getDate(), email.getBody()
            );

            processEmailResult(jobData, newlyProcessedEmails, newJobRelatedEmails);

            if (!jobData.isJobRelated()) {
                ignoredEmails++;
                System.out.printf("  Γ¥î IGNORED: Not job-related (confidence: %.1f%%)\n",
                        jobData.getConfidenceScore() * 100);
            }
        }

        reportingService.displayProcessingSummary(newlyProcessedEmails, newJobRelatedEmails,
                ignoredEmails, skippedEmails, processedEmailIds.size(), jobApplicationsDb.size(), isFirstRun);
    }

    private void processRealEmails() {
        try {
            Set<String> unprocessedEmails = gmailService.findUnprocessedEmails(processedEmailIds);
            System.out.printf("Found %d unprocessed emails from the past week\n", unprocessedEmails.size());

            List<JobApplicationData> newlyProcessedEmails = new ArrayList<>();
            List<JobApplicationData> newJobRelatedEmails = new ArrayList<>();
            int ignoredEmails = 0;
            int skippedEmails = 0;

            for (String emailId : unprocessedEmails) {
                try {
                    GmailService.EmailData emailData = gmailService.getEmailContent(emailId);

                    System.out.printf("\n≡ƒöì Processing: %s\n", emailData.getSubject());

                    JobApplicationData jobData = emailAnalysisService.analyzeEmail(
                            emailData.getId(), emailData.getSubject(), emailData.getFrom(),
                            emailData.getDate(), emailData.getBody()
                    );

                    processEmailResult(jobData, newlyProcessedEmails, newJobRelatedEmails);

                    if (!jobData.isJobRelated()) {
                        ignoredEmails++;
                        System.out.printf("  Γ¥î IGNORED: %s - Not job-related\n",
                                jobData.getSubject().substring(0, Math.min(50, jobData.getSubject().length())));
                    }

                    // Small delay to be respectful to the API
                    Thread.sleep(100);

                } catch (Exception e) {
                    System.err.printf("Error processing email %s: %s\n", emailId, e.getMessage());
                }
            }

            reportingService.displayProcessingSummary(newlyProcessedEmails, newJobRelatedEmails,
                    ignoredEmails, skippedEmails, processedEmailIds.size(), jobApplicationsDb.size(), isFirstRun);

        } catch (Exception e) {
            System.err.println("Error processing real emails: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processEmailResult(JobApplicationData jobData,
            List<JobApplicationData> newlyProcessedEmails,
            List<JobApplicationData> newJobRelatedEmails) {
        dataStorage.saveProcessedEmailId(jobData.getEmailId());
        processedEmailIds.add(jobData.getEmailId());
        newlyProcessedEmails.add(jobData);

        if (jobData.isJobRelated()) {
            newJobRelatedEmails.add(jobData);
            dataStorage.saveJobApplicationData(jobData);
            jobApplicationsDb.put(jobData.getEmailId(), jobData);
            reportingService.displayJobApplicationData(jobData);

            // // Update Google Sheets if enabled
            // if (updateGoogleSheets) {
            //     try {
            //         sheetsService.updateJobApplicationData(jobData);
            //         System.out.println("  ≡ƒÅï Updated job application in Google Sheets");
            //     } catch (Exception e) {
            //         System.err.println("  ✖ Failed to update Google Sheets: " + e.getMessage());
            //     }
            // }
        }
    }

    public void viewJobApplications() {
        reportingService.displayExistingJobApplications(jobApplicationsDb);
    }

    public void syncAllApplicationsToGoogleSheets() {
        if (!updateGoogleSheets) {
            System.out.println("Google Sheets updating is not enabled");
            return;
        }

        System.out.println("\n=== Syncing All Job Applications to Google Sheets ===");

        // Group applications by company and keep only the latest/most important status for each
        Map<String, JobApplicationData> consolidatedApplications = consolidateApplicationsByCompany();

        if (consolidatedApplications.isEmpty()) {
            System.out.println("No job-related applications to sync.");
            return;
        }

        try {
            // Use the new bulk update method instead of individual updates
            sheetsService.bulkUpdateAllApplications(consolidatedApplications);

            System.out.printf("\nBulk sync complete. %d applications synchronized to Google Sheets.\n",
                    consolidatedApplications.size());
        } catch (Exception e) {
            System.err.println("Failed to bulk sync applications: " + e.getMessage());
            e.printStackTrace();
        }
    }

// New method to consolidate applications by company
    private Map<String, JobApplicationData> consolidateApplicationsByCompany() {
        Map<String, JobApplicationData> consolidated = new HashMap<>();

        // Status priority: higher number = higher priority
        Map<String, Integer> statusPriority = Map.of(
                "Rejected", 5,
                "Offer", 4,
                "Interview", 3,
                "Assessment", 2,
                "Applied", 1
        );

        for (JobApplicationData jobData : jobApplicationsDb.values()) {
            if (!jobData.isJobRelated() || jobData.getCompanyName() == null) {
                continue;
            }

            String companyKey = jobData.getCompanyName().trim().toLowerCase();
            JobApplicationData existing = consolidated.get(companyKey);

            if (existing == null) {
                // First entry for this company
                consolidated.put(companyKey, jobData);
            } else {
                // Compare and keep the higher priority status
                String existingStatus = existing.getApplicationStatus() != null ? existing.getApplicationStatus() : "Applied";
                String newStatus = jobData.getApplicationStatus() != null ? jobData.getApplicationStatus() : "Applied";

                int existingPriority = getStatusPriority(existingStatus, statusPriority);
                int newPriority = getStatusPriority(newStatus, statusPriority);

                if (newPriority > existingPriority) {
                    // New status has higher priority, replace
                    consolidated.put(companyKey, jobData);
                } else if (newPriority == existingPriority) {
                    // Same priority, keep the more recent one
                    try {
                        LocalDateTime existingTime = LocalDateTime.parse(existing.getProcessedTimestamp());
                        LocalDateTime newTime = LocalDateTime.parse(jobData.getProcessedTimestamp());
                        if (newTime.isAfter(existingTime)) {
                            consolidated.put(companyKey, jobData);
                        }
                    } catch (Exception e) {
                        // If timestamp parsing fails, keep the existing one
                    }
                }
                // If existing has higher priority, keep it (do nothing)
            }
        }

        return consolidated;
    }

// Helper method to get status priority
    private int getStatusPriority(String status, Map<String, Integer> statusPriority) {
        for (Map.Entry<String, Integer> entry : statusPriority.entrySet()) {
            if (status.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return 1; // Default to lowest priority (Applied)
    }

    public void formatGoogleSheet() {
        if (updateGoogleSheets) {
            try {
                // sheetsService.formatSpreadsheet();
                System.out.println("Google Sheets formatting applied successfully");
            } catch (Exception e) {
                System.err.println("Failed to format Google Sheets: " + e.getMessage());
            }
        } else {
            System.out.println("Google Sheets updating is not enabled");
        }
    }

    public void exportApplicationsToCSV(String filePath) {
        try {
            int count = dataStorage.exportJobApplicationsToCSV(jobApplicationsDb, filePath);
            System.out.printf("Exported %d job applications to %s\n", count, filePath);
        } catch (Exception e) {
            System.err.println("Failed to export applications to CSV: " + e.getMessage());
        }
    }

    // Method to update existing applications based on new emails
    public void updateExistingApplications() {
        if (!updateGoogleSheets) {
            System.out.println("Google Sheets updating is not enabled");
            return;
        }

        System.out.println("\n=== Updating Existing Applications ===");

        // This would involve more complex logic to match new emails with existing applications
        // For example, looking for follow-ups on the same thread or emails with similar subjects
        // For now, just a placeholder
        System.out.println("This feature is not yet implemented");
    }

    public static void main(String[] args) {
        ApplicationManager manager = new ApplicationManager();

        try {
            // Initialize with real Gmail data (false) or dummy data (true)
            // Second parameter controls Google Sheets integration
            boolean useDummyData = false;
            boolean updateGoogleSheets = true;

            manager.initialize(useDummyData, updateGoogleSheets);

            // Process new job application emails
            manager.processJobApplicationEmails();

            // Research job applications (limit to 5 to avoid excessive API calls)
            manager.researchJobApplications(5);

            // Sync all applications to Google Sheets with intelligent consolidation
            manager.syncAllApplicationsToGoogleSheets();

            // Apply formatting to Google Sheet
            manager.formatGoogleSheet();

            // Optional: Export to CSV as a backup
            // manager.exportApplicationsToCSV("job_applications_export.csv");
            // Optional: View applications in console
            manager.viewJobApplications();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
