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
    public void processJobsAndApply() {
        try {
            // Navigate to job search
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

            // Process jobs one by one
            while (applicationsSubmitted < crawlerConfig.getMaxApplications()) {
                JobInfo job = findAndClickNextEasyApplyJob();

                if (job == null) {
                    System.out.println("No more Easy Apply jobs found or reached application limit");
                    break;
                }

                // Wait for job card to update - reduced wait time
                page.waitForTimeout(Math.max(500, crawlerConfig.getJobCardLoadDelay() / 2));

                // Extract and print job description
                if (crawlerConfig.isDebugMode()) {
                    printJobDescriptionFromCard();
                }

                // Extract job description
                String jobDescription = extractJobDescriptionFromCard();

                // Generate CV for this job
                Path generatedCvPath = generateCVForJob(job, jobDescription);

                if (generatedCvPath != null && Files.exists(generatedCvPath)) {
                    // Apply for the job
                    boolean applicationSuccess = applyForJob(generatedCvPath);

                    if (applicationSuccess) {
                        applicationsSubmitted++;
                        System.out.println("Successfully applied to job " + applicationsSubmitted + "/" + crawlerConfig.getMaxApplications());
                    }
                } else {
                    System.out.println("Failed to generate CV for job: " + job.getTitle());
                }

                // Reduced wait time between applications
                page.waitForTimeout(Math.max(1000, crawlerConfig.getApplicationDelay() / 2));
            }

            printSessionSummary();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean performJobSearch() {
        try {
            // First wait a short time for page to fully load
            page.waitForTimeout(500);

            Locator searchInput = page.locator(crawlerConfig.getSearchInputSelector()).first();

            // Check if search input exists and is visible
            if (searchInput == null || !searchInput.isVisible()) {
                System.out.println("Search input not found or not visible");
                return false;
            }

            searchInput.click();
            searchInput.fill(crawlerConfig.getSearchKeywords());
            searchInput.press("Enter");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Reduced wait time for search results
            page.waitForTimeout(Math.max(1000, crawlerConfig.getSearchResultsDelay() / 2));

            // Verify search was successful by checking for job results
            String[] jobSelectors = crawlerConfig.getJobCardSelectors().split(",");
            boolean foundResults = false;

            for (String selector : jobSelectors) {
                try {
                    Locator elements = page.locator(selector.trim());
                    if (elements.count() > 0) {
                        foundResults = true;
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            return foundResults;

        } catch (Exception e) {
            System.out.println("Error performing job search: " + e.getMessage());
            return false;
        }
    }

    private boolean handleLoginIfRequired() {
        try {
            System.out.println("Login may be required. Checking for login elements...");

            // Look for login form elements (username/email input, password input, login button)
            Locator emailInput = page.locator("input[type='email'], input[name='email'], input[id*='email'], input[id*='username']").first();
            Locator passwordInput = page.locator("input[type='password']").first();
            Locator loginButton = page.locator("button[type='submit'], input[type='submit'], button:has-text('Sign in'), button:has-text('Log in')").first();

            if (emailInput != null && emailInput.isVisible()
                    && passwordInput != null && passwordInput.isVisible()
                    && loginButton != null && loginButton.isVisible()) {

                System.out.println("Login form detected. Please provide login credentials to continue.");
                System.out.println("This crawler requires you to manually log in.");

                // Wait for user to complete login manually
                System.out.println("Waiting for 30 seconds for manual login...");
                page.waitForTimeout(30000);

                // Check if login was successful
                boolean loginSuccess = !page.url().contains("login") && !page.url().contains("signin");
                if (loginSuccess) {
                    System.out.println("Login appears successful. Continuing with job search.");
                    return true;
                } else {
                    System.out.println("Login may have failed. Attempting to continue anyway.");
                    return false;
                }
            } else {
                System.out.println("No login form detected, but search failed. Site may be experiencing issues.");
                return false;
            }

        } catch (Exception e) {
            System.out.println("Error handling login: " + e.getMessage());
            return false;
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

                                        System.out.println("✅ Found Easy Apply job: " + job.getTitle() + " (" + easyApplyJobsFound + " Easy Apply jobs found so far)");

                                        element.scrollIntoViewIfNeeded();
                                        // Reduced wait time
                                        page.waitForTimeout(Math.max(200, crawlerConfig.getElementInteractionDelay() / 2));
                                        element.click();
                                        // Reduced wait time
                                        page.waitForTimeout(Math.max(500, crawlerConfig.getJobCardLoadDelay() / 2));

                                        return job;
                                    } else {
                                        JobInfo job = extractJobInfo(element, i);
                                        if (crawlerConfig.isDebugMode()) {
                                            System.out.println("❌ Skipping job (no Easy Apply): " + job.getTitle());
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

            // Check if we need to load more jobs or go to next page
            if (tryLoadMoreJobsOrNextPage()) {
                // Reset jobs checked counter for the new page/batch
                jobsChecked = 0;
                return findAndClickNextEasyApplyJob();
            }

            System.out.println("No more Easy Apply jobs found after checking " + jobsChecked + " jobs");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean tryLoadMoreJobsOrNextPage() {
        try {
            // Try to find and click "Load More" button
            Locator loadMoreButton = page.locator("button:has-text('Load More'), button:has-text('Show More'), a:has-text('Load More')").first();
            if (loadMoreButton != null && loadMoreButton.isVisible()) {
                System.out.println("Clicking 'Load More' button to get more jobs...");
                loadMoreButton.scrollIntoViewIfNeeded();
                loadMoreButton.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(1000);
                return true;
            }

            // Try to find and click "Next Page" button
            Locator nextPageButton = page.locator("a:has-text('Next'), a[aria-label='Next page'], button:has-text('Next')").first();
            if (nextPageButton != null && nextPageButton.isVisible()) {
                System.out.println("Navigating to next page of results...");
                nextPageButton.scrollIntoViewIfNeeded();
                nextPageButton.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(1000);
                return true;
            }

            return false;
        } catch (Exception e) {
            System.out.println("Error trying to load more jobs: " + e.getMessage());
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
                return false;
            }

            // Reduced wait time
            page.waitForTimeout(Math.max(500, crawlerConfig.getApplicationStepDelay() / 2));

            clickUpdateButton();
            // Reduced wait time
            page.waitForTimeout(Math.max(200, crawlerConfig.getElementInteractionDelay() / 2));

            if (!clickChooseOwnCVButton()) {
                System.out.println("Could not find Choose your own CV file button");
                return false;
            }

            // Reduced wait time
            page.waitForTimeout(Math.max(200, crawlerConfig.getElementInteractionDelay() / 2));

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
            // Reduced initial wait time
            page.waitForTimeout(Math.max(300, crawlerConfig.getProcessingStartDelay() / 2));

            for (String selector : processingSelectors) {
                try {
                    Locator processingElement = page.locator(selector.trim()).first();
                    if (processingElement.isVisible()) {
                        System.out.println("CV processing detected, waiting for completion...");
                        // Keep timeout but add polling option for faster response
                        processingElement.waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.HIDDEN)
                                .setTimeout(crawlerConfig.getProcessingTimeout()));
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Reduced wait time after processing
            page.waitForTimeout(Math.max(500, crawlerConfig.getProcessingCompleteDelay() / 2));
            System.out.println("CV processing completed");

        } catch (Exception e) {
            System.out.println("Error waiting for CV processing: " + e.getMessage());
            // Reduced fallback delay
            page.waitForTimeout(Math.max(1000, crawlerConfig.getProcessingFallbackDelay() / 2));
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
            // Reduced confirmation dialog wait time
            page.waitForTimeout(Math.max(500, crawlerConfig.getConfirmationDialogDelay() / 2));

            String[] okSelectors = crawlerConfig.getConfirmationSelectors().split(",");

            for (String selector : okSelectors) {
                try {
                    Locator okButton = page.locator(selector.trim()).first();
                    if (okButton.isVisible()) {
                        System.out.println("Found confirmation dialog, clicking OK...");
                        okButton.click();
                        // Reduced interaction delay
                        page.waitForTimeout(Math.max(200, crawlerConfig.getElementInteractionDelay() / 2));
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

    private String extractJobDescriptionFromCard() {
        try {
            String[] cardSelectors = crawlerConfig.getJobDescriptionSelectors().split(",");
            StringBuilder fullDescription = new StringBuilder();

            for (String cardSelector : cardSelectors) {
                try {
                    Locator jobCard = page.locator(cardSelector.trim()).first();
                    if (jobCard.isVisible()) {
                        // Get main text content
                        fullDescription.append(jobCard.textContent()).append("\n\n");

                        // Extract all article text content
                        Locator allArticleElements = jobCard.locator("article, section, div[class*='description'], div[class*='detail'], div[class*='content'], p");
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

                        return fullDescription.toString();
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // Fallback to full page content
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

            return pageContent.toString();

        } catch (Exception e) {
            return page.textContent("body"); // Ultimate fallback
        }
    }
}
