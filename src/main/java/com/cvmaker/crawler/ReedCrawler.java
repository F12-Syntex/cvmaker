package com.cvmaker.crawler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

public class ReedCrawler {

    private Playwright playwright;
    private BrowserContext context;
    private Page page;
    private Set<String> processedUrls;

    // Common search terms for software jobs
    private static final String[] SOFTWARE_KEYWORDS = {
        "software engineer", "java developer", "python developer",
        "frontend developer", "backend developer", "full stack developer",
        "software developer", "web developer", "mobile developer"
    };

    // Reed.co.uk specific selectors
    private static final String SEARCH_INPUT = "input[name='keywords']";
    private static final String SEARCH_BUTTON = "input[type='submit'][value='Search jobs']";
    private static final String JOB_CARDS = ".job-result";
    private static final String EASY_APPLY_BUTTON = "button:has-text('Easy Apply'), a:has-text('Easy Apply')";
    private static final String NEXT_PAGE_BUTTON = "a[aria-label='Next']";

    public ReedCrawler() {
        this.processedUrls = new HashSet<>();
    }

    public void setupBrowser() {
        System.out.println("🔧 DEBUG: Setting up browser with anti-bot protection for Reed.co.uk...");

        // Create Playwright instance
        playwright = Playwright.create();
        System.out.println("✅ DEBUG: Playwright instance created");

        try {
            // Use persistent context for session/cookie saving - SAME AS ORIGINAL
            java.nio.file.Path userDataDir = java.nio.file.Paths.get("playwright-session");
            this.context = playwright.chromium().launchPersistentContext(
                    userDataDir,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(false)
                            .setSlowMo(1000)
                            .setViewportSize(1920, 1080)
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .setJavaScriptEnabled(true)
                            .setArgs(Arrays.asList(
                                    "--disable-blink-features=AutomationControlled",
                                    "--disable-dev-shm-usage",
                                    "--no-sandbox",
                                    "--disable-web-security",
                                    "--disable-features=VizDisplayCompositor",
                                    "--disable-extensions-file-access-check",
                                    "--disable-extensions-http-throttling",
                                    "--disable-extensions-http-throttling",
                                    "--aggressive-cache-discard",
                                    "--disable-background-timer-throttling",
                                    "--disable-backgrounding-occluded-windows",
                                    "--disable-renderer-backgrounding",
                                    "--disable-features=TranslateUI",
                                    "--disable-ipc-flooding-protection",
                                    "--enable-features=NetworkService,NetworkServiceLogging",
                                    "--force-color-profile=srgb",
                                    "--metrics-recording-only",
                                    "--use-mock-keychain",
                                    "--disable-component-update"
                            ))
                            .setExtraHTTPHeaders(Map.of(
                                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                                    "Accept-Language", "en-US,en;q=0.9",
                                    "Accept-Encoding", "gzip, deflate, br",
                                    "DNT", "1",
                                    "Connection", "keep-alive",
                                    "Upgrade-Insecure-Requests", "1"
                            ))
            );

            System.out.println("✅ DEBUG: Persistent context created successfully (session will be saved in '" + userDataDir + "')");

            // Add script to remove automation indicators - SAME AS ORIGINAL
            context.addInitScript("() => {"
                    + "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
                    + "delete navigator.__proto__.webdriver;"
                    + "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});"
                    + "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});"
                    + "window.chrome = { runtime: {} };"
                    + "Object.defineProperty(navigator, 'permissions', {get: () => ({query: () => Promise.resolve({state: 'granted'})})});"
                    + "}");

            this.page = context.newPage();
            System.out.println("✅ DEBUG: Page created successfully: " + page.url());

            // Set reasonable timeouts - SAME AS ORIGINAL
            page.setDefaultTimeout(60000); // Increased timeout for Cloudflare
            page.setDefaultNavigationTimeout(60000);
            System.out.println("✅ DEBUG: Timeouts set to 60 seconds");

            // Add debug listeners - SAME AS ORIGINAL
            page.onRequest(request -> {
                System.out.println("🌐 DEBUG: Request: " + request.method() + " " + request.url());
            });

            page.onResponse(response -> {
                System.out.println("📥 DEBUG: Response: " + response.status() + " " + response.url());
            });

            page.onLoad(p -> {
                System.out.println("📄 DEBUG: Page loaded: " + p.url());
            });

            page.onDOMContentLoaded(p -> {
                System.out.println("🏗️ DEBUG: DOM content loaded: " + p.url());
            });

            System.out.println("✅ Browser setup complete for Reed.co.uk");

        } catch (Exception e) {
            System.err.println("❌ DEBUG: Failed to setup browser: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean handleCloudflareChallenge() {
        System.out.println("☁️ DEBUG: Checking for Cloudflare challenge...");

        try {
            // Wait a bit for the page to load
            page.waitForTimeout(3000);

            // Check for various Cloudflare indicators
            String[] cloudflareSelectors = {
                ".cf-browser-verification",
                ".cf-challenge-form",
                "#cf-challenge-stage",
                ".challenge-form",
                "[data-ray]",
                ".cf-wrapper",
                "body[class*='cf-']",
                ".cf-error-overview"
            };

            boolean cloudflareDetected = false;
            for (String selector : cloudflareSelectors) {
                try {
                    if (page.locator(selector).isVisible()) {
                        System.out.println("☁️ DEBUG: Cloudflare detected with selector: " + selector);
                        cloudflareDetected = true;
                        break;
                    }
                } catch (Exception e) {
                    // Continue checking other selectors
                }
            }

            // Also check page content for Cloudflare text
            String pageContent = page.content().toLowerCase();
            if (pageContent.contains("cloudflare")
                    || pageContent.contains("checking your browser")
                    || pageContent.contains("please wait")
                    || pageContent.contains("verify you are human")
                    || pageContent.contains("ddos protection")) {
                System.out.println("☁️ DEBUG: Cloudflare detected in page content");
                cloudflareDetected = true;
            }

            if (cloudflareDetected) {
                System.out.println("☁️ DEBUG: Cloudflare challenge detected, handling...");
                return handleCloudflareWithRetries();
            } else {
                System.out.println("✅ DEBUG: No Cloudflare challenge detected");
                return true;
            }

        } catch (Exception e) {
            System.out.println("❌ DEBUG: Error checking for Cloudflare: " + e.getMessage());
            return false;
        }
    }

    private boolean handleCloudflareWithRetries() {
        int maxRetries = 5;
        int retryDelay = 5000; // 5 seconds

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            System.out.println("☁️ DEBUG: Cloudflare bypass attempt " + attempt + "/" + maxRetries);

            try {
                // Look for the verification button
                String[] verifySelectors = {
                    ".cf-challenge-form input[type='submit']",
                    ".cf-challenge-form button",
                    "input[value*='Verify']",
                    "button:has-text('Verify')",
                    ".challenge-form input[type='submit']",
                    ".challenge-form button",
                    "#cf-challenge-stage input",
                    "#cf-challenge-stage button"
                };

                boolean buttonClicked = false;
                for (String selector : verifySelectors) {
                    try {
                        Locator verifyButton = page.locator(selector).first();
                        if (verifyButton.isVisible()) {
                            System.out.println("☁️ DEBUG: Found verify button with selector: " + selector);

                            // Add random human-like delay before clicking
                            int randomDelay = 1000 + (int) (Math.random() * 3000);
                            System.out.println("⏳ DEBUG: Waiting " + randomDelay + "ms before clicking...");
                            page.waitForTimeout(randomDelay);

                            // Click the button
                            verifyButton.click();
                            System.out.println("✅ DEBUG: Clicked verify button");
                            buttonClicked = true;
                            break;
                        }
                    } catch (Exception e) {
                        System.out.println("❌ DEBUG: Failed to click selector " + selector + ": " + e.getMessage());
                        continue;
                    }
                }

                if (buttonClicked) {
                    // Wait for verification to complete
                    System.out.println("⏳ DEBUG: Waiting for verification to complete...");
                    page.waitForTimeout(retryDelay);

                    // Check if we're past the challenge
                    String pageContent = page.content().toLowerCase();

                    if (!pageContent.contains("cloudflare")
                            && !pageContent.contains("checking your browser")
                            && !pageContent.contains("verify you are human")) {
                        System.out.println("✅ DEBUG: Successfully bypassed Cloudflare challenge!");
                        return true;
                    } else {
                        System.out.println("⚠️ DEBUG: Still on Cloudflare page, retrying...");
                    }
                } else {
                    System.out.println("❌ DEBUG: Could not find verify button, trying alternative approach...");

                    // Try clicking anywhere on the page (sometimes needed for invisible challenges)
                    try {
                        page.click("body");
                        page.waitForTimeout(retryDelay);
                    } catch (Exception e) {
                        System.out.println("❌ DEBUG: Alternative click failed: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                System.out.println("❌ DEBUG: Error in Cloudflare bypass attempt " + attempt + ": " + e.getMessage());
            }

            // Wait before next retry
            if (attempt < maxRetries) {
                System.out.println("⏳ DEBUG: Waiting " + retryDelay + "ms before next attempt...");
                page.waitForTimeout(retryDelay);
                retryDelay += 2000; // Increase delay for next attempt
            }
        }

        System.out.println("❌ DEBUG: Failed to bypass Cloudflare after " + maxRetries + " attempts");
        System.out.println("💡 DEBUG: You may need to manually complete the challenge");

        // Give user option to manually complete
        System.out.println("\n" + "=".repeat(80));
        System.out.println("☁️ CLOUDFLARE CHALLENGE DETECTED");
        System.out.println("Please manually complete the Cloudflare challenge in the browser.");
        System.out.println("After completing the challenge, press ENTER to continue...");
        System.out.println("=".repeat(80));

        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();

        return true; // Continue anyway
    }

    public void openForLogin() {
        System.out.println("🔐 DEBUG: Opening browser for manual login to Reed.co.uk...");

        if (page == null) {
            System.err.println("❌ DEBUG: Page is null! Cannot proceed.");
            return;
        }

        try {
            System.out.println("🌐 DEBUG: Navigating to: https://www.reed.co.uk/");

            // Navigate to Reed.co.uk
            Response response = page.navigate("https://www.reed.co.uk/", new Page.NavigateOptions()
                    .setTimeout(60000)
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );

            System.out.println("📥 DEBUG: Navigation response status: " + (response != null ? response.status() : "null"));

            // Wait for page to be ready
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            System.out.println("✅ DEBUG: DOM content loaded");

            // Handle Cloudflare challenge if present
            if (!handleCloudflareChallenge()) {
                System.out.println("❌ DEBUG: Failed to handle Cloudflare challenge");
                return;
            }

            System.out.println("📍 DEBUG: Current page URL: " + page.url());
            System.out.println("📄 DEBUG: Page title: " + page.title());

            // Give user time to see the page
            System.out.println("⏳ DEBUG: Waiting 3 seconds for page to settle...");
            page.waitForTimeout(3000);

            System.out.println("\n" + "=".repeat(80));
            System.out.println("🔐 MANUAL LOGIN REQUIRED");
            System.out.println("Please log in to Reed.co.uk if needed.");
            System.out.println("After logging in, press ENTER to continue with job search...");
            System.out.println("=".repeat(80));

            // Wait for user to press Enter after logging in
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            System.out.println("✅ DEBUG: User confirmed login completion");

        } catch (Exception e) {
            System.err.println("❌ DEBUG: Exception in openForLogin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void crawlAllJobs() {
        System.out.println("🚀 Starting comprehensive job crawl on Reed.co.uk...");

        for (String keyword : SOFTWARE_KEYWORDS) {
            System.out.println("\n🔍 Searching for: " + keyword);

            try {
                searchJobs(keyword);
                crawlSearchResults();

                // Wait between searches to be respectful
                page.waitForTimeout(2000);

            } catch (Exception e) {
                System.err.println("❌ Error searching for '" + keyword + "': " + e.getMessage());
                continue;
            }
        }

        System.out.println("\n✅ Completed crawling all keywords");
        System.out.println("📊 Total URLs processed: " + processedUrls.size());
    }

    private void searchJobs(String keyword) {
        System.out.println("🔍 DEBUG: Performing search for: " + keyword);

        try {
            // Go to Reed.co.uk homepage first
            page.navigate("https://www.reed.co.uk/");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(2000);

            // Find and fill search input
            Locator searchInput = page.locator(SEARCH_INPUT).first();
            if (searchInput.isVisible()) {
                System.out.println("✅ DEBUG: Found search input");
                searchInput.click();
                searchInput.fill("");
                searchInput.type(keyword, new Locator.TypeOptions().setDelay(100));
                page.waitForTimeout(1000);

                // Click search button
                Locator searchButton = page.locator(SEARCH_BUTTON).first();
                if (searchButton.isVisible()) {
                    System.out.println("✅ DEBUG: Found search button");
                    searchButton.click();
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    page.waitForTimeout(3000);
                    System.out.println("✅ DEBUG: Search performed successfully");
                } else {
                    // Fallback to Enter key
                    System.out.println("⌨️ DEBUG: Pressing Enter as fallback");
                    searchInput.press("Enter");
                    page.waitForTimeout(3000);
                    System.out.println("✅ DEBUG: Search performed with Enter key");
                }
            } else {
                System.out.println("❌ DEBUG: Could not find search input");
            }

        } catch (Exception e) {
            System.err.println("❌ DEBUG: Error performing search: " + e.getMessage());
        }
    }

    private List<JobInfo> extractJobsFromPage() {
        List<JobInfo> jobs = new ArrayList<>();

        try {
            // Target the specific job card structure
            String[] jobCardSelectors = {
                ".job-card_jobCard__MkcJD", // Your specific class
                "[class*='job-card_jobCard']", // Partial match
                ".card.job-card", // Generic fallback
                ".job-card"
            };

            Locator jobCards = null;
            String workingSelector = "";

            // Find which selector works
            for (String selector : jobCardSelectors) {
                try {
                    Locator testCards = page.locator(selector);
                    int count = testCards.count();
                    System.out.println("📊 DEBUG: Selector '" + selector + "' found " + count + " cards");

                    if (count > 0) {
                        jobCards = testCards;
                        workingSelector = selector;
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (jobCards == null) {
                System.out.println("❌ DEBUG: No job cards found with any selector");
                return jobs;
            }

            int count = jobCards.count();
            System.out.println("✅ DEBUG: Using selector '" + workingSelector + "' - found " + count + " job cards");

            // Limit to first 3 jobs for testing
            for (int i = 0; i < Math.min(count, 3); i++) {
                try {
                    Locator card = jobCards.nth(i);

                    JobInfo job = new JobInfo();
                    job.cardLocator = card;

                    // Try to extract title from within the card
                    try {
                        Locator titleElement = card.locator("h3, .job-title, [class*='jobTitle'], [class*='title']").first();
                        if (titleElement.isVisible()) {
                            job.title = titleElement.textContent().trim();
                        }
                    } catch (Exception e) {
                        job.title = "Job " + (i + 1); // Fallback title
                    }

                    // Try to extract company
                    try {
                        Locator companyElement = card.locator(".company, [class*='company'], [data-qa*='company']").first();
                        if (companyElement.isVisible()) {
                            job.company = companyElement.textContent().trim();
                        }
                    } catch (Exception e) {
                        job.company = "Company not found";
                    }

                    job.url = "card-" + i; // Placeholder since we're clicking cards not links

                    if (job.title != null && !job.title.isEmpty()) {
                        jobs.add(job);
                        System.out.println("✅ DEBUG: Extracted job card " + i + ": " + job.title);
                    }

                } catch (Exception e) {
                    System.out.println("❌ DEBUG: Error extracting job card " + i + ": " + e.getMessage());
                    continue;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ DEBUG: Error extracting job cards: " + e.getMessage());
        }

        return jobs;
    }

    private void clickOnJob(JobInfo job) {
        System.out.println("\n🖱️ PHASE 1: Clicking on job: " + job.title);
        System.out.println("🏢 Company: " + job.company);
        System.out.println("📍 Location: " + job.location);
        System.out.println("🔗 URL: " + job.url);
        System.out.println("⚡ Easy Apply: " + (job.hasEasyApply ? "YES" : "NO"));

        try {
            // Store current URL to compare later
            String originalUrl = page.url();
            System.out.println("📍 DEBUG: Current URL before click: " + originalUrl);

            // Add human-like delay before clicking
            int randomDelay = 1000 + (int) (Math.random() * 2000);
            System.out.println("⏳ DEBUG: Waiting " + randomDelay + "ms before clicking job...");
            page.waitForTimeout(randomDelay);

            // Scroll to the job card to make sure it's visible
            if (job.cardLocator != null) {
                job.cardLocator.scrollIntoViewIfNeeded();
                page.waitForTimeout(500);
            }

            // Click on the job title link
            if (job.titleLocator != null && job.titleLocator.isVisible()) {
                System.out.println("🖱️ DEBUG: Clicking on job title link...");
                job.titleLocator.click();

                // Wait for navigation or page change
                page.waitForTimeout(3000);

                String newUrl = page.url();
                System.out.println("📍 DEBUG: URL after click: " + newUrl);

                if (!newUrl.equals(originalUrl)) {
                    System.out.println("✅ SUCCESS: Successfully clicked on job - page changed!");
                    System.out.println("📄 DEBUG: New page title: " + page.title());

                    // PHASE 1: Do nothing after clicking - just log success
                    System.out.println("🎯 PHASE 1 COMPLETE: Job clicked, doing nothing as requested");

                    // Wait a bit to see the job page
                    page.waitForTimeout(2000);

                    // Go back to search results for next job
                    System.out.println("🔙 DEBUG: Going back to search results...");
                    page.goBack();
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    page.waitForTimeout(2000);

                } else {
                    System.out.println("⚠️ DEBUG: URL didn't change, job might have opened in same page");
                }

            } else {
                System.out.println("❌ DEBUG: Job title link not found or not visible");
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to click on job: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("─".repeat(60));
    }

    private void scrollPage() {
        try {
            System.out.println("📜 DEBUG: Scrolling to load dynamic content...");
            // Scroll down to load dynamic content
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(1000);
            page.evaluate("window.scrollTo(0, 0)");
            page.waitForTimeout(500);
        } catch (Exception e) {
            System.out.println("❌ DEBUG: Scrolling failed: " + e.getMessage());
        }
    }

    private boolean goToNextPage() {
        try {
            System.out.println("🔄 DEBUG: Looking for next page button...");
            Locator nextButton = page.locator(NEXT_PAGE_BUTTON).first();
            if (nextButton.isVisible() && nextButton.isEnabled()) {
                System.out.println("✅ DEBUG: Found next page button");

                // Add human-like delay before clicking next page
                int randomDelay = 2000 + (int) (Math.random() * 3000);
                page.waitForTimeout(randomDelay);

                nextButton.click();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(3000);
                return true;
            } else {
                System.out.println("❌ DEBUG: Next page button not found or disabled");
            }
        } catch (Exception e) {
            System.out.println("❌ DEBUG: Error going to next page: " + e.getMessage());
        }
        return false;
    }

    private boolean isValidJob(JobInfo job) {
        if (job.title == null || job.title.isEmpty() || job.url == null || job.url.isEmpty()) {
            return false;
        }

        String lowerTitle = job.title.toLowerCase();
        return lowerTitle.contains("software")
                || lowerTitle.contains("developer")
                || lowerTitle.contains("engineer")
                || lowerTitle.contains("programmer");
    }

    private String makeAbsoluteUrl(String url) {
        if (url == null || url.startsWith("http")) {
            return url;
        }
        return "https://www.reed.co.uk" + (url.startsWith("/") ? url : "/" + url);
    }

    public void close() {
        System.out.println("🔒 DEBUG: Closing browser resources...");
        if (page != null) {
            page.close();
            System.out.println("✅ DEBUG: Page closed");
        }
        if (context != null) {
            context.close();
            System.out.println("✅ DEBUG: Context closed");
        }
        if (playwright != null) {
            playwright.close();
            System.out.println("✅ DEBUG: Playwright closed");
        }
    }

    private static class JobInfo {

        String title = "";
        String company = "";
        String location = "";
        String url = "";
        boolean hasEasyApply = false;
        Locator cardLocator = null;    // Store the job card locator
        Locator titleLocator = null;   // Store the title link locator
    }

    public static void main(String[] args) {
        ReedCrawler crawler = new ReedCrawler();

        try {
            crawler.setupBrowser();
            crawler.openForLogin();
            crawler.crawlAllJobs();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            crawler.close();
        }
    }

    private void crawlSearchResults() {
        System.out.println("📋 DEBUG: Crawling search results...");

        // Scroll to load dynamic content
        scrollPage();

        // Find all job cards on current page
        List<JobInfo> jobs = extractJobsFromPage();
        System.out.println("📊 DEBUG: Found " + jobs.size() + " jobs on this page");

        // Click on ONLY the first job and STOP
        if (!jobs.isEmpty()) {
            JobInfo firstJob = jobs.get(0);
            System.out.println("\n🎯 DEBUG: Clicking on FIRST job only, then stopping");
            clickOnJobActually(firstJob, 0);
        } else {
            System.out.println("❌ No jobs found to click on");
        }
    }

    private void clickOnJobActually(JobInfo job, int jobIndex) {
        System.out.println("\n🖱️ PHASE 1: Actually clicking on job card: " + job.title);
        System.out.println("🏢 Company: " + job.company);
        System.out.println("📍 Location: " + job.location);

        try {
            // Store current URL to compare later
            String originalUrl = page.url();
            System.out.println("📍 DEBUG: Current URL before click: " + originalUrl);

            // Target the specific job card structure you mentioned
            System.out.println("🔍 DEBUG: Finding job card to click...");

            // Reed.co.uk specific job card selectors based on your provided classes
            String[] jobCardSelectors = {
                ".job-card_jobCard__MkcJD", // The main job card
                "[class*='job-card_jobCard']", // Partial match in case class changes
                ".job-card_jobTitleBtn__block__ZeEY5", // The title button
                "[class*='job-card_jobTitleBtn']", // Partial match for title button
                ".card.job-card", // Generic fallback
                ".job-card", // Another fallback
                ".btn.btn-link[class*='jobTitleBtn']" // Button specific
            };

            for (String selector : jobCardSelectors) {
                try {
                    System.out.println("🔍 DEBUG: Trying job card selector: " + selector);
                    Locator jobCards = page.locator(selector);
                    int cardCount = jobCards.count();
                    System.out.println("📊 DEBUG: Found " + cardCount + " job cards with selector: " + selector);

                    if (cardCount > jobIndex) {
                        Locator targetCard = jobCards.nth(jobIndex);

                        if (targetCard.isVisible()) {
                            // Get some info about the card we're about to click
                            String cardText = targetCard.textContent();
                            System.out.println("🎯 DEBUG: Found job card:");
                            System.out.println("   Card text preview: " + (cardText.length() > 100 ? cardText.substring(0, 100) + "..." : cardText));

                            // Scroll to the card
                            System.out.println("📜 DEBUG: Scrolling to job card...");
                            targetCard.scrollIntoViewIfNeeded();
                            page.waitForTimeout(1000);

                            // CLICK THE JOB CARD AND STOP!
                            System.out.println("🖱️ CLICKING JOB CARD NOW!");
                            targetCard.click();

                            System.out.println("✅ CLICKED JOB CARD! STOPPING HERE TO WAIT FOR SITE REACTION...");
                            System.out.println("🛑 PHASE 1: Click complete - waiting for your observation");
                            System.out.println("📍 Current URL: " + page.url());

                            // STOP HERE - Wait for user input to see what happens
                            Scanner scanner = new Scanner(System.in);
                            System.out.println("\n" + "=".repeat(60));
                            System.out.println("⏸️  PAUSED AFTER CLICKING JOB CARD");
                            System.out.println("Observe what happens on the website...");
                            System.out.println("Press ENTER when ready to continue...");
                            System.out.println("=".repeat(60));
                            scanner.nextLine();

                            return; // Exit method after clicking
                        } else {
                            System.out.println("❌ DEBUG: Job card not visible at index " + jobIndex);
                        }
                    } else {
                        System.out.println("❌ DEBUG: Not enough job cards found for index " + jobIndex);
                    }

                } catch (Exception e) {
                    System.out.println("❌ DEBUG: Error with selector " + selector + ": " + e.getMessage());
                    continue;
                }
            }

            System.out.println("❌ ERROR: Could not find any job card to click");

        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to click on job card: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkForEasyApplyOnJobPage() {
        System.out.println("🔍 DEBUG: Checking for Easy Apply button on job page...");

        try {
            // Multiple selectors for Easy Apply button
            String[] easyApplySelectors = {
                "button:has-text('Easy Apply')",
                "a:has-text('Easy Apply')",
                ".easy-apply",
                "[data-qa='easy-apply']",
                "button[class*='easy-apply']",
                "a[class*='easy-apply']"
            };

            boolean easyApplyFound = false;

            for (String selector : easyApplySelectors) {
                try {
                    Locator easyApplyBtn = page.locator(selector).first();
                    if (easyApplyBtn.isVisible()) {
                        System.out.println("✅ FOUND: Easy Apply button on job page!");
                        System.out.println("🎯 PHASE 1: Found Easy Apply - not clicking yet");
                        easyApplyFound = true;
                        break;
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            if (!easyApplyFound) {
                System.out.println("❌ No Easy Apply button found on this job page");
            }

        } catch (Exception e) {
            System.out.println("❌ Error checking for Easy Apply: " + e.getMessage());
        }
    }
}
