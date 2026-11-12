package com.cvmaker.application.management;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;

public class ApplicationManager {

    // Constants
    private static final LLMModel DEFAULT_MODEL = LLMModel.GPT_4_1_MINI;
    /** After this many AI failures in a row the run stops: the AI is down, not one email. */
    static final int MAX_CONSECUTIVE_AI_FAILURES = 3;
    private static final long DELAY_BETWEEN_EMAILS_MS = 100;

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
        this(new DataStorage(), new GmailService(), new GoogleSheetsService(),
                new EmailAnalysisService(new AiService(DEFAULT_MODEL)));
    }

    /** For tests: uses the given services instead of creating real ones. */
    ApplicationManager(DataStorage dataStorage, GmailService gmailService,
            GoogleSheetsService sheetsService, EmailAnalysisService emailAnalysisService) {
        this.dataStorage = dataStorage;
        this.gmailService = gmailService;
        this.sheetsService = sheetsService;
        this.emailAnalysisService = emailAnalysisService;
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
        int deferred = 0;
        int consecutiveAiFailures = 0;

        for (String emailId : emailIds) {
            try {
                GmailService.EmailData emailData = gmailService.getEmailContent(emailId);
                boolean recorded = processSingleEmail(emailData);

                if (recorded) {
                    consecutiveAiFailures = 0;
                } else {
                    deferred++;
                    consecutiveAiFailures++;
                    if (consecutiveAiFailures >= MAX_CONSECUTIVE_AI_FAILURES) {
                        System.err.printf("AI analysis failed %d times in a row; stopping this run. "
                                + "The remaining emails stay unprocessed.\n", consecutiveAiFailures);
                        break;
                    }
                }
                Thread.sleep(DELAY_BETWEEN_EMAILS_MS); // Rate limiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.printf("Failed to process email %s: %s\n", emailId, e.getMessage());
            }
        }

        if (deferred > 0) {
            System.out.printf("%d email(s) were not analysed because the AI call failed. "
                    + "They are not marked as processed and will be retried on the next run.\n", deferred);
        }
    }

    /**
     * Analyses one email and records it.
     *
     * @return false when the AI call failed and the email was left unrecorded for a later run
     */
    private boolean processSingleEmail(GmailService.EmailData emailData) {

        EmailAnalysisService.AnalysisResult result = emailAnalysisService.analyzeEmailDetailed(
                emailData.getId(),
                emailData.getSubject(),
                emailData.getFrom(),
                emailData.getDate(),
                emailData.getBody()
        );

        if (result.shouldRetry()) {
            // The data is only a keyword guess made because the AI was unreachable. Recording
            // it would mark the email as processed and the real analysis would never happen.
            return false;
        }

        updateApplicationDatabase(result.data());
        return true;
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
