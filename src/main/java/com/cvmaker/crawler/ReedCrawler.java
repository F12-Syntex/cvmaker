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

    private void crawlSearchResults() {
        System.out.println("📋 DEBUG: Crawling search results...");
        
        int pageCount = 1;
        int maxPages = 5; // Limit pages per search
        
        do {
            System.out.println("📄 DEBUG: Processing page " + pageCount);
            
            // Scroll to load dynamic content
            scrollPage();
            
            // Find all job cards on current page
            List<JobInfo> jobs = extractJobsFromPage();
            System.out.println("📊 DEBUG: Found " + jobs.size() + " jobs on this page");
            
            // Process each job
            for (JobInfo job : jobs) {
                if (!processedUrls.contains(job.url)) {
                    processedUrls.add(job.url);
                    processJob(job);
                }
            }
            
            pageCount++;
            
        } while (pageCount <= maxPages && goToNextPage());
    }

    private List<JobInfo> extractJobsFromPage() {
        List<JobInfo> jobs = new ArrayList<>();
        
        try {
            Locator jobCards = page.locator(JOB_CARDS);
            int count = jobCards.count();
            System.out.println("📊 DEBUG: Found " + count + " job cards");
            
            for (int i = 0; i < count; i++) {
                try {
                    Locator card = jobCards.nth(i);
                    
                    // Extract job information
                    JobInfo job = new JobInfo();
                    
                    // Get job title
                    Locator titleLink = card.locator("h3 a, .job-title a").first();
                    if (titleLink.isVisible()) {
                        job.title = titleLink.textContent().trim();
                        job.url = makeAbsoluteUrl(titleLink.getAttribute("href"));
                    }
                    
                    // Get company
                    Locator companyElement = card.locator(".gtmJobListingPostedBy, .company").first();
                    if (companyElement.isVisible()) {
                        job.company = companyElement.textContent().trim();
                    }
                    
                    // Get location
                    Locator locationElement = card.locator(".location, .gtmJobListingLocation").first();
                    if (locationElement.isVisible()) {
                        job.location = locationElement.textContent().trim();
                    }
                    
                    // Check for Easy Apply button
                    Locator easyApplyBtn = card.locator(EASY_APPLY_BUTTON).first();
                    job.hasEasyApply = easyApplyBtn.isVisible();
                    
                    if (isValidJob(job)) {
                        jobs.add(job);
                        System.out.println("✅ DEBUG: Extracted job: " + job.title + " (Easy Apply: " + job.hasEasyApply + ")");
                    }
                    
                } catch (Exception e) {
                    System.out.println("❌ DEBUG: Error extracting job " + i + ": " + e.getMessage());
                    continue;
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ DEBUG: Error extracting jobs: " + e.getMessage());
        }
        
        return jobs;
    }

    private void processJob(JobInfo job) {
        System.out.println("\n📝 Processing: " + job.title);
        System.out.println("🏢 Company: " + job.company);
        System.out.println("📍 Location: " + job.location);
        System.out.println("🔗 URL: " + job.url);
        System.out.println("⚡ Easy Apply: " + (job.hasEasyApply ? "YES" : "NO"));
        
        if (job.hasEasyApply) {
            System.out.println("🎯 Found Easy Apply job - Phase 1 complete");
            // Phase 1: Just identify Easy Apply jobs
            // Future phases will interact with the Easy Apply functionality
        }
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
        return lowerTitle.contains("software") ||
               lowerTitle.contains("developer") ||
               lowerTitle.contains("engineer") ||
               lowerTitle.contains("programmer");
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
}