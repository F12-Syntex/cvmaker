package com.cvmaker.crawler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import com.cvmaker.CVGenerator;
import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

public class ReedCrawler {

    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    // Configuration
    private ConfigManager config;
    private CrawlerConfig crawlerConfig;

    // Application tracking
    private int applicationsSubmitted = 0;
    private int jobsChecked = 0;
    private int easyApplyJobsFound = 0;

    public ReedCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public ReedCrawler(CrawlerConfig crawlerConfig) throws Exception {
        this.crawlerConfig = crawlerConfig;
        this.config = new ConfigManager(crawlerConfig.getCvConfigFile());
        initializeDirectories();
    }

    private void initializeDirectories() throws Exception {
        Files.createDirectories(Paths.get(config.getOutputDirectory()));
    }

    public void setupBrowser() {
        playwright = Playwright.create();

        try {
            Path userDataDir = Paths.get(crawlerConfig.getBrowserDataDir());
            this.context = playwright.chromium().launchPersistentContext(
                    userDataDir,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(crawlerConfig.isHeadless())
                            .setSlowMo(crawlerConfig.getSlowMo())
                            .setViewportSize(crawlerConfig.getViewportWidth(), crawlerConfig.getViewportHeight())
                            .setUserAgent(crawlerConfig.getUserAgent())
                            .setJavaScriptEnabled(true)
                            .setArgs(Arrays.asList(crawlerConfig.getBrowserArgs().split(",")))
                            .setExtraHTTPHeaders(Map.of(
                                    "Accept", crawlerConfig.getAcceptHeader(),
                                    "Accept-Language", crawlerConfig.getAcceptLanguageHeader()
                            ))
            );

            context.addInitScript("() => {"
                    + "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                    + "delete navigator.__proto__.webdriver;"
                    + "window.chrome = { runtime: {} };"
                    + "}");

            this.page = context.newPage();
            page.setDefaultTimeout(crawlerConfig.getPageTimeout());
            page.setDefaultNavigationTimeout(crawlerConfig.getNavigationTimeout());

            System.out.println("Browser setup completed");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openForLogin() {
        try {
            page.navigate(crawlerConfig.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(crawlerConfig.getPageLoadDelay());

            System.out.println("Login to " + crawlerConfig.getBaseUrl() + " if needed, then press ENTER...");
            System.in.read();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processJobsAndApply() {
        try {
            // Navigate to job search
            page.navigate(crawlerConfig.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Perform search
            performJobSearch();

            System.out.println("Starting job search - looking for Easy Apply jobs only...");
            System.out.println("Maximum applications to submit: " + crawlerConfig.getMaxApplications());

            // Process jobs one by one
            while (applicationsSubmitted < crawlerConfig.getMaxApplications()) {
                JobInfo job = findAndClickNextEasyApplyJob();

                if (job == null) {
                    System.out.println("No more Easy Apply jobs found or reached application limit");
                    break;
                }

                // Wait for job card to update
                page.waitForTimeout(crawlerConfig.getJobCardLoadDelay());

                // Extract and print job description
                if (crawlerConfig.isDebugMode()) {
                    printJobDescriptionFromCard();
                }

                // Generate CV for this job
                Path generatedCvPath = generateCVForJob(job);

                if (generatedCvPath != null && Files.exists(generatedCvPath)) {
                    // Apply for the job
                    boolean applicationSuccess = applyForJobStandardProcess(generatedCvPath);

                    if (applicationSuccess) {
                        applicationsSubmitted++;
                        System.out.println("Successfully applied to job " + applicationsSubmitted + "/" + crawlerConfig.getMaxApplications());
                    }
                } else {
                    System.out.println("Failed to generate CV for job: " + job.title);
                }

                // Wait between applications
                page.waitForTimeout(crawlerConfig.getApplicationDelay());
            }

            printSessionSummary();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performJobSearch() {
        try {
            Locator searchInput = page.locator(crawlerConfig.getSearchInputSelector()).first();
            searchInput.click();
            searchInput.fill(crawlerConfig.getSearchKeywords());
            searchInput.press("Enter");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(crawlerConfig.getSearchResultsDelay());

        } catch (Exception e) {
            System.out.println("Error performing job search: " + e.getMessage());
        }
    }

    private JobInfo findAndClickNextEasyApplyJob() {
        try {
            String[] jobSelectors = crawlerConfig.getJobCardSelectors().split(",");
            System.out.println("Looking for Easy Apply jobs...");

            for (String selector : jobSelectors) {
                try {
                    Locator elements = page.locator(selector.trim());
                    int count = elements.count();

                    if (count > 0) {
                        if (crawlerConfig.isDebugMode()) {
                            System.out.println("Found " + count + " job elements with selector: " + selector);
                        }

                        // Check each job card for Easy Apply button
                        for (int i = jobsChecked; i < count; i++) {
                            try {
                                Locator element = elements.nth(i);
                                jobsChecked = i + 1;

                                if (element.isVisible()) {
                                    if (hasEasyApplyButton(element)) {
                                        easyApplyJobsFound++;
                                        JobInfo job = extractJobInfo(element, i);

                                        System.out.println("✅ Found Easy Apply job: " + job.title + " (" + easyApplyJobsFound + " Easy Apply jobs found so far)");

                                        element.scrollIntoViewIfNeeded();
                                        page.waitForTimeout(crawlerConfig.getElementInteractionDelay());
                                        element.click();
                                        page.waitForTimeout(crawlerConfig.getJobCardLoadDelay());

                                        return job;
                                    } else {
                                        JobInfo job = extractJobInfo(element, i);
                                        if (crawlerConfig.isDebugMode()) {
                                            System.out.println("❌ Skipping job (no Easy Apply): " + job.title);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                continue;
                            }
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("No more Easy Apply jobs found after checking " + jobsChecked + " jobs");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean hasEasyApplyButton(Locator jobCard) {
        try {
            String[] easyApplySelectors = crawlerConfig.getEasyApplySelectors().split(",");

            for (String selector : easyApplySelectors) {
                try {
                    Locator easyApplyButton = jobCard.locator(selector.trim());
                    if (easyApplyButton.count() > 0 && easyApplyButton.first().isVisible()) {
                        if (crawlerConfig.isDebugMode()) {
                            System.out.println("   Found Easy Apply button with selector: " + selector);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Check card text for Easy Apply keywords
            try {
                String cardText = jobCard.textContent();
                if (cardText != null) {
                    String lowerText = cardText.toLowerCase();
                    String[] keywords = crawlerConfig.getEasyApplyKeywords().split(",");
                    for (String keyword : keywords) {
                        if (lowerText.contains(keyword.trim().toLowerCase())) {
                            if (crawlerConfig.isDebugMode()) {
                                System.out.println("   Found Easy Apply keyword: " + keyword);
                            }
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // Continue
            }

            return false;

        } catch (Exception e) {
            System.out.println("Error checking for Easy Apply button: " + e.getMessage());
            return false;
        }
    }

    private JobInfo extractJobInfo(Locator element, int index) {
        JobInfo job = new JobInfo();

        try {
            String text = element.textContent();
            if (text != null && !text.trim().isEmpty()) {
                String[] lines = text.split("\n");
                job.title = lines[0].trim();
                if (lines.length > 1) {
                    job.company = lines[1].trim();
                }
            }
        } catch (Exception e) {
            job.title = "Job " + index;
            job.company = "Company";
        }

        return job;
    }

    private boolean applyForJobStandardProcess(Path cvPath) {
        try {
            System.out.println("Starting job application process...");

            if (!clickApplyNowButton()) {
                System.out.println("Could not find Apply Now button");
                return false;
            }

            page.waitForTimeout(crawlerConfig.getApplicationStepDelay());

            clickUpdateButton();
            page.waitForTimeout(crawlerConfig.getElementInteractionDelay());

            if (!clickChooseOwnCVButton()) {
                System.out.println("Could not find Choose your own CV file button");
                return false;
            }

            page.waitForTimeout(crawlerConfig.getElementInteractionDelay());

            if (!uploadCVFile(cvPath)) {
                System.out.println("Failed to upload CV file");
                return false;
            }

            waitForCVProcessing();

            if (!submitApplication()) {
                System.out.println("Failed to submit application");
                return false;
            }

            handleConfirmationDialog();

            System.out.println("Job application completed successfully!");
            return true;

        } catch (Exception e) {
            System.out.println("Error during job application: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean clickApplyNowButton() {
        String[] applySelectors = crawlerConfig.getApplyButtonSelectors().split(",");

        for (String selector : applySelectors) {
            try {
                Locator applyButton = page.locator(selector.trim()).first();
                if (applyButton.isVisible()) {
                    System.out.println("Found Apply Now button with selector: " + selector);
                    applyButton.scrollIntoViewIfNeeded();
                    applyButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return false;
    }

    private void clickUpdateButton() {
        String[] updateSelectors = crawlerConfig.getUpdateButtonSelectors().split(",");

        for (String selector : updateSelectors) {
            try {
                Locator updateButton = page.locator(selector.trim()).first();
                if (updateButton.isVisible()) {
                    System.out.println("Found Update button, clicking...");
                    updateButton.click();
                    return;
                }
            } catch (Exception e) {
                continue;
            }
        }

        if (crawlerConfig.isDebugMode()) {
            System.out.println("No Update button found (this may be normal)");
        }
    }

    private boolean clickChooseOwnCVButton() {
        String[] cvSelectors = crawlerConfig.getCvUploadSelectors().split(",");

        for (String selector : cvSelectors) {
            try {
                Locator cvButton = page.locator(selector.trim()).first();
                if (cvButton.isVisible()) {
                    System.out.println("Found Choose CV button with selector: " + selector);
                    cvButton.scrollIntoViewIfNeeded();
                    cvButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        return false;
    }

    private boolean uploadCVFile(Path cvPath) {
        try {
            String[] fileInputSelectors = crawlerConfig.getFileInputSelectors().split(",");

            for (String selector : fileInputSelectors) {
                try {
                    Locator fileInput = page.locator(selector.trim()).first();
                    if (fileInput.count() > 0) {
                        System.out.println("Found file input, uploading CV: " + cvPath.toAbsolutePath());
                        fileInput.setInputFiles(cvPath);
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("No file input found for CV upload");
            return false;

        } catch (Exception e) {
            System.out.println("Error uploading CV file: " + e.getMessage());
            return false;
        }
    }

    private void waitForCVProcessing() {
        try {
            System.out.println("Waiting for CV processing to complete...");

            String[] processingSelectors = crawlerConfig.getProcessingSelectors().split(",");
            page.waitForTimeout(crawlerConfig.getProcessingStartDelay());

            for (String selector : processingSelectors) {
                try {
                    Locator processingElement = page.locator(selector.trim()).first();
                    if (processingElement.isVisible()) {
                        System.out.println("CV processing detected, waiting for completion...");
                        processingElement.waitFor(new Locator.WaitForOptions()
                                .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
                                .setTimeout(crawlerConfig.getProcessingTimeout()));
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            page.waitForTimeout(crawlerConfig.getProcessingCompleteDelay());
            System.out.println("CV processing completed");

        } catch (Exception e) {
            System.out.println("Error waiting for CV processing: " + e.getMessage());
            page.waitForTimeout(crawlerConfig.getProcessingFallbackDelay());
        }
    }

    private boolean submitApplication() {
        String[] submitSelectors = crawlerConfig.getSubmitButtonSelectors().split(",");

        for (String selector : submitSelectors) {
            try {
                Locator submitButton = page.locator(selector.trim()).first();
                if (submitButton.isVisible()) {
                    System.out.println("Found Submit button with selector: " + selector);
                    submitButton.scrollIntoViewIfNeeded();
                    submitButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        System.out.println("No Submit Application button found");
        return false;
    }

    private void handleConfirmationDialog() {
        try {
            page.waitForTimeout(crawlerConfig.getConfirmationDialogDelay());

            String[] okSelectors = crawlerConfig.getConfirmationSelectors().split(",");

            for (String selector : okSelectors) {
                try {
                    Locator okButton = page.locator(selector.trim()).first();
                    if (okButton.isVisible()) {
                        System.out.println("Found confirmation dialog, clicking OK...");
                        okButton.click();
                        page.waitForTimeout(crawlerConfig.getElementInteractionDelay());
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (crawlerConfig.isDebugMode()) {
                System.out.println("No confirmation dialog found");
            }

        } catch (Exception e) {
            System.out.println("Error handling confirmation dialog: " + e.getMessage());
        }
    }

    private void printJobDescriptionFromCard() {
        try {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DEBUG: JOB DESCRIPTION FROM UPDATED CARD");
            System.out.println("=".repeat(80));

            String[] cardSelectors = crawlerConfig.getJobDescriptionSelectors().split(",");
            boolean foundDescription = false;

            for (String cardSelector : cardSelectors) {
                try {
                    Locator jobCard = page.locator(cardSelector.trim()).first();
                    if (jobCard.isVisible()) {
                        String cardContent = jobCard.textContent();

                        if (cardContent != null && cardContent.length() > 200) {
                            System.out.println("FOUND JOB CARD WITH SELECTOR: " + cardSelector);
                            System.out.println("CARD CONTENT LENGTH: " + cardContent.length() + " characters");

                            if (cardContent.length() > 1500) {
                                System.out.println(cardContent.substring(0, 1500) + "...[TRUNCATED]");
                            } else {
                                System.out.println(cardContent);
                            }

                            foundDescription = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (!foundDescription) {
                System.out.println("NO UPDATED JOB CARD FOUND");
            }

            System.out.println("=".repeat(80));
            System.out.println("END DEBUG: JOB DESCRIPTION FROM UPDATED CARD");
            System.out.println("=".repeat(80) + "\n");

        } catch (Exception e) {
            System.out.println("Error printing job description from card: " + e.getMessage());
        }
    }

    private Path generateCVForJob(JobInfo job) {
        try {
            System.out.println("Generating CV for: " + job.title + " at " + job.company);

            String jobContent = extractJobDescriptionFromCard();
            if (jobContent == null || jobContent.trim().isEmpty()) {
                System.out.println("No job content found, skipping CV generation");
                return null;
            }

            // Create unique job folder
            String uuid = UUID.randomUUID().toString();
            String jobFolder = config.getOutputDirectory() + "/" + uuid;
            Path jobFolderPath = Paths.get(jobFolder);
            Files.createDirectories(jobFolderPath);

            // Create config for this specific job
            config.setJobDescriptionContent(jobContent);

            // Generate CV using the simplified CVGenerator
            CVGenerator generator = new CVGenerator(config);

            // Temporarily update config output directory
            String originalOutputDir = config.getOutputDirectory();
            config.setOutputDirectory(jobFolderPath.toString());

            generator.generate();

            // Restore original output directory
            config.setOutputDirectory(originalOutputDir);

            generator.shutdown();

            Path cvPath = jobFolderPath.resolve(config.getOutputPdfName());
            if (Files.exists(cvPath)) {
                System.out.println("CV generated successfully: " + cvPath.toAbsolutePath());
                return cvPath;
            } else {
                System.out.println("CV generation failed - file not found");
                return null;
            }

        } catch (Exception e) {
            System.out.println("Error generating CV: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String extractJobDescriptionFromCard() {
        try {
            String[] cardSelectors = crawlerConfig.getJobDescriptionSelectors().split(",");

            for (String cardSelector : cardSelectors) {
                try {
                    Locator jobCard = page.locator(cardSelector.trim()).first();
                    if (jobCard.isVisible()) {
                        return jobCard.textContent();
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Fallback to full page
            return page.textContent("body");

        } catch (Exception e) {
            return "";
        }
    }

    private void printSessionSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("JOB APPLICATION SESSION COMPLETED");
        System.out.println("=".repeat(60));
        System.out.println("Total jobs checked: " + jobsChecked);
        System.out.println("Easy Apply jobs found: " + easyApplyJobsFound);
        System.out.println("Applications submitted: " + applicationsSubmitted);
        System.out.println("=".repeat(60));
    }

    public void setMaxApplications(int maxApplications) {
        this.crawlerConfig.setMaxApplications(maxApplications);
    }

    public void close() {
        if (page != null) {
            page.close();
        }
        if (context != null) {
            context.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private static class JobInfo {

        String title = "";
        String company = "";
    }

    public static void main(String[] args) {
        ReedCrawler crawler = null;

        try {
            crawler = new ReedCrawler();

            crawler.setupBrowser();
            crawler.openForLogin();
            crawler.processJobsAndApply();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (crawler != null) {
                crawler.close();
            }
        }
    }
}
