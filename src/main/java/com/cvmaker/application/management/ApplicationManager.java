package com.cvmaker.application.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public ApplicationManager() {
        this.dataStorage = new DataStorage();
        this.reportingService = new ReportingService();
        this.dummyEmailService = new DummyEmailService();
        this.sheetsService = new GoogleSheetsService(); // Initialize sheets service

        // Initialize AI service
        AiService aiService = new AiService(model);
        this.emailAnalysisService = new EmailAnalysisService(aiService);
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

            // Update Google Sheets if enabled
            if (updateGoogleSheets) {
                try {
                    sheetsService.updateJobApplicationData(jobData);
                    System.out.println("  ≡ƒÅï Updated job application in Google Sheets");
                } catch (Exception e) {
                    System.err.println("  ✖ Failed to update Google Sheets: " + e.getMessage());
                }
            }
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
        int syncCount = 0;

        for (JobApplicationData jobData : jobApplicationsDb.values()) {
            if (jobData.isJobRelated()) {
                try {
                    sheetsService.updateJobApplicationData(jobData);
                    syncCount++;
                    System.out.printf("  ≡ƒÅï Synced: %s - %s\n",
                            jobData.getCompanyName() != null ? jobData.getCompanyName() : "Unknown",
                            jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "Unknown");
                } catch (Exception e) {
                    System.err.printf("  ✖ Failed to sync application %s: %s\n",
                            jobData.getEmailId(), e.getMessage());
                }
            }
        }

        System.out.printf("\nSync complete. %d applications synchronized to Google Sheets.\n", syncCount);
    }

    public void formatGoogleSheet() {
        if (updateGoogleSheets) {
            try {
                sheetsService.formatSpreadsheet();
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

            // Sync all existing applications to Google Sheets
            // This ensures the sheet has all applications, not just new ones
            manager.syncAllApplicationsToGoogleSheets();

            // Apply formatting to Google Sheet
            manager.formatGoogleSheet();

            // Optional: Export to CSV as a backup
            // manager.exportApplicationsToCSV("job_applications_export.csv");
            // Optional: View applications in console
            // manager.viewJobApplications();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
