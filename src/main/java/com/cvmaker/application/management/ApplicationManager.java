package com.cvmaker.application.management;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;

public class ApplicationManager {

    // Constants
    private static final LLMModel DEFAULT_MODEL = LLMModel.GPT_4_1_MINI;

    // Core services
    private final DataStorage dataStorage;
    private final EmailAnalysisService emailAnalysisService;
    private final GmailService gmailService;
    private final GoogleSheetsService sheetsService;

    // State tracking
    private Set<String> processedEmailIds;
    private Map<String, JobApplicationData> jobApplicationsDb;
    private boolean isGoogleSheetsEnabled;

    public ApplicationManager() {
        this.dataStorage = new DataStorage();
        this.gmailService = new GmailService();
        this.sheetsService = new GoogleSheetsService();
        this.emailAnalysisService = new EmailAnalysisService(new AiService(DEFAULT_MODEL));
    }

    public void initialize() {
        initializeServices();
        loadData();
        printInitializationStatus();
    }

    private void initializeServices() {
        try {
            gmailService.initialize();
            initializeGoogleSheets();
        } catch (Exception e) {
            handleInitializationError(e);
        }
    }

    private void initializeGoogleSheets() {
        try {
            sheetsService.initialize();
            sheetsService.initializeSpreadsheet();
            isGoogleSheetsEnabled = true;
        } catch (Exception e) {
            System.err.println("Google Sheets initialization failed: " + e.getMessage());
            isGoogleSheetsEnabled = false;
        }
    }

    private void loadData() {
        dataStorage.loadSystemState();
        processedEmailIds = dataStorage.loadProcessedEmailIds();
        jobApplicationsDb = dataStorage.loadJobApplicationsDb();
    }

    private void printInitializationStatus() {
        System.out.println("=== Job Application Manager Initialized ===");
        System.out.println("Google Sheets enabled: " + isGoogleSheetsEnabled);
        System.out.println("Processed emails count: " + processedEmailIds.size());
        System.out.println("Applications in database: " + jobApplicationsDb.size());
        System.out.println("AI Model: " + DEFAULT_MODEL);
    }

    public void processJobApplicationEmails() {
        System.out.println("\n=== Processing Job Application Emails ===");

        try {
            Set<String> unprocessedEmails = gmailService.findUnprocessedEmails(processedEmailIds);
            System.out.printf("Found %d unprocessed emails\n", unprocessedEmails.size());

            processEmails(unprocessedEmails);
            dataStorage.saveSystemState();

        } catch (Exception e) {
            System.err.println("Email processing failed: " + e.getMessage());
        }
    }

    private void processEmails(Set<String> emailIds) {
        for (String emailId : emailIds) {
            try {
                GmailService.EmailData emailData = gmailService.getEmailContent(emailId);
                processSingleEmail(emailData);
                Thread.sleep(100); // Rate limiting
            } catch (Exception e) {
                System.err.printf("Failed to process email %s: %s\n", emailId, e.getMessage());
            }
        }
    }

    private void processSingleEmail(GmailService.EmailData emailData) {

        JobApplicationData jobData = emailAnalysisService.analyzeEmail(
                emailData.getId(),
                emailData.getSubject(),
                emailData.getFrom(),
                emailData.getDate(),
                emailData.getBody()
        );

        updateApplicationDatabase(jobData);
    }

    private void updateApplicationDatabase(JobApplicationData jobData) {
        processedEmailIds.add(jobData.getEmailId());
        dataStorage.saveProcessedEmailId(jobData.getEmailId());

        if (jobData.isJobRelated()) {
            jobApplicationsDb.put(jobData.getEmailId(), jobData);
            dataStorage.saveJobApplicationData(jobData);
        }
    }

    public void syncAllApplicationsToGoogleSheets() {
        if (!isGoogleSheetsEnabled) {
            System.out.println("Google Sheets sync disabled");
            return;
        }

        try {
            Map<String, JobApplicationData> consolidated = consolidateApplicationsByCompany();
            if (!consolidated.isEmpty()) {
                sheetsService.bulkUpdateAllApplications(consolidated);
                System.out.printf("Synced %d applications to Google Sheets\n", consolidated.size());
            }
        } catch (Exception e) {
            System.err.println("Google Sheets sync failed: " + e.getMessage());
        }
    }

    private Map<String, JobApplicationData> consolidateApplicationsByCompany() {
        Map<String, JobApplicationData> consolidated = new HashMap<>();
        Map<String, Integer> statusPriority = getStatusPriorityMap();

        for (JobApplicationData jobData : jobApplicationsDb.values()) {
            if (!isValidJobApplication(jobData)) {
                continue;
            }

            String companyKey = jobData.getCompanyName().trim().toLowerCase();
            updateConsolidatedApplications(consolidated, companyKey, jobData, statusPriority);
        }

        return consolidated;
    }

    // Adds or updates the consolidated map with the job application with the highest status priority
    private void updateConsolidatedApplications(
            Map<String, JobApplicationData> consolidated,
            String companyKey,
            JobApplicationData jobData,
            Map<String, Integer> statusPriority) {

        JobApplicationData existing = consolidated.get(companyKey);
        if (existing == null) {
            consolidated.put(companyKey, jobData);
        } else {
            int existingPriority = statusPriority.getOrDefault(existing.getApplicationStatus(), 0);
            int newPriority = statusPriority.getOrDefault(jobData.getApplicationStatus(), 0);
            if (newPriority > existingPriority) {
                consolidated.put(companyKey, jobData);
            }
        }
    }

    private boolean isValidJobApplication(JobApplicationData jobData) {
        return jobData.isJobRelated() && jobData.getCompanyName() != null;
    }

    private Map<String, Integer> getStatusPriorityMap() {
        return Map.of(
                "Rejected", 5,
                "Offer", 4,
                "Interview", 3,
                "Assessment", 2,
                "Applied", 1
        );
    }

    public void exportApplicationsToCSV(String filePath) {
        try {
            int count = dataStorage.exportJobApplicationsToCSV(jobApplicationsDb, filePath);
            System.out.printf("Exported %d applications to %s\n", count, filePath);
        } catch (Exception e) {
            System.err.println("CSV export failed: " + e.getMessage());
        }
    }

    private void handleInitializationError(Exception e) {
        System.err.println("Initialization failed: " + e.getMessage());
        isGoogleSheetsEnabled = false;
    }

    public static void main(String[] args) {
        ApplicationManager manager = new ApplicationManager();
        try {
            manager.initialize();
            manager.processJobApplicationEmails();
            manager.syncAllApplicationsToGoogleSheets();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
