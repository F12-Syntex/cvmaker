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
            this.title = title != null ? title : "";
            this.company = company != null ? company : "";
            this.location = location != null ? location : "";
            this.url = url != null ? url : "";
            this.jobId = url.contains("/jobs/view/") ? url.split("/jobs/view/")[1].replaceAll("[^0-9].*$", "") : "";
            this.datePosted = "";
            this.searchDate = LocalDateTime.now().toString();
        }

        @Override
        public String toString() {
            return "Job: " + title + "\nCompany: " + company + "\nLocation: " + location + "\nURL: " + url;
        }
    }

    private WebDriver driver;
    private boolean debug;
    private Set<String> processedUrls = new HashSet<>();
    private List<JobListing> allJobs = new ArrayList<>();
    private static final int WAIT_SECONDS = 15;
    private AiService aiService;
    private Random random = new Random();

    public LinkedInJobCrawler(boolean debug) {
        this.debug = debug;
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // Use your default Chrome profile
        String userDataDir = getUserDataDirectory();
        options.addArguments("--user-data-dir=" + userDataDir);

        // Optional: specify a specific profile if you have multiple
        // options.addArguments("--profile-directory=Default");
        // Keep some anti-detection measures but remove conflicting ones
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--window-size=1920,1080");

        // Remove automation indicators
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Don't run headless when using profile (for better compatibility)
        log("Using Chrome profile from: " + userDataDir);

        driver = new ChromeDriver(options);

        // Remove webdriver property
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        // Initialize AiService with minimal tokens
        aiService = new AiService(LLMModel.GPT_4_1_MINI, 0.2);
    }

    /**
     * Get the default Chrome user data directory based on the operating system
     */
    private String getUserDataDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            // Windows
            return userHome + "\\AppData\\Local\\Google\\Chrome\\User Data";
        } else if (os.contains("mac")) {
            // macOS
            return userHome + "/Library/Application Support/Google/Chrome";
        } else {
            // Linux
            return userHome + "/.config/google-chrome";
        }
    }

    /**
     * Alternative constructor to use a custom profile path
     */
    public LinkedInJobCrawler(boolean debug, String customProfilePath) {
        this.debug = debug;
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // Use custom profile path
        options.addArguments("--user-data-dir=" + customProfilePath);

        // Anti-detection measures
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--window-size=1920,1080");

        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        log("Using custom Chrome profile from: " + customProfilePath);

        driver = new ChromeDriver(options);
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        aiService = new AiService(LLMModel.GPT_4_1_NANO, 0.2);
    }

    private void log(String message) {
        if (debug) {
            System.out.println("[DEBUG] " + message);
        }
    }

    private void randomWait() {
        try {
            // Random wait between 2-5 seconds to appear more human-like
            Thread.sleep(2000 + random.nextInt(3000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<JobListing> searchJobs(String keyword, String location, int maxPages) {
        allJobs.clear();
        processedUrls.clear();

        try {
            // First, check if we're already logged in to LinkedIn
            log("Checking LinkedIn authentication status...");
            driver.get("https://www.linkedin.com/");
            randomWait();

            if (isLoggedIn()) {
                log("Successfully authenticated with LinkedIn!");
            } else {
                log("Not authenticated. You may need to log in manually.");
                log("Please log in to LinkedIn in the browser window that opened.");
                log("Press Enter in the console when you're logged in...");

                // Wait for manual login
                try {
                    System.in.read();
                } catch (Exception e) {
                    Thread.sleep(30000); // Wait 30 seconds if input reading fails
                }
            }

            // Go to LinkedIn jobs page
            String jobsUrl = buildJobsUrl(keyword, location);
            log("Jobs URL: " + jobsUrl);

            driver.get(jobsUrl);
            randomWait();

            // Check if we can access job results
            if (!waitForJobResults()) {
                log("Could not find job results. Page might have changed structure or access might be restricted.");
                return allJobs;
            }

            // Process first page
            scrollAndExtractJobs();

            // Navigate through additional pages
            for (int page = 2; page <= maxPages; page++) {
                log("Navigating to page " + page);
                if (navigateToNextPage()) {
                    randomWait();
                    scrollAndExtractJobs();
                } else {
                    log("Could not navigate to page " + page + ". Stopping.");
                    break;
                }
            }

            log("Found " + allJobs.size() + " total jobs");
            return allJobs;

        } catch (Exception e) {
            log("Error searching jobs: " + e.getMessage());
            e.printStackTrace();
            return allJobs;
        }
    }

    /**
     * Check if user is logged in to LinkedIn
     */
    private boolean isLoggedIn() {
        try {
            // Look for elements that indicate a logged-in state
            List<WebElement> profileElements = driver.findElements(By.cssSelector(
                    ".global-nav__me, .nav-item__profile-member-photo, [data-testid='nav-me-tab']"
            ));

            // Also check if we're not on a login/guest page
            String currentUrl = driver.getCurrentUrl();
            String pageSource = driver.getPageSource().toLowerCase();

            boolean hasProfileElement = !profileElements.isEmpty();
            boolean notOnLoginPage = !currentUrl.contains("/login")
                    && !currentUrl.contains("/guest")
                    && !pageSource.contains("sign in to linkedin");

            return hasProfileElement && notOnLoginPage;

        } catch (Exception e) {
            log("Error checking login status: " + e.getMessage());
            return false;
        }
    }

    private String buildJobsUrl(String keyword, String location) {
        StringBuilder url = new StringBuilder("https://www.linkedin.com/jobs/search/?");
        url.append("keywords=").append(keyword.replace(" ", "%20"));

        if (location != null && !location.isEmpty()) {
            url.append("&location=").append(location.replace(" ", "%20"));
        }

        // Add some default parameters for better results
        url.append("&f_TPR=r86400"); // Jobs posted in last 24 hours
        url.append("&f_JT=F"); // Full-time jobs
        url.append("&sortBy=DD"); // Sort by date

        return url.toString();
    }

    private boolean waitForJobResults() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SECONDS));

        // Try multiple possible selectors for job results
        String[] selectors = {
            ".jobs-search__results-list",
            ".jobs-search-results-list",
            "[data-testid='jobs-search-results-list']",
            ".job-search-result",
            ".jobs-search-results",
            ".scaffold-layout__list-container"
        };

        for (String selector : selectors) {
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                log("Found job results with selector: " + selector);
                return true;
            } catch (Exception e) {
                // Try next selector
            }
        }

        return false;
    }

    private boolean navigateToNextPage() {
        try {
            // Scroll to bottom to ensure pagination is visible
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight)");
            randomWait();

            // Updated selectors for LinkedIn pagination
            String[] nextButtonSelectors = {
                "button[aria-label*='Next']",
                ".artdeco-pagination__button--next:not([disabled])",
                "[data-testid='pagination-next-button']",
                "button:contains('Next')",
                ".pv2.ph3.artdeco-button--secondary"
            };

            for (String selector : nextButtonSelectors) {
                try {
                    List<WebElement> buttons = driver.findElements(By.cssSelector(selector));
                    if (!buttons.isEmpty() && buttons.get(0).isEnabled()) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", buttons.get(0));
                        Thread.sleep(3000); // Wait for page to load
                        return waitForJobResults();
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }

            return false;

        } catch (Exception e) {
            log("Error navigating to next page: " + e.getMessage());
            return false;
        }
    }

    private void scrollAndExtractJobs() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Scroll gradually to load all content
            for (int i = 0; i < 5; i++) {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight * " + (i + 1) / 5.0 + ")");
                randomWait();

                // Try to click "Show more" or "Load more" buttons
                tryClickShowMore();
            }

            // Find job cards with updated selectors
            List<WebElement> jobElements = findJobElements();
            log("Found " + jobElements.size() + " job elements on page");

            // Extract job data
            for (WebElement jobElement : jobElements) {
                try {
                    extractJobFromElement(jobElement);
                } catch (Exception e) {
                    log("Error extracting job: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log("Error scrolling and extracting jobs: " + e.getMessage());
        }
    }

    private void tryClickShowMore() {
        String[] showMoreSelectors = {
            ".infinite-scroller__show-more-button",
            ".see-more-jobs",
            "button[aria-label*='more']",
            "[data-testid='show-more-button']"
        };

        for (String selector : showMoreSelectors) {
            try {
                List<WebElement> buttons = driver.findElements(By.cssSelector(selector));
                if (!buttons.isEmpty() && buttons.get(0).isDisplayed()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", buttons.get(0));
                    randomWait();
                    return;
                }
            } catch (Exception e) {
                // Try next selector
            }
        }
    }

    private List<WebElement> findJobElements() {
        String[] jobCardSelectors = {
            ".jobs-search__results-list li",
            ".job-search-card",
            "[data-testid='job-search-card']",
            ".scaffold-layout__list-item",
            ".jobs-search-results-list__list-item",
            ".job-result-card"
        };

        for (String selector : jobCardSelectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                if (!elements.isEmpty()) {
                    log("Using job card selector: " + selector);
                    return elements;
                }
            } catch (Exception e) {
                // Try next selector
            }
        }

        return new ArrayList<>();
    }

    private void extractJobFromElement(WebElement jobElement) {
        try {
            // Get job URL first (most important)
            String url = findJobUrl(jobElement);
            if (url.isEmpty() || processedUrls.contains(url)) {
                return;
            }

            // Extract job details with updated selectors
            String title = getTextSafely(jobElement,
                    ".base-search-card__title, .job-card-list__title, h3, .job-search-card__title, [data-testid='job-title']");

            String company = getTextSafely(jobElement,
                    ".base-search-card__subtitle, .job-card-container__company-name, .job-search-card__subtitle, [data-testid='company-name']");

            String location = getTextSafely(jobElement,
                    ".job-search-card__location, .job-card-container__metadata-item, .base-search-card__metadata, [data-testid='job-location']");

            if (!title.isEmpty() && !company.isEmpty()) {
                JobListing job = new JobListing(title, company, location, url);
                allJobs.add(job);
                processedUrls.add(url);
                log("Extracted job: " + title + " at " + company);
            }

        } catch (Exception e) {
            log("Error extracting job from element: " + e.getMessage());
        }
    }

    private String findJobUrl(WebElement jobElement) {
        String[] linkSelectors = {
            "a[href*='/jobs/view/']",
            "a[data-testid='job-title-link']",
            ".job-card__title-link",
            ".base-search-card__title a"
        };

        for (String selector : linkSelectors) {
            try {
                WebElement link = jobElement.findElement(By.cssSelector(selector));
                String href = link.getAttribute("href");
                if (href != null && href.contains("/jobs/view/")) {
                    return href;
                }
            } catch (Exception e) {
                // Try next selector
            }
        }

        return "";
    }

    private String getTextSafely(WebElement element, String selectors) {
        try {
            String[] selectorArray = selectors.split(", ");
            for (String selector : selectorArray) {
                try {
                    WebElement child = element.findElement(By.cssSelector(selector));
                    String text = child.getText().trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }
        } catch (Exception e) {
            // Return empty if all fail
        }
        return "";
    }

    public void exportToJson(String filename) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(allJobs);

            Files.createDirectories(Paths.get("output"));
            try (FileWriter writer = new FileWriter("output/" + filename)) {
                writer.write(json);
            }

            System.out.println("Jobs exported to output/" + filename);
        } catch (Exception e) {
            System.err.println("Error exporting to JSON: " + e.getMessage());
        }
    }

    public void close() {
        if (driver != null) {
            driver.quit();
        }
        if (aiService != null) {
            aiService.shutdown();
        }
    }

    public static void main(String[] args) {
        String keyword = "junior software";
        String location = "London, UK";
        int maxPages = 3;

        System.out.println("LinkedIn Job Crawler with Default Chrome Profile");
        System.out.println("Searching for: " + keyword + " in " + location);
        System.out.println("Using your authenticated Chrome profile for better access.");

        // Use default profile
        LinkedInJobCrawler crawler = new LinkedInJobCrawler(true);

        // Alternative: Use custom profile path
        // String customProfilePath = "/path/to/your/custom/chrome/profile";
        // LinkedInJobCrawler crawler = new LinkedInJobCrawler(true, customProfilePath);
        try {
            long startTime = System.currentTimeMillis();
            List<JobListing> jobs = crawler.searchJobs(keyword, location, maxPages);
            long duration = (System.currentTimeMillis() - startTime) / 1000;

            System.out.println("\nFound " + jobs.size() + " jobs in " + duration + " seconds");

            if (!jobs.isEmpty()) {
                int sampleSize = Math.min(5, jobs.size());
                System.out.println("\nSample results:");
                for (int i = 0; i < sampleSize; i++) {
                    JobListing job = jobs.get(i);
                    System.out.println((i + 1) + ". "
                            + (!job.title.isEmpty() ? job.title : "Untitled")
                            + (!job.company.isEmpty() ? " at " + job.company : ""));
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = "linkedin_jobs_" + keyword.replaceAll("\\s+", "_") + "_" + timestamp + ".json";
                crawler.exportToJson(filename);
            } else {
                System.out.println("\nNo jobs found. Check your search parameters or LinkedIn access.");
            }

        } finally {
            crawler.close();
        }
    }
}
