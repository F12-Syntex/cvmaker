package com.cvmaker.crawler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import com.cvmaker.CVGenerator;
import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

/**
 * Abstract base implementation for all job site crawlers. Handles common
 * functionality like browser setup, CV generation, etc.
 */
public abstract class AbstractJobCrawler implements JobCrawler {

    protected Playwright playwright;
    protected BrowserContext context;
    protected Page page;

    // Configuration
    protected ConfigManager config;
    protected CrawlerConfig crawlerConfig;

    // Application tracking
    protected int applicationsSubmitted = 0;
    protected int jobsChecked = 0;
    protected int easyApplyJobsFound = 0;

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

    @Override
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

            System.out.println("Browser setup completed for " + getCrawlerName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
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

    @Override
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
            System.out.println("Generating CV for: " + job.getTitle() + " at " + job.getCompany());

            if (jobContent == null || jobContent.trim().isEmpty()) {
                System.out.println("No job content found, skipping CV generation");
                return null;
            }

            // Create unique job folder
            String title = job.getTitle();
            // Remove everything after the 5th space
            String[] parts = title.split(" ");
            if (parts.length > 5) {
                title = String.join(" ", Arrays.copyOfRange(parts, 0, 5));
            }
            // Remove everything before the first '.'
            int dotIndex = title.indexOf('.');
            if (dotIndex != -1) {
                title = title.substring(dotIndex + 1).trim();
            }
            String uuid = "Job-" + title.replaceAll("[^a-zA-Z0-9-_ ]", "").replaceAll("\\s+", "_");
            String jobFolder = config.getOutputDirectory() + "/" + uuid;
            Path jobFolderPath = Paths.get(jobFolder);
            Files.createDirectories(jobFolderPath);

            // Create config for this specific job
            config.setJobDescriptionContent(jobContent);

            System.out.println(jobContent);

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

    protected int adjustedDelay(int baseDelay) {
        // Calculate adjusted delay based on crawling speed (1-10)
        // Speed 10 = fastest (0.3x delay), Speed 1 = slowest (1.5x delay)
        float speedMultiplier = 1.5f - (crawlerConfig.getCrawlingSpeed() * 0.12f);
        return Math.max(100, Math.round(baseDelay * speedMultiplier));
    }
}
