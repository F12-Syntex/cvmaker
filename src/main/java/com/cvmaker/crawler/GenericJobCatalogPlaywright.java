package com.cvmaker.crawler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

public class GenericJobCatalogPlaywright {

    private Browser browser;
    private BrowserContext context;
    private Page page;
    private Set<String> processedUrls;

    // Generic search patterns
    private static final String[] SEARCH_SELECTORS = {
        "input[placeholder*='search' i]", "input[name*='search' i]", "input[id*='search' i]",
        "input[placeholder*='job' i]", "input[name*='job' i]", "input[id*='job' i]",
        "input[type='search']", ".search-input", "#search", ".search-box",
        "[data-testid*='search']", "[aria-label*='search' i]"
    };

    private static final String[] JOB_LINK_SELECTORS = {
        "a[href*='job']", "a[href*='career']", "a[href*='position']",
        "a[href*='opening']", "a[href*='opportunity']", "a[href*='posting']",
        ".job-title a", ".job-link", ".position-link", ".career-link",
        ".job-item a", ".position-item a", ".listing a",
        "[data-testid*='job'] a", "[data-cy*='job'] a", ".job a"
    };

    public GenericJobCatalogPlaywright() {
        this.processedUrls = new HashSet<>();
    }

    public void setupBrowser() {
        System.out.println("🔧 DEBUG: Setting up browser with anti-bot protection...");

        // Create Playwright instance
        Playwright playwright = Playwright.create();
        System.out.println("✅ DEBUG: Playwright instance created");

        try {
            // Use persistent context for session/cookie saving
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

            // Add script to remove automation indicators
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

            // Set reasonable timeouts
            page.setDefaultTimeout(60000); // Increased timeout for Cloudflare
            page.setDefaultNavigationTimeout(60000);
            System.out.println("✅ DEBUG: Timeouts set to 60 seconds");

            // Add debug listeners
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
                    String currentUrl = page.url();
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

    public void openForLogin(String websiteUrl) {
        System.out.println("🔐 DEBUG: Opening browser for manual login to: " + websiteUrl);

        if (page == null) {
            System.err.println("❌ DEBUG: Page is null! Cannot proceed.");
            return;
        }

        try {
            System.out.println("🌐 DEBUG: Navigating to: " + websiteUrl);

            // Navigate to the website
            Response response = page.navigate(websiteUrl, new Page.NavigateOptions()
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
            System.out.println("Please log in to the website in the opened browser window.");
            System.out.println("After logging in, press ENTER to continue with job search...");
            System.out.println("=".repeat(80));

            // Wait for user to press Enter after logging in
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            System.out.println("✅ DEBUG: User confirmed login completion");

            // Check login status after user confirmation
            checkLoginStatus(websiteUrl);

        } catch (Exception e) {
            System.err.println("❌ DEBUG: Exception in openForLogin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void catalogJobs(String websiteUrl, String searchTerm, int maxPages) {
        System.out.println("\n🔍 DEBUG: catalogJobs called");
        System.out.println("🌐 DEBUG: Website URL: " + websiteUrl);
        System.out.println("🔎 DEBUG: Search term: " + searchTerm);
        System.out.println("📄 DEBUG: Max pages: " + maxPages);

        if (page == null) {
            System.err.println("❌ DEBUG: Page is null! Cannot proceed.");
            return;
        }

        try {
            String currentUrl = page.url();
            System.out.println("📍 DEBUG: Current page URL: " + currentUrl);

            // Check if we're already on the target domain
            boolean needsNavigation = true;
            if (currentUrl != null && !currentUrl.equals("about:blank") && !currentUrl.equals("data:,")) {
                try {
                    java.net.URL current = new java.net.URL(currentUrl);
                    java.net.URL target = new java.net.URL(websiteUrl);

                    if (current.getHost().equals(target.getHost())) {
                        System.out.println("✅ DEBUG: Already on target domain, skipping navigation");
                        needsNavigation = false;
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ DEBUG: URL comparison failed, will navigate anyway");
                }
            }

            if (needsNavigation) {
                System.out.println("🌐 DEBUG: About to navigate to: " + websiteUrl);

                // Navigate with explicit options
                Response response = page.navigate(websiteUrl, new Page.NavigateOptions()
                        .setTimeout(60000)
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                );

                System.out.println("📥 DEBUG: Navigation response status: " + (response != null ? response.status() : "null"));

                // Wait for page to be ready
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                System.out.println("✅ DEBUG: DOM content loaded");

                // Handle Cloudflare challenge if it appears again
                handleCloudflareChallenge();
            }

            System.out.println("📍 DEBUG: Current page URL after navigation: " + page.url());
            System.out.println("📄 DEBUG: Page title: " + page.title());

            // Additional wait for dynamic content
            System.out.println("⏳ DEBUG: Waiting 3 seconds for page to settle...");
            page.waitForTimeout(3000);

            // Check if we actually navigated successfully
            String finalUrl = page.url();
            if (finalUrl.equals("about:blank") || finalUrl.equals("data:,")) {
                System.err.println("❌ DEBUG: Still on blank page after navigation!");
                return;
            }

            System.out.println("✅ DEBUG: Successfully navigated to: " + finalUrl);

            // Check login status
            checkLoginStatus(websiteUrl);

            // Try to perform search
            System.out.println("🔍 DEBUG: About to perform search...");
            if (performSearch(searchTerm)) {
                System.out.println("✅ DEBUG: Search performed successfully");
                page.waitForTimeout(4000);

                // Extract jobs from pages
                for (int pageNum = 1; pageNum <= maxPages; pageNum++) {
                    System.out.println("\n📋 DEBUG: Processing page " + pageNum);
                    System.out.println("📍 DEBUG: Current URL: " + page.url());

                    List<JobInfo> jobs = extractJobs(websiteUrl);
                    System.out.println("📊 DEBUG: Found " + jobs.size() + " jobs on this page");

                    if (jobs.isEmpty()) {
                        System.out.println("❌ DEBUG: No jobs found, breaking");
                        break;
                    }

                    // Display jobs
                    for (int i = 0; i < jobs.size(); i++) {
                        JobInfo job = jobs.get(i);
                        System.out.printf("%d. %s\n", (i + 1), job);
                    }

                    // Try next page
                    if (pageNum < maxPages) {
                        System.out.println("🔄 DEBUG: Attempting to go to next page...");
                        if (!goToNextPage()) {
                            System.out.println("🛑 DEBUG: Could not find next page button");
                            break;
                        }
                        page.waitForTimeout(3000);
                    }
                }
            } else {
                System.out.println("❌ DEBUG: Could not perform search");
            }

        } catch (Exception e) {
            System.err.println("❌ DEBUG: Exception in catalogJobs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Rest of the methods remain the same...
    private void checkLoginStatus(String websiteUrl) {
        System.out.println("🔐 DEBUG: Checking login status for: " + websiteUrl);

        try {
            String pageContent = page.content().toLowerCase();
            System.out.println("📝 DEBUG: Page content length for login check: " + pageContent.length());

            if (websiteUrl.contains("linkedin.com")) {
                System.out.println("🔍 DEBUG: Checking LinkedIn login status...");
                try {
                    boolean loggedIn = page.locator(".global-nav__me").isVisible()
                            || page.locator("[data-test-global-nav-me]").isVisible();
                    System.out.println("✅ DEBUG: LinkedIn login status: " + (loggedIn ? "LOGGED IN" : "NOT LOGGED IN"));
                } catch (Exception e) {
                    System.out.println("❓ DEBUG: LinkedIn login check failed: " + e.getMessage());
                }
            } else if (websiteUrl.contains("indeed.com")) {
                System.out.println("🔍 DEBUG: Checking Indeed login status...");
                try {
                    boolean loggedIn = page.locator("[data-testid='gnav-AccountMenu-button']").isVisible();
                    System.out.println("✅ DEBUG: Indeed login status: " + (loggedIn ? "LOGGED IN" : "NOT LOGGED IN"));
                } catch (Exception e) {
                    System.out.println("❓ DEBUG: Indeed login check failed: " + e.getMessage());
                }
            } else {
                System.out.println("🔍 DEBUG: Generic login check...");
                if (pageContent.contains("sign out") || pageContent.contains("logout")) {
                    System.out.println("✅ DEBUG: Appears to be logged in (found sign out)");
                } else if (pageContent.contains("sign in") || pageContent.contains("login")) {
                    System.out.println("⚠️ DEBUG: May need to log in (found sign in)");
                } else {
                    System.out.println("❓ DEBUG: Login status unclear");
                }
            }

        } catch (Exception e) {
            System.out.println("❓ DEBUG: Could not check login status: " + e.getMessage());
        }
    }

    private boolean performSearch(String searchTerm) {
        System.out.println("🔍 DEBUG: performSearch called with term: " + searchTerm);

        for (int i = 0; i < SEARCH_SELECTORS.length; i++) {
            String selector = SEARCH_SELECTORS[i];
            System.out.println("🔍 DEBUG: Trying search selector " + (i + 1) + "/" + SEARCH_SELECTORS.length + ": " + selector);

            try {
                Locator searchBox = page.locator(selector).first();
                System.out.println("📍 DEBUG: Locator created for: " + selector);

                boolean isVisible = searchBox.isVisible();
                System.out.println("👁️ DEBUG: Search box visible: " + isVisible);

                if (isVisible) {
                    System.out.println("✅ DEBUG: Found visible search box with selector: " + selector);

                    // Add human-like delay
                    int randomDelay = 500 + (int) (Math.random() * 1500);
                    page.waitForTimeout(randomDelay);

                    // Scroll to element
                    System.out.println("📜 DEBUG: Scrolling to search box...");
                    searchBox.scrollIntoViewIfNeeded();
                    page.waitForTimeout(1000);

                    // Click to focus
                    System.out.println("🖱️ DEBUG: Clicking search box...");
                    searchBox.click();
                    page.waitForTimeout(500);

                    // Clear existing content
                    System.out.println("🗑️ DEBUG: Clearing search box...");
                    searchBox.fill("");
                    page.waitForTimeout(500);

                    // Type search term with human-like delays
                    System.out.println("⌨️ DEBUG: Typing search term...");
                    searchBox.type(searchTerm, new Locator.TypeOptions().setDelay(100 + (int) (Math.random() * 100)));
                    page.waitForTimeout(1000);

                    // Try to submit
                    System.out.println("🚀 DEBUG: Attempting to submit search...");
                    if (findAndClickSubmitButton()) {
                        System.out.println("✅ DEBUG: Submit button clicked");
                    } else {
                        System.out.println("⌨️ DEBUG: Pressing Enter...");
                        searchBox.press("Enter");
                    }

                    // Wait for results
                    System.out.println("⏳ DEBUG: Waiting for search results...");
                    page.waitForTimeout(3000);

                    return true;
                }
            } catch (Exception e) {
                System.out.println("❌ DEBUG: Error with selector " + selector + ": " + e.getMessage());
                continue;
            }
        }

        System.out.println("❌ DEBUG: Could not find any search box");
        return false;
    }

    private boolean findAndClickSubmitButton() {
        System.out.println("🔍 DEBUG: Looking for submit button...");

        String[] submitSelectors = {
            "button[type='submit']", "input[type='submit']",
            "button:has-text('Search')", "button:has-text('Find')",
            ".search-button", "[data-testid*='search-button']"
        };

        for (String selector : submitSelectors) {
            try {
                System.out.println("🔍 DEBUG: Trying submit selector: " + selector);
                Locator submitButton = page.locator(selector).first();
                if (submitButton.isVisible()) {
                    System.out.println("✅ DEBUG: Found submit button: " + selector);

                    // Add human-like delay before clicking
                    int randomDelay = 200 + (int) (Math.random() * 800);
                    page.waitForTimeout(randomDelay);

                    submitButton.click();
                    return true;
                }
            } catch (Exception e) {
                System.out.println("❌ DEBUG: Submit selector failed: " + selector);
                continue;
            }
        }

        System.out.println("❌ DEBUG: No submit button found");
        return false;
    }

    private List<JobInfo> extractJobs(String baseUrl) {
        System.out.println("📊 DEBUG: extractJobs called");
        List<JobInfo> jobs = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        // Scroll to load content
        System.out.println("📜 DEBUG: Scrolling to load dynamic content...");
        try {
            for (int i = 0; i < 3; i++) {
                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(1000);
            }
            page.evaluate("window.scrollTo(0, 0)");
            page.waitForTimeout(500);
        } catch (Exception e) {
            System.out.println("❌ DEBUG: Scrolling failed: " + e.getMessage());
        }

        for (int i = 0; i < JOB_LINK_SELECTORS.length; i++) {
            String selector = JOB_LINK_SELECTORS[i];
            System.out.println("🔍 DEBUG: Trying job selector " + (i + 1) + "/" + JOB_LINK_SELECTORS.length + ": " + selector);

            try {
                Locator jobLinks = page.locator(selector);
                int count = jobLinks.count();
                System.out.println("📊 DEBUG: Found " + count + " elements with selector: " + selector);

                for (int j = 0; j < count && j < 50; j++) { // Limit to 50 per selector
                    try {
                        Locator link = jobLinks.nth(j);

                        String url = link.getAttribute("href");
                        String title = getJobTitle(link);

                        System.out.println("🔗 DEBUG: Link " + j + " - URL: " + url + ", Title: " + title);

                        if (isValidJob(url, title, seenUrls)) {
                            seenUrls.add(url);

                            JobInfo job = new JobInfo();
                            job.title = cleanText(title);
                            job.url = makeAbsoluteUrl(url, baseUrl);
                            job.company = findNearbyText(link, "company");
                            job.location = findNearbyText(link, "location");

                            jobs.add(job);
                            System.out.println("✅ DEBUG: Added valid job: " + job.title);
                        }
                    } catch (Exception e) {
                        System.out.println("❌ DEBUG: Error processing link " + j + ": " + e.getMessage());
                        continue;
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ DEBUG: Error with job selector " + selector + ": " + e.getMessage());
                continue;
            }
        }

        System.out.println("📊 DEBUG: Total jobs extracted: " + jobs.size());
        return jobs;
    }

    private String getJobTitle(Locator link) {
        try {
            String title = link.textContent();
            if (title != null && !title.trim().isEmpty() && title.length() < 200) {
                return title.trim();
            }

            title = link.getAttribute("title");
            if (title != null && !title.isEmpty()) {
                return title;
            }

            title = link.getAttribute("aria-label");
            if (title != null && !title.isEmpty()) {
                return title;
            }

        } catch (Exception e) {
            // Ignore
        }
        return "";
    }

    private String findNearbyText(Locator link, String type) {
        // Simplified for debugging.  
        // If you want, you could look for text near the link in the DOM.
        return "";
    }

    private boolean isValidJob(String url, String title, Set<String> seenUrls) {
        if (url == null || url.isEmpty() || title == null || title.isEmpty()) {
            return false;
        }

        if (seenUrls.contains(url)) {
            return false;
        }

        String lowerTitle = title.toLowerCase();
        String[] jobKeywords = {
            "engineer", "developer", "analyst", "manager", "specialist",
            "coordinator", "associate", "intern", "position", "role"
        };

        for (String keyword : jobKeywords) {
            if (lowerTitle.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean goToNextPage() {
        System.out.println("🔄 DEBUG: goToNextPage called");

        String[] nextPageSelectors = {
            "a[aria-label*='Next' i]",
            "button[aria-label*='Next' i]",
            "a:has-text('Next')",
            "button:has-text('Next')",
            ".pn", // Indeed specific
            "[data-testid*='pagination-page-next']",
            ".np:last-child", // Google Jobs
            "[aria-label*='next page' i]"
        };

        for (String selector : nextPageSelectors) {
            try {
                System.out.println("🔍 DEBUG: Trying next page selector: " + selector);
                Locator nextButton = page.locator(selector).first();
                if (nextButton.isVisible() && nextButton.isEnabled()) {
                    System.out.println("✅ DEBUG: Found next button: " + selector);

                    // Add human-like delay
                    int randomDelay = 1000 + (int) (Math.random() * 2000);
                    page.waitForTimeout(randomDelay);

                    nextButton.click();
                    page.waitForTimeout(2000);
                    return true;
                }
            } catch (Exception e) {
                System.out.println("❌ DEBUG: Next page selector failed: " + selector);
                continue;
            }
        }

        return false;
    }

    private String makeAbsoluteUrl(String url, String baseUrl) {
        if (url == null || url.startsWith("http")) {
            return url;
        }
        try {
            return new java.net.URL(new java.net.URL(baseUrl), url).toString();
        } catch (Exception e) {
            return url;
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
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
        if (browser != null) {
            browser.close();
            System.out.println("✅ DEBUG: Browser closed");
        }
    }

    private static class JobInfo {

        String title = "";
        String company = "";
        String location = "";
        String url = "";

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("📝 ").append(title);
            if (!company.isEmpty()) {
                sb.append(" | 🏢 ").append(company);
            }
            if (!location.isEmpty()) {
                sb.append(" | 📍 ").append(location);
            }
            sb.append("\n   🔗 ").append(url);
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        GenericJobCatalogPlaywright catalog = new GenericJobCatalogPlaywright();

        try {
            // Setup browser
            catalog.setupBrowser();

            // Open browser for manual login (will handle Cloudflare automatically)
            catalog.openForLogin("https://www.reed.co.uk/");

            // After login, perform job search
            catalog.catalogJobs("https://www.reed.co.uk/", "software engineer", 1);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            catalog.close();
        }
    }
}
