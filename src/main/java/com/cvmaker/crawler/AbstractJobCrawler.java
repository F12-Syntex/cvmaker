package com.cvmaker.crawler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import com.cvmaker.CVGenerator;
import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.cache.PageInputCacheManagerCSV;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Abstract base implementation for all job site crawlers. Handles common
 * functionality like browser setup, CV generation, etc.
 */
@Data
public abstract class AbstractJobCrawler implements JobCrawler {

    protected Playwright playwright;
    protected BrowserContext context;
    protected Page page;

    // Visual feedback in the browser window. Never null: a no-op object until
    // setupBrowser() has run, after close(), and when
    // crawler.visualization.enabled is false, so subclasses can call it freely.
    protected PageVisualizer visualizer = PageVisualizer.disabled();

    // Configuration
    protected ConfigManager config;
    protected CrawlerConfig crawlerConfig;

    // Application tracking
    protected int applicationsSubmitted = 0;
    protected int jobsChecked = 0;
    protected int easyApplyJobsFound = 0;

    // One CV generator, and so one AI client and one executor thread, for the
    // whole session. Created on first use, shut down in close().
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CVGenerator cvGenerator;

    public AbstractJobCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public AbstractJobCrawler(CrawlerConfig crawlerConfig) throws Exception {
        this.crawlerConfig = crawlerConfig;
        this.config = new ConfigManager(crawlerConfig.getCvConfigFile());
    }

    @Override
    public void initialize() throws Exception {
        initializeDirectories();
    }

    protected void initializeDirectories() throws Exception {
        Files.createDirectories(Paths.get(config.getOutputDirectory()));
    }

    /**
     * Launch the browser and open the main page.
     *
     * <p>If anything fails, whatever was already started is closed again and the
     * exception is rethrown, so a caller never continues with a null page and
     * the Playwright driver process is not leaked.
     *
     * @throws RuntimeException if the browser cannot be launched
     */
    @Override
    public synchronized void setupBrowser() {
        // A repeated call must not leak the browser from the previous one.
        closeBrowser();

        try {
            playwright = createPlaywright();

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

            this.visualizer = new PageVisualizer(page, crawlerConfig.isVisualizationEnabled());

            // 🔹 Automatically cache the main page on load
            page.onLoad(p -> PageInputCacheManagerCSV.saveInputCache(getCrawlerName(), p));

            // 🔹 Automatically cache *all new pages* (popups, tabs, redirects)
            context.onPage(p -> p.onLoad(loaded -> PageInputCacheManagerCSV.saveInputCache(getCrawlerName(), loaded)));

            System.out.println("Browser setup completed for " + getCrawlerName());

        } catch (RuntimeException e) {
            System.out.println("Browser setup failed for " + getCrawlerName() + ": " + e.getMessage());
            closeBrowser();
            throw e;
        }
    }

    /**
     * Start the Playwright driver. Kept as a separate method so that tests can
     * supply a mock instead of launching a real browser.
     */
    protected Playwright createPlaywright() {
        return Playwright.create();
    }

    @Override
    public void openForLogin() {
        if (page == null) {
            throw new IllegalStateException("Browser is not set up; call setupBrowser() before openForLogin()");
        }

        try {
            page.navigate(crawlerConfig.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(crawlerConfig.getPageLoadDelay());

            System.out.println("Login to " + crawlerConfig.getBaseUrl() + " if needed, then press ENTER...");
            visualizer.visualizeAction("Log in if needed, then press ENTER in the console");
            // Consume the whole line, so a leftover '\n' (Windows sends "\r\n")
            // does not answer the next prompt by itself.
            ConsolePrompt.waitForEnter(System.in);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Release the browser and the CV generator. Safe to call more than once,
     * before {@link #setupBrowser()}, and after a failed setup: every resource
     * is closed in its own step, so one failing close does not leak the others.
     */
    @Override
    public synchronized void close() {
        closeBrowser();

        CVGenerator generator = cvGenerator;
        cvGenerator = null;
        if (generator != null) {
            try {
                generator.shutdown();
            } catch (Exception e) {
                System.out.println("Error shutting down CV generator for " + getCrawlerName() + ": " + e.getMessage());
            }
        }
    }

    private void closeBrowser() {
        Page oldPage = page;
        BrowserContext oldContext = context;
        Playwright oldPlaywright = playwright;

        // Clear the fields first: a second close() then has nothing to do.
        page = null;
        context = null;
        playwright = null;
        visualizer = PageVisualizer.disabled();

        closeQuietly("page", oldPage);
        closeQuietly("browser context", oldContext);
        closeQuietly("Playwright", oldPlaywright);
    }

    private void closeQuietly(String what, AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            System.out.println("Error closing " + what + " for " + getCrawlerName() + ": " + e.getMessage());
        }
    }

    @Override
    public void setMaxApplications(int maxApplications) {
        this.crawlerConfig.setMaxApplications(maxApplications);
    }

    protected void printSessionSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println(getCrawlerName().toUpperCase() + " JOB APPLICATION SESSION COMPLETED");
        System.out.println("=".repeat(60));
        System.out.println("Total jobs checked: " + jobsChecked);
        System.out.println("Easy Apply jobs found: " + easyApplyJobsFound);
        System.out.println("Applications submitted: " + applicationsSubmitted);
        System.out.println("=".repeat(60));
    }

    protected Path generateCVForJob(JobInfo job, String jobContent) {
        try {
            System.out.println("Generating CV for: " + (job == null ? null : job.getTitle())
                    + " at " + (job == null ? null : job.getCompany()));

            if (jobContent == null || jobContent.trim().isEmpty()) {
                System.out.println("No job content found, skipping CV generation");
                return null;
            }

            // One folder per job: title and company for readability, plus a hash
            // so that two jobs with the same title do not overwrite each other.
            Path jobFolderPath = Paths.get(config.getOutputDirectory(), JobFolderNamer.folderName(job, jobContent));
            Files.createDirectories(jobFolderPath);
            visualizer.visualizeAction("Generating CV: " + (job == null ? null : job.getTitle()));

            // Create config for this specific job
            config.setJobDescriptionContent(jobContent);

            // Generate CV with the session's CVGenerator (shut down in close())
            CVGenerator generator = cvGenerator();

            // Temporarily point the output directory at the job folder, and
            // always restore it: a failed job must not redirect the next ones.
            String originalOutputDir = config.getOutputDirectory();
            config.setOutputDirectory(jobFolderPath.toString());
            try {
                generator.generate();
            } finally {
                config.setOutputDirectory(originalOutputDir);
            }

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

    /**
     * Build the CV generator for this crawler. Called at most once per session
     * (again only after {@link #close()}). Overridable for tests.
     */
    protected CVGenerator createCvGenerator() {
        return new CVGenerator(config);
    }

    private synchronized CVGenerator cvGenerator() {
        if (cvGenerator == null) {
            cvGenerator = createCvGenerator();
        }
        return cvGenerator;
    }

    protected int adjustedDelay(int baseDelay) {
        // Calculate adjusted delay based on crawling speed (1-10)
        // Speed 10 = fastest (0.3x delay), Speed 1 = slowest (1.5x delay)
        float speedMultiplier = 1.5f - (crawlerConfig.getCrawlingSpeed() * 0.12f);
        return Math.max(100, Math.round(baseDelay * speedMultiplier));
    }
}
