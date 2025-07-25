// File: src\main\java\com\cvmaker\crawler\ReedCrawler.java
package com.cvmaker.crawler;

import java.nio.file.Files;
import java.nio.file.Path;

import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Reed job site crawler implementation.
 */
public class ReedCrawler extends AbstractJobCrawler {

    private PageVisualizer visualizer;

    public ReedCrawler() throws Exception {
        super();
    }

    public ReedCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
    }

    @Override
    public String getCrawlerName() {
        return "Reed";
    }

    @Override
    public void initialize() throws Exception {
        super.initialize();

        // Enable visualization mode if configured
        if (crawlerConfig.isVisualizationEnabled()) {
            System.out.println("Visualization mode enabled - you will see highlighted elements during operation");
        }
    }

    @Override
    public void setupBrowser() {
        super.setupBrowser();

        // Initialize the visualizer after browser is set up
        this.visualizer = new PageVisualizer(page, crawlerConfig.isVisualizationEnabled());
    }

    @Override
    public void processJobsAndApply() {
        try {
            // Navigate to job search
            visualizer.visualizeAction("Navigating to " + crawlerConfig.getBaseUrl());
            page.navigate(crawlerConfig.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Perform search
            boolean searchSuccess = performJobSearch();

            // Check if search failed due to login requirements
            if (!searchSuccess) {
                if (handleLoginIfRequired()) {
                    // Retry search after login
                    searchSuccess = performJobSearch();
                    if (!searchSuccess) {
                        System.out.println("Search failed even after login attempt. Exiting.");
                        return;
                    }
                } else {
                    System.out.println("Login required but could not complete login. Exiting.");
                    return;
                }
            }

            System.out.println("Starting job search - looking for Easy Apply jobs only...");
            System.out.println("Maximum applications to submit: " + crawlerConfig.getMaxApplications());
            visualizer.visualizeAction("Searching for Easy Apply jobs...");

            // Process jobs one by one
            while (applicationsSubmitted < crawlerConfig.getMaxApplications()) {
                JobInfo job = findAndClickNextEasyApplyJob();

                if (job == null) {
                    System.out.println("No more Easy Apply jobs found or reached application limit");
                    visualizer.visualizeAction("No more Easy Apply jobs found");
                    break;
                }

                // Wait for job card to update - with adjusted timing
                page.waitForTimeout(adjustedDelay(crawlerConfig.getJobCardLoadDelay()));
                visualizer.visualizeAction("Analyzing job: " + job.getTitle());

                // Extract and print job description
                if (crawlerConfig.isDebugMode()) {
                    printJobDescriptionFromCard();
                }

                // Extract job description
                String jobDescription = extractJobDescriptionFromCard();
                visualizer.visualizeAction("Extracted job description (" + jobDescription.length() + " chars)");

                // Generate CV for this job
                visualizer.visualizeAction("Generating tailored CV...");
                Path generatedCvPath = generateCVForJob(job, jobDescription);

                if (generatedCvPath != null && Files.exists(generatedCvPath)) {
                    // Apply for the job
                    visualizer.visualizeAction("Starting application process");
                    boolean applicationSuccess = applyForJob(generatedCvPath);

                    if (applicationSuccess) {
                        applicationsSubmitted++;
                        visualizer.visualizeAction("Application submitted! (" + applicationsSubmitted + "/"
                                + crawlerConfig.getMaxApplications() + ")");
                        System.out.println("Successfully applied to job " + applicationsSubmitted
                                + "/" + crawlerConfig.getMaxApplications());
                    } else {
                        visualizer.visualizeAction("Application failed");
                    }
                } else {
                    System.out.println("Failed to generate CV for job: " + job.getTitle());
                    visualizer.visualizeAction("CV generation failed");
                }

                // Wait between applications - with adjusted timing
                page.waitForTimeout(adjustedDelay(crawlerConfig.getApplicationDelay()));
            }

            printSessionSummary();
            visualizer.visualizeAction("Crawler session completed");

        } catch (Exception e) {
            e.printStackTrace();
            visualizer.visualizeAction("Error: " + e.getMessage());
        }
    }

    private boolean performJobSearch() {
        try {
            // First wait a short time for page to fully load
            page.waitForTimeout(adjustedDelay(500));

            visualizer.visualizeAction("Looking for search input");
            visualizer.highlightElements(crawlerConfig.getSearchInputSelector());
            Locator searchInput = page.locator(crawlerConfig.getSearchInputSelector()).first();

            // Check if search input exists and is visible
            if (searchInput == null || !searchInput.isVisible()) {
                System.out.println("Search input not found or not visible");
                visualizer.visualizeAction("Search input not found");
                return false;
            }

            searchInput.click();
            visualizer.visualizeAction("Typing: " + crawlerConfig.getSearchKeywords());
            searchInput.fill(crawlerConfig.getSearchKeywords());
            searchInput.press("Enter");
            visualizer.visualizeAction("Searching...");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Wait for search results - with adjusted timing
            page.waitForTimeout(adjustedDelay(crawlerConfig.getSearchResultsDelay()));

            // Verify search was successful by checking for job results
            String[] jobSelectors = crawlerConfig.getJobCardSelectors().split(",");
            boolean foundResults = false;

            for (String selector : jobSelectors) {
                try {
                    visualizer.highlightElements(selector.trim());
                    Locator elements = page.locator(selector.trim());
                    if (elements.count() > 0) {
                        foundResults = true;
                        visualizer.visualizeAction("Found " + elements.count() + " job results");
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            return foundResults;

        } catch (Exception e) {
            System.out.println("Error performing job search: " + e.getMessage());
            visualizer.visualizeAction("Search error: " + e.getMessage());
            return false;
        }
    }

    private boolean handleLoginIfRequired() {
        try {
            System.out.println("Login may be required. Checking for login elements...");
            visualizer.visualizeAction("Checking if login is required");

            // Look for login form elements (username/email input, password input, login button)
            Locator emailInput = page.locator("input[type='email'], input[name='email'], input[id*='email'], input[id*='username']").first();
            Locator passwordInput = page.locator("input[type='password']").first();
            Locator loginButton = page.locator("button[type='submit'], input[type='submit'], button:has-text('Sign in'), button:has-text('Log in')").first();

            if (emailInput != null && emailInput.isVisible()
                    && passwordInput != null && passwordInput.isVisible()
                    && loginButton != null && loginButton.isVisible()) {

                System.out.println("Login form detected. Please provide login credentials to continue.");
                System.out.println("This crawler requires you to manually log in.");
                visualizer.visualizeAction("Please log in manually");

                // Highlight login elements to help user
                visualizer.highlightElements("input[type='email'], input[name='email'], input[id*='email'], input[id*='username']");
                visualizer.highlightElements("input[type='password']");
                visualizer.highlightElements("button[type='submit'], input[type='submit'], button:has-text('Sign in'), button:has-text('Log in')");

                // Wait for user to complete login manually
                System.out.println("Waiting for 30 seconds for manual login...");
                for (int i = 30; i > 0; i--) {
                    if (i % 5 == 0) {
                        visualizer.visualizeAction("Waiting for login: " + i + " seconds left");
                    }
                    page.waitForTimeout(1000);
                }

                // Check if login was successful
                boolean loginSuccess = !page.url().contains("login") && !page.url().contains("signin");
                if (loginSuccess) {
                    System.out.println("Login appears successful. Continuing with job search.");
                    visualizer.visualizeAction("Login successful");
                    return true;
                } else {
                    System.out.println("Login may have failed. Attempting to continue anyway.");
                    visualizer.visualizeAction("Login may have failed");
                    return false;
                }
            } else {
                System.out.println("No login form detected, but search failed. Site may be experiencing issues.");
                visualizer.visualizeAction("No login form found");
                return false;
            }

        } catch (Exception e) {
            System.out.println("Error handling login: " + e.getMessage());
            visualizer.visualizeAction("Login handling error");
            return false;
        }
    }

    private JobInfo findAndClickNextEasyApplyJob() {
        try {
            String[] jobSelectors = crawlerConfig.getJobCardSelectors().split(",");
            System.out.println("Looking for Easy Apply jobs...");
            visualizer.visualizeAction("Scanning for Easy Apply jobs");

            for (String selector : jobSelectors) {
                try {
                    visualizer.highlightElements(selector.trim());
                    Locator elements = page.locator(selector.trim());
                    int count = elements.count();

                    if (count > 0) {
                        if (crawlerConfig.isDebugMode()) {
                            System.out.println("Found " + count + " job elements with selector: " + selector);
                        }
                        visualizer.visualizeAction("Found " + count + " job cards");

                        // Check each job card for Easy Apply button
                        for (int i = jobsChecked; i < count; i++) {
                            try {
                                Locator element = elements.nth(i);
                                jobsChecked = i + 1;

                                if (element.isVisible()) {
                                    visualizer.visualizeAction("Checking job " + (i + 1) + " for Easy Apply");
                                    visualizer.highlightElements(selector + ":nth-child(" + (i + 1) + ")");

                                    if (hasEasyApplyButton(element)) {
                                        easyApplyJobsFound++;
                                        JobInfo job = extractJobInfo(element, i);

                                        System.out.println("✓ Found Easy Apply job: " + job.getTitle()
                                                + " (" + easyApplyJobsFound + " Easy Apply jobs found so far)");
                                        visualizer.visualizeAction("Found Easy Apply job: " + job.getTitle());

                                        element.scrollIntoViewIfNeeded();
                                        // Adjusted wait time
                                        page.waitForTimeout(adjustedDelay(crawlerConfig.getElementInteractionDelay()));
                                        element.click();
                                        visualizer.visualizeAction("Opening job details");
                                        // Adjusted wait time
                                        page.waitForTimeout(adjustedDelay(crawlerConfig.getJobCardLoadDelay()));

                                        return job;
                                    } else {
                                        JobInfo job = extractJobInfo(element, i);
                                        if (crawlerConfig.isDebugMode()) {
                                            System.out.println("✗ Skipping job (no Easy Apply): " + job.getTitle());
                                        }
                                        visualizer.visualizeAction("Not Easy Apply, skipping");
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

            // Check if we need to load more jobs or go to next page
            visualizer.visualizeAction("Checking for more jobs or next page");
            if (tryLoadMoreJobsOrNextPage()) {
                // Reset jobs checked counter for the new page/batch
                jobsChecked = 0;
                return findAndClickNextEasyApplyJob();
            }

            System.out.println("No more Easy Apply jobs found after checking " + jobsChecked + " jobs");
            visualizer.visualizeAction("No more Easy Apply jobs found");

        } catch (Exception e) {
            e.printStackTrace();
            visualizer.visualizeAction("Error finding jobs: " + e.getMessage());
        }

        return null;
    }

    private boolean tryLoadMoreJobsOrNextPage() {
        try {
            // Try to find and click "Load More" button
            Locator loadMoreButton = page.locator("button:has-text('Load More'), button:has-text('Show More'), a:has-text('Load More')").first();
            if (loadMoreButton != null && loadMoreButton.isVisible()) {
                System.out.println("Clicking 'Load More' button to get more jobs...");
                visualizer.visualizeAction("Loading more jobs");
                visualizer.highlightElements("button:has-text('Load More'), button:has-text('Show More'), a:has-text('Load More')");
                loadMoreButton.scrollIntoViewIfNeeded();
                loadMoreButton.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(adjustedDelay(1000));
                return true;
            }

            // Try to find and click "Next Page" button
            Locator nextPageButton = page.locator("a:has-text('Next'), a[aria-label='Next page'], button:has-text('Next')").first();
            if (nextPageButton != null && nextPageButton.isVisible()) {
                System.out.println("Navigating to next page of results...");
                visualizer.visualizeAction("Going to next page");
                visualizer.highlightElements("a:has-text('Next'), a[aria-label='Next page'], button:has-text('Next')");
                nextPageButton.scrollIntoViewIfNeeded();
                nextPageButton.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(adjustedDelay(1000));
                return true;
            }

            return false;
        } catch (Exception e) {
            System.out.println("Error trying to load more jobs: " + e.getMessage());
            visualizer.visualizeAction("Error loading more jobs");
            return false;
        }
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
                        visualizer.highlightElements(selector);
                        visualizer.visualizeAction("Easy Apply button found");
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
                            visualizer.visualizeAction("Easy Apply keyword found: " + keyword);
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
                job.setTitle(lines[0].trim());
                if (lines.length > 1) {
                    job.setCompany(lines[1].trim());
                }
            }
        } catch (Exception e) {
            job.setTitle("Job " + index);
            job.setCompany("Company");
        }

        return job;
    }

    private boolean applyForJob(Path cvPath) {
        try {
            System.out.println("Starting job application process...");

            if (!clickApplyNowButton()) {
                System.out.println("Could not find Apply Now button");
                visualizer.visualizeAction("Apply Now button not found");
                return false;
            }

            // Adjusted wait time
            page.waitForTimeout(adjustedDelay(crawlerConfig.getApplicationStepDelay()));

            clickUpdateButton();
            // Adjusted wait time
            page.waitForTimeout(adjustedDelay(crawlerConfig.getElementInteractionDelay()));

            if (!clickChooseOwnCVButton()) {
                System.out.println("Could not find Choose your own CV file button");
                visualizer.visualizeAction("CV upload button not found");
                return false;
            }

            // Adjusted wait time
            page.waitForTimeout(adjustedDelay(crawlerConfig.getElementInteractionDelay()));

            if (!uploadCVFile(cvPath)) {
                System.out.println("Failed to upload CV file");
                visualizer.visualizeAction("CV upload failed");
                return false;
            }

            waitForCVProcessing();

            if (!submitApplication()) {
                System.out.println("Failed to submit application");
                visualizer.visualizeAction("Submit application failed");
                return false;
            }

            handleConfirmationDialog();

            System.out.println("Job application completed successfully!");
            visualizer.visualizeAction("Application completed successfully!");
            return true;

        } catch (Exception e) {
            System.out.println("Error during job application: " + e.getMessage());
            visualizer.visualizeAction("Application error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean clickApplyNowButton() {
        String[] applySelectors = crawlerConfig.getApplyButtonSelectors().split(",");

        for (String selector : applySelectors) {
            try {
                visualizer.highlightElements(selector);
                Locator applyButton = page.locator(selector.trim()).first();
                if (applyButton.isVisible()) {
                    System.out.println("Found Apply Now button with selector: " + selector);
                    visualizer.visualizeAction("Clicking Apply Now");
                    applyButton.scrollIntoViewIfNeeded();
                    applyButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        visualizer.visualizeAction("Apply Now button not found");
        return false;
    }

    private void clickUpdateButton() {
        String[] updateSelectors = crawlerConfig.getUpdateButtonSelectors().split(",");

        for (String selector : updateSelectors) {
            try {
                visualizer.highlightElements(selector);
                Locator updateButton = page.locator(selector.trim()).first();
                if (updateButton.isVisible()) {
                    System.out.println("Found Update button, clicking...");
                    visualizer.visualizeAction("Clicking Update button");
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
        visualizer.visualizeAction("No Update button found (this may be normal)");
    }

    private boolean clickChooseOwnCVButton() {
        String[] cvSelectors = crawlerConfig.getCvUploadSelectors().split(",");

        for (String selector : cvSelectors) {
            try {
                visualizer.highlightElements(selector);
                Locator cvButton = page.locator(selector.trim()).first();
                if (cvButton.isVisible()) {
                    System.out.println("Found Choose CV button with selector: " + selector);
                    visualizer.visualizeAction("Clicking Choose CV button");
                    cvButton.scrollIntoViewIfNeeded();
                    cvButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        visualizer.visualizeAction("Choose CV button not found");
        return false;
    }

    private boolean uploadCVFile(Path cvPath) {
        try {
            String[] fileInputSelectors = crawlerConfig.getFileInputSelectors().split(",");

            for (String selector : fileInputSelectors) {
                try {
                    visualizer.highlightElements(selector);
                    Locator fileInput = page.locator(selector.trim()).first();
                    if (fileInput.count() > 0) {
                        System.out.println("Found file input, uploading CV: " + cvPath.toAbsolutePath());
                        visualizer.visualizeAction("Uploading CV file");
                        fileInput.setInputFiles(cvPath);
                        return true;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            System.out.println("No file input found for CV upload");
            visualizer.visualizeAction("File input not found");
            return false;

        } catch (Exception e) {
            System.out.println("Error uploading CV file: " + e.getMessage());
            visualizer.visualizeAction("Upload error: " + e.getMessage());
            return false;
        }
    }

    private void waitForCVProcessing() {
        try {
            System.out.println("Waiting for CV processing to complete...");
            visualizer.visualizeAction("Waiting for CV processing");

            String[] processingSelectors = crawlerConfig.getProcessingSelectors().split(",");
            // Adjusted initial wait time
            page.waitForTimeout(adjustedDelay(crawlerConfig.getProcessingStartDelay()));

            for (String selector : processingSelectors) {
                try {
                    visualizer.highlightElements(selector);
                    Locator processingElement = page.locator(selector.trim()).first();
                    if (processingElement.isVisible()) {
                        System.out.println("CV processing detected, waiting for completion...");
                        visualizer.visualizeAction("CV processing in progress...");
                        // Use polling rate from config
                        processingElement.waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.HIDDEN)
                                .setTimeout(crawlerConfig.getProcessingTimeout()));
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Adjusted wait time after processing
            page.waitForTimeout(adjustedDelay(crawlerConfig.getProcessingCompleteDelay()));
            System.out.println("CV processing completed");
            visualizer.visualizeAction("CV processing completed");

        } catch (Exception e) {
            System.out.println("Error waiting for CV processing: " + e.getMessage());
            visualizer.visualizeAction("Processing error: " + e.getMessage());
            // Adjusted fallback delay
            page.waitForTimeout(adjustedDelay(crawlerConfig.getProcessingFallbackDelay()));
        }
    }

    private boolean submitApplication() {
        String[] submitSelectors = crawlerConfig.getSubmitButtonSelectors().split(",");

        for (String selector : submitSelectors) {
            try {
                visualizer.highlightElements(selector);
                Locator submitButton = page.locator(selector.trim()).first();
                if (submitButton.isVisible()) {
                    System.out.println("Found Submit button with selector: " + selector);
                    visualizer.visualizeAction("Submitting application");
                    submitButton.scrollIntoViewIfNeeded();
                    submitButton.click();
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }

        System.out.println("No Submit Application button found");
        visualizer.visualizeAction("Submit button not found");
        return false;
    }

    private void handleConfirmationDialog() {
        try {
            // Adjusted confirmation dialog wait time
            page.waitForTimeout(adjustedDelay(crawlerConfig.getConfirmationDialogDelay()));
            visualizer.visualizeAction("Checking for confirmation dialog");

            String[] okSelectors = crawlerConfig.getConfirmationSelectors().split(",");

            for (String selector : okSelectors) {
                try {
                    visualizer.highlightElements(selector);
                    Locator okButton = page.locator(selector.trim()).first();
                    if (okButton.isVisible()) {
                        System.out.println("Found confirmation dialog, clicking OK...");
                        visualizer.visualizeAction("Clicking OK on confirmation dialog");
                        okButton.click();
                        // Adjusted interaction delay
                        page.waitForTimeout(adjustedDelay(crawlerConfig.getElementInteractionDelay()));
                        return;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (crawlerConfig.isDebugMode()) {
                System.out.println("No confirmation dialog found");
            }
            visualizer.visualizeAction("No confirmation dialog found");

        } catch (Exception e) {
            System.out.println("Error handling confirmation dialog: " + e.getMessage());
            visualizer.visualizeAction("Confirmation dialog error");
        }
    }

    private void printJobDescriptionFromCard() {
        try {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("DEBUG: JOB DESCRIPTION FROM UPDATED CARD");
            System.out.println("=".repeat(80));

            String[] cardSelectors = crawlerConfig.getJobDescriptionSelectors().split(",");
            boolean foundDescription = false;

            // First try the specific class you mentioned
            try {
                String specificSelector = ".job-details-drawer-modal_jobSection__42ckh.job-details-drawer-modal_jobDescription__r4Xn1";
                visualizer.highlightElements(specificSelector);
                Locator specificJobDesc = page.locator(specificSelector).first();

                if (specificJobDesc != null && specificJobDesc.isVisible()) {
                    String content = specificJobDesc.textContent();
                    System.out.println("FOUND SPECIFIC JOB DESCRIPTION SELECTOR: " + specificSelector);
                    System.out.println("CONTENT LENGTH: " + content.length() + " characters");

                    if (content.length() > 1500) {
                        System.out.println(content.substring(0, 1500) + "...[TRUNCATED]");
                    } else {
                        System.out.println(content);
                    }

                    foundDescription = true;
                }
            } catch (Exception e) {
                // Continue to other selectors
            }

            // If specific selector didn't work, try the configured ones
            if (!foundDescription) {
                for (String cardSelector : cardSelectors) {
                    try {
                        visualizer.highlightElements(cardSelector);
                        Locator jobCard = page.locator(cardSelector.trim()).first();
                        if (jobCard != null && jobCard.isVisible()) {
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

    private String extractJobDescriptionFromCard() {
        try {
            StringBuilder fullDescription = new StringBuilder();
            visualizer.visualizeAction("Extracting job description");

            // First try the specific Reed class you mentioned
            try {
                // Try with the specific class names mentioned
                String specificSelector = ".job-details-drawer-modal_jobSection__42ckh.job-details-drawer-modal_jobDescription__r4Xn1";
                visualizer.highlightElements(specificSelector);
                Locator specificJobDesc = page.locator(specificSelector).first();

                if (specificJobDesc != null && specificJobDesc.isVisible()) {
                    fullDescription.append(specificJobDesc.textContent()).append("\n\n");
                    System.out.println("Found job description using specific Reed selector.");
                    visualizer.visualizeAction("Found job description with specific selector");
                    return fullDescription.toString();
                }

                // Try similar classes that might match
                String partialSelector1 = "[class*='jobSection'][class*='jobDescription']";
                String partialSelector2 = "[class*='job-details'][class*='description']";

                for (String selector : new String[]{partialSelector1, partialSelector2}) {
                    visualizer.highlightElements(selector);
                    Locator element = page.locator(selector).first();
                    if (element != null && element.isVisible()) {
                        fullDescription.append(element.textContent()).append("\n\n");
                        System.out.println("Found job description using partial selector: " + selector);
                        visualizer.visualizeAction("Found job description with partial selector");
                        return fullDescription.toString();
                    }
                }
            } catch (Exception e) {
                // Continue to other selectors
                System.out.println("Could not find specific Reed job description element, trying alternatives");
            }

            // Then try the configured selectors
            String[] cardSelectors = crawlerConfig.getJobDescriptionSelectors().split(",");
            for (String cardSelector : cardSelectors) {
                try {
                    visualizer.highlightElements(cardSelector.trim());
                    Locator jobCard = page.locator(cardSelector.trim()).first();
                    if (jobCard != null && jobCard.isVisible()) {
                        // Get main text content
                        fullDescription.append(jobCard.textContent()).append("\n\n");

                        // Extract all article text content
                        Locator allArticleElements = jobCard.locator("article, section, div[class*='description'], div[class*='detail'], div[class*='content'], p");
                        visualizer.highlightElements(cardSelector + " article, " + cardSelector + " section, "
                                + cardSelector + " div[class*='description'], " + cardSelector + " div[class*='detail'], "
                                + cardSelector + " div[class*='content'], " + cardSelector + " p");

                        int elementsCount = allArticleElements.count();

                        if (elementsCount > 0) {
                            for (int i = 0; i < elementsCount; i++) {
                                String elementText = allArticleElements.nth(i).textContent();
                                if (elementText != null && !elementText.trim().isEmpty()) {
                                    fullDescription.append(elementText).append("\n\n");
                                }
                            }
                        }

                        // Also try to extract all paragraph text
                        Locator paragraphs = jobCard.locator("p, li, h1, h2, h3, h4, h5, h6");
                        int pCount = paragraphs.count();

                        if (pCount > 0) {
                            for (int i = 0; i < pCount; i++) {
                                String pText = paragraphs.nth(i).textContent();
                                if (pText != null && !pText.trim().isEmpty()) {
                                    fullDescription.append(pText).append("\n");
                                }
                            }
                        }

                        visualizer.visualizeAction("Description extracted successfully");
                        return fullDescription.toString();
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Fallback to common job description containers
            String[] commonJobDescSelectors = {
                "[class*='job-description']",
                "[class*='jobDescription']",
                "[class*='job-details']",
                "[class*='jobDetails']",
                "[class*='description-container']",
                "[id*='job-description']",
                "[id*='jobDescription']",
                "[data-testid*='description']",
                "[data-qa*='description']"
            };

            for (String selector : commonJobDescSelectors) {
                try {
                    visualizer.highlightElements(selector);
                    Locator element = page.locator(selector).first();
                    if (element != null && element.isVisible()) {
                        String text = element.textContent();
                        if (text != null && text.length() > 100) {
                            fullDescription.append(text).append("\n\n");
                            System.out.println("Found job description using common selector: " + selector);
                            visualizer.visualizeAction("Found description with common selector");
                            return fullDescription.toString();
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Ultimate fallback to full page content
            visualizer.visualizeAction("Using fallback method for description");
            StringBuilder pageContent = new StringBuilder();

            // Get main body content
            pageContent.append(page.textContent("body")).append("\n\n");

            // Try to extract job description from common containers
            String[] descContainers = {
                ".job-description",
                "#job-description",
                "[class*='description']",
                "[class*='job-details']",
                "article",
                "section"
            };

            for (String container : descContainers) {
                try {
                    visualizer.highlightElements(container);
                    Locator elements = page.locator(container);
                    int count = elements.count();

                    for (int i = 0; i < count; i++) {
                        String text = elements.nth(i).textContent();
                        if (text != null && text.length() > 100) { // Only include substantial content
                            pageContent.append(text).append("\n\n");
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (pageContent.length() > 0) {
                return pageContent.toString();
            }

            // If all else fails, just return body text
            visualizer.visualizeAction("Using body text as fallback");
            return page.textContent("body");

        } catch (Exception e) {
            visualizer.visualizeAction("Error extracting description");
            System.out.println("Error extracting job description: " + e.getMessage());
            return page.textContent("body"); // Ultimate fallback
        }
    }
}
