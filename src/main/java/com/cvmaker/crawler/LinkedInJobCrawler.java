package com.cvmaker.crawler;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.bonigarcia.wdm.WebDriverManager;

public class LinkedInJobCrawler {

    public static class JobListing {

        private String title;
        private String company;
        private String location;
        private String url;
        private String jobId;
        private String datePosted;
        private String searchDate;

        public JobListing(String title, String company, String location, String url) {
            this.title = title != null ? title.trim() : "";
            this.company = company != null ? company.trim() : "";
            this.location = location != null ? location.trim() : "";
            this.url = url != null ? url.trim() : "";
            this.jobId = extractJobId(url);
            this.datePosted = "";
            this.searchDate = LocalDateTime.now().toString();
        }

        private String extractJobId(String url) {
            if (url != null && url.contains("/jobs/view/")) {
                try {
                    String[] parts = url.split("/jobs/view/");
                    if (parts.length > 1) {
                        return parts[1].replaceAll("[^0-9].*$", "");
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            return "";
        }

        @Override
        public String toString() {
            return "Job: " + title + "\nCompany: " + company + "\nLocation: " + location + "\nURL: " + url;
        }

        // Getters for JSON export
        public String getTitle() {
            return title;
        }

        public String getCompany() {
            return company;
        }

        public String getLocation() {
            return location;
        }

        public String getUrl() {
            return url;
        }

        public String getJobId() {
            return jobId;
        }

        public String getDatePosted() {
            return datePosted;
        }

        public String getSearchDate() {
            return searchDate;
        }
    }

    private WebDriver driver;
    private boolean debug;
    private Set<String> processedUrls = new HashSet<>();
    private List<JobListing> allJobs = new ArrayList<>();
    private static final int WAIT_SECONDS = 30;
    private AiService aiService;
    private Random random = new Random();

    public LinkedInJobCrawler(boolean debug) {
        this.debug = debug;
        setupDriver(getUserDataDirectory());
    }

    public LinkedInJobCrawler(boolean debug, String customProfilePath) {
        this.debug = debug;
        setupDriver(customProfilePath);
    }

    private void setupDriver(String profilePath) {
        try {
            log("Setting up WebDriver...");
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();

            // Profile settings
            options.addArguments("--user-data-dir=" + profilePath);
            options.addArguments("--profile-directory=Default");

            // Essential arguments
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--start-maximized");

            // Remove automation detection
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);

            log("Using Chrome profile from: " + profilePath);

            driver = new ChromeDriver(options);

            // Remove webdriver property
            ((JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
            );

            log("WebDriver setup completed successfully");

        } catch (Exception e) {
            log("ERROR setting up driver: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize WebDriver", e);
        }
    }

    private String getUserDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            return userHome + "\\AppData\\Local\\Google\\Chrome\\User Data";
        } else if (os.contains("mac")) {
            return userHome + "/Library/Application Support/Google/Chrome";
        } else {
            return userHome + "/.config/google-chrome";
        }
    }

    private void log(String message) {
        if (debug) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            System.out.println("[" + timestamp + "] " + message);
        }
    }

    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void randomWait() {
        sleep(2000 + random.nextInt(3000));
    }

    public List<JobListing> searchJobs(String keyword, String location, int maxPages) {
        log("Starting job search for: '" + keyword + "' in '" + location + "'");
        allJobs.clear();
        processedUrls.clear();

        try {
            // Step 1: Go to LinkedIn
            log("Step 1: Navigating to LinkedIn...");
            driver.get("https://uk.indeed.com/");
            sleep(5000);

            log("Current URL after loading LinkedIn: " + driver.getCurrentUrl());
            log("Page title: " + driver.getTitle());

            // Step 2: Check if logged in
            log("Step 2: Checking authentication...");
            if (!checkAndWaitForLogin()) {
                log("FAILED: Could not authenticate with LinkedIn");
                return allJobs;
            }
            log("SUCCESS: Authenticated with LinkedIn");

            // Step 3: Navigate to jobs
            log("Step 3: Navigating to jobs page...");
            String jobsUrl = buildJobsUrl(keyword, location);
            log("Jobs URL: " + jobsUrl);
            driver.get(jobsUrl);
            sleep(5000);

            log("Current URL after jobs search: " + driver.getCurrentUrl());

            // Step 4: Wait for and verify job results
            log("Step 4: Waiting for job results to load...");
            if (!waitForJobResults()) {
                log("FAILED: Could not find job results");
                debugPageContent();
                return allJobs;
            }
            log("SUCCESS: Job results loaded");

            // Step 5: Extract jobs from pages
            for (int page = 1; page <= maxPages; page++) {
                log("Step 5." + page + ": Processing page " + page);

                int jobsOnPage = extractJobsFromCurrentPage();
                log("Extracted " + jobsOnPage + " jobs from page " + page);

                if (page < maxPages) {
                    log("Attempting to navigate to next page...");
                    if (!navigateToNextPage()) {
                        log("Could not navigate to page " + (page + 1) + ". Stopping.");
                        break;
                    }
                }
            }

            log("COMPLETED: Found " + allJobs.size() + " total jobs");
            return allJobs;

        } catch (Exception e) {
            log("ERROR during job search: " + e.getMessage());
            e.printStackTrace();
            debugPageContent();
            return allJobs;
        }
    }

    private boolean checkAndWaitForLogin() {
        try {
            // Wait for page to fully load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            for (int attempt = 1; attempt <= 3; attempt++) {
                log("Login check attempt " + attempt + "/3");

                if (isLoggedIn()) {
                    log("Already logged in!");
                    return true;
                }

                if (attempt == 1) {
                    log("Not logged in. Please log in manually...");
                    log("Waiting 30 seconds for you to log in...");
                    sleep(30000);
                } else {
                    log("Still not logged in. Waiting another 15 seconds...");
                    sleep(15000);
                }
            }

            return isLoggedIn();

        } catch (Exception e) {
            log("Error checking login: " + e.getMessage());
            return false;
        }
    }

    private boolean isLoggedIn() {
        try {
            String currentUrl = driver.getCurrentUrl();
            log("Checking login status. Current URL: " + currentUrl);

            // Check URL first
            if (currentUrl.contains("/login") || currentUrl.contains("/guest") || currentUrl.contains("/uas/login")) {
                log("URL indicates not logged in: " + currentUrl);
                return false;
            }

            // Look for profile/me elements
            List<WebElement> profileElements = driver.findElements(By.cssSelector(
                    ".global-nav__me, "
                    + ".global-nav__me-photo, "
                    + "[data-control-name='nav.settings_and_privacy'], "
                    + ".nav-item__profile-member-photo, "
                    + "[data-test-global-nav-me]"
            ));

            boolean hasProfileElement = !profileElements.isEmpty();
            log("Profile elements found: " + hasProfileElement + " (count: " + profileElements.size() + ")");

            // Also check for sign-in elements (shouldn't exist if logged in)
            List<WebElement> signInElements = driver.findElements(By.cssSelector(
                    "[data-control-name='guest_homepage-basic_nav-header-signin'], "
                    + ".nav__button-secondary, "
                    + "a[href*='/login']"
            ));

            boolean hasSignInElements = !signInElements.isEmpty();
            log("Sign-in elements found: " + hasSignInElements + " (count: " + signInElements.size() + ")");

            return hasProfileElement && !hasSignInElements;

        } catch (Exception e) {
            log("Error checking login status: " + e.getMessage());
            return false;
        }
    }

    private String buildJobsUrl(String keyword, String location) {
        try {
            StringBuilder url = new StringBuilder("https://www.linkedin.com/jobs/search/?");

            if (keyword != null && !keyword.trim().isEmpty()) {
                url.append("keywords=").append(java.net.URLEncoder.encode(keyword.trim(), "UTF-8"));
            }

            if (location != null && !location.trim().isEmpty()) {
                url.append("&location=").append(java.net.URLEncoder.encode(location.trim(), "UTF-8"));
            }

            // Additional parameters for better results
            url.append("&f_TPR=r604800"); // Past week
            url.append("&sortBy=DD"); // Sort by date
            url.append("&start=0"); // Start from first result

            return url.toString();

        } catch (Exception e) {
            log("Error building jobs URL: " + e.getMessage());
            return "https://www.linkedin.com/jobs/search/";
        }
    }

    private boolean waitForJobResults() {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS));

            log("Waiting for job results container...");

            // Wait for any of these containers to appear
            String[] containerSelectors = {
                ".jobs-search__results-list",
                ".scaffold-layout__list",
                ".jobs-search-results-list",
                "[data-testid='jobs-search-results-list']"
            };

            for (String selector : containerSelectors) {
                try {
                    log("Trying selector: " + selector);
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                    log("Found container with selector: " + selector);

                    // Wait a bit more for content to load
                    sleep(3000);
                    return true;

                } catch (Exception e) {
                    log("Selector " + selector + " not found, trying next...");
                }
            }

            log("No job results container found with any selector");
            return false;

        } catch (Exception e) {
            log("Error waiting for job results: " + e.getMessage());
            return false;
        }
    }

    private int extractJobsFromCurrentPage() {
        int jobsFound = 0;

        try {
            log("Starting job extraction from current page...");

            // Scroll to load all content
            scrollPageGradually();

            // Find job elements
            List<WebElement> jobElements = findJobElements();
            log("Found " + jobElements.size() + " job elements on page");

            if (jobElements.isEmpty()) {
                debugPageContent();
                return 0;
            }

            // Extract each job
            for (int i = 0; i < jobElements.size(); i++) {
                try {
                    log("Extracting job " + (i + 1) + "/" + jobElements.size());
                    if (extractJobFromElement(jobElements.get(i))) {
                        jobsFound++;
                    }
                } catch (Exception e) {
                    log("Error extracting job " + (i + 1) + ": " + e.getMessage());
                }
            }

            log("Successfully extracted " + jobsFound + " jobs from page");
            return jobsFound;

        } catch (Exception e) {
            log("Error extracting jobs from page: " + e.getMessage());
            e.printStackTrace();
            return jobsFound;
        }
    }

    private void scrollPageGradually() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Get page dimensions
            long totalHeight = (Long) js.executeScript("return document.body.scrollHeight");
            long viewportHeight = (Long) js.executeScript("return window.innerHeight");

            log("Page height: " + totalHeight + ", Viewport height: " + viewportHeight);

            // Scroll gradually
            for (long position = 0; position < totalHeight; position += viewportHeight / 2) {
                js.executeScript("window.scrollTo(0, " + position + ")");
                sleep(1500);

                // Try to load more content
                tryLoadMoreJobs();
            }

            // Scroll back to top
            js.executeScript("window.scrollTo(0, 0)");
            sleep(2000);

        } catch (Exception e) {
            log("Error during scrolling: " + e.getMessage());
        }
    }

    private void tryLoadMoreJobs() {
        try {
            List<WebElement> loadMoreButtons = driver.findElements(By.cssSelector(
                    ".infinite-scroller__show-more-button:not([disabled]), "
                    + ".see-more-jobs:not([disabled]), "
                    + "button[aria-label*='Show more']:not([disabled]), "
                    + "button[aria-label*='more results']:not([disabled])"
            ));

            for (WebElement button : loadMoreButtons) {
                if (button.isDisplayed() && button.isEnabled()) {
                    log("Clicking 'Load More' button");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", button);
                    sleep(3000);
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore - load more is optional
        }
    }

    private List<WebElement> findJobElements() {
        String[] selectors = {
            ".jobs-search__results-list .job-search-card",
            ".jobs-search__results-list li",
            ".scaffold-layout__list .scaffold-layout__list-item",
            ".jobs-search-results-list .jobs-search-results__list-item",
            ".job-search-card",
            "[data-testid='job-search-card']"
        };

        for (String selector : selectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                if (!elements.isEmpty()) {
                    log("Found " + elements.size() + " job elements using selector: " + selector);
                    return elements;
                }
            } catch (Exception e) {
                log("Error with selector " + selector + ": " + e.getMessage());
            }
        }

        log("WARNING: No job elements found with any selector");
        return new ArrayList<>();
    }

    private boolean extractJobFromElement(WebElement jobElement) {
        try {
            // Extract job details
            String title = extractText(jobElement,
                    ".job-search-card__title a, "
                    + ".job-search-card__title, "
                    + "h3 a, "
                    + ".base-search-card__title a, "
                    + "[data-testid='job-title'] a"
            );

            String company = extractText(jobElement,
                    ".job-search-card__subtitle-link, "
                    + ".job-search-card__subtitle, "
                    + ".base-search-card__subtitle a, "
                    + ".hidden-nested-link, "
                    + "[data-testid='company-name']"
            );

            String location = extractText(jobElement,
                    ".job-search-card__location, "
                    + ".base-search-card__metadata, "
                    + "[data-testid='job-location']"
            );

            String url = extractJobUrl(jobElement);

            // Log what we found
            log("Raw extraction - Title: '" + title + "', Company: '" + company + "', Location: '" + location + "', URL: '" + url + "'");

            // Validate
            if (title.isEmpty()) {
                log("Skipping job - no title found");
                return false;
            }

            if (url.isEmpty()) {
                log("Skipping job - no URL found");
                return false;
            }

            if (processedUrls.contains(url)) {
                log("Skipping job - already processed: " + url);
                return false;
            }

            // Create job listing
            JobListing job = new JobListing(title, company, location, url);
            allJobs.add(job);
            processedUrls.add(url);

            log("✓ Successfully extracted: " + title + " at " + (company.isEmpty() ? "Unknown Company" : company));
            return true;

        } catch (Exception e) {
            log("Error extracting job: " + e.getMessage());
            return false;
        }
    }

    private String extractText(WebElement parent, String selectors) {
        String[] selectorArray = selectors.split(", ");

        for (String selector : selectorArray) {
            try {
                WebElement element = parent.findElement(By.cssSelector(selector.trim()));
                String text = element.getText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            } catch (Exception e) {
                // Try next selector
            }
        }

        return "";
    }

    private String extractJobUrl(WebElement jobElement) {
        String[] selectors = {
            ".job-search-card__title a",
            "h3 a",
            ".base-search-card__title a",
            "a[href*='/jobs/view/']",
            "[data-testid='job-title'] a"
        };

        for (String selector : selectors) {
            try {
                WebElement link = jobElement.findElement(By.cssSelector(selector));
                String href = link.getAttribute("href");
                if (href != null && href.contains("/jobs/view/")) {
                    // Clean URL by removing query parameters except essential ones
                    return href.split("\\?")[0];
                }
            } catch (Exception e) {
                // Try next selector
            }
        }

        return "";
    }

    private boolean navigateToNextPage() {
        try {
            // Scroll to bottom to find pagination
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
            sleep(3000);

            // Look for next button
            List<WebElement> nextButtons = driver.findElements(By.cssSelector(
                    "button[aria-label*='Next']:not([disabled]), "
                    + ".artdeco-pagination__button--next:not([disabled]), "
                    + "[data-testid='pagination-next-button']:not([disabled])"
            ));

            if (!nextButtons.isEmpty()) {
                WebElement nextButton = nextButtons.get(0);
                if (nextButton.isDisplayed() && nextButton.isEnabled()) {
                    log("Clicking next page button");
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);

                    // Wait for page to load
                    sleep(5000);

                    // Verify we're on a new page
                    return waitForJobResults();
                }
            }

            log("Next button not found or not clickable");
            return false;

        } catch (Exception e) {
            log("Error navigating to next page: " + e.getMessage());
            return false;
        }
    }

    private void debugPageContent() {
        try {
            log("=== DEBUG: Page Content Analysis ===");
            log("Current URL: " + driver.getCurrentUrl());
            log("Page title: " + driver.getTitle());

            // Check for common elements
            String[] debugSelectors = {
                "body",
                ".jobs-search__results-list",
                ".job-search-card",
                ".scaffold-layout__list",
                ".authentication-outlet"
            };

            for (String selector : debugSelectors) {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                log("Elements found for '" + selector + "': " + elements.size());
            }

            // Get page source snippet
            String pageSource = driver.getPageSource();
            if (pageSource.length() > 500) {
                log("Page source snippet: " + pageSource.substring(0, 500) + "...");
            }

            log("=== END DEBUG ===");

        } catch (Exception e) {
            log("Error during debug: " + e.getMessage());
        }
    }

    public void exportToJson(String filename) {
        try {
            if (allJobs.isEmpty()) {
                log("No jobs to export");
                return;
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(allJobs);

            Files.createDirectories(Paths.get("output"));
            try (FileWriter writer = new FileWriter("output/" + filename)) {
                writer.write(json);
            }

            log("Successfully exported " + allJobs.size() + " jobs to output/" + filename);
        } catch (Exception e) {
            log("Error exporting to JSON: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (driver != null) {
                log("Closing browser...");
                driver.quit();
            }
        } catch (Exception e) {
            log("Error closing driver: " + e.getMessage());
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        String keyword = "software engineer";
        String location = "London, UK";
        int maxPages = 2;

        System.out.println("=== LinkedIn Job Crawler ===");
        System.out.println("Search: '" + keyword + "' in '" + location + "'");
        System.out.println("Max pages: " + maxPages);
        System.out.println();

        LinkedInJobCrawler crawler = new LinkedInJobCrawler(true);

        try {
            long startTime = System.currentTimeMillis();
            List<JobListing> jobs = crawler.searchJobs(keyword, location, maxPages);
            long duration = (System.currentTimeMillis() - startTime) / 1000;

            System.out.println("\n=== FINAL RESULTS ===");
            System.out.println("Total jobs found: " + jobs.size());
            System.out.println("Time taken: " + duration + " seconds");

            if (!jobs.isEmpty()) {
                System.out.println("\nFirst 5 jobs:");
                for (int i = 0; i < Math.min(5, jobs.size()); i++) {
                    JobListing job = jobs.get(i);
                    System.out.println((i + 1) + ". " + job.getTitle() + " at "
                            + (job.getCompany().isEmpty() ? "Unknown Company" : job.getCompany()));
                }

                // Export to JSON
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = "linkedin_jobs_" + timestamp + ".json";
                crawler.exportToJson(filename);
            } else {
                System.out.println("\n❌ No jobs found. Check the debug output above for issues.");
            }

        } catch (Exception e) {
            System.err.println("❌ Critical error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            crawler.close();
        }
    }
}
