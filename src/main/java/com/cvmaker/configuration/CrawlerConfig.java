package com.cvmaker.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import lombok.Data;

@Data
public class CrawlerConfig {

    private static final String DEFAULT_CONFIG_FILE = "configuration/reedcrawler.properties";

    // Main configuration
    private String configFile;
    private String crawlerName = "Reed";

    // CV Generation settings
    private String cvConfigFile;

    // Crawler settings
    private int maxApplications;
    private boolean debugMode;

    // Visualization settings
    private boolean visualizationEnabled;
    private int pollingRate;
    private int crawlingSpeed;

    // Browser settings
    private String browserDataDir;
    private boolean headless;
    private int slowMo;
    private int viewportWidth;
    private int viewportHeight;
    private String userAgent;
    private String browserArgs;
    private String acceptHeader;
    private String acceptLanguageHeader;

    // Timing settings
    private int pageTimeout;
    private int navigationTimeout;
    private int pageLoadDelay;
    private int searchResultsDelay;
    private int jobCardLoadDelay;
    private int applicationDelay;
    private int applicationStepDelay;
    private int elementInteractionDelay;
    private int processingStartDelay;
    private int processingTimeout;
    private int processingCompleteDelay;
    private int processingFallbackDelay;
    private int confirmationDialogDelay;

    // Site settings
    private String baseUrl;
    private String searchKeywords;

    public CrawlerConfig() throws IOException {
        this(DEFAULT_CONFIG_FILE);
    }

    public CrawlerConfig(String configFilePath) throws IOException {
        this.configFile = configFilePath;
        loadConfiguration();
    }

    /**
     * Create a config specifically for a crawler type
     */
    public CrawlerConfig(String configFilePath, String crawlerName) throws IOException {
        this.configFile = configFilePath;
        this.crawlerName = crawlerName;
        loadConfiguration();
    }

    private void loadConfiguration() throws IOException {
        setDefaults();

        Properties properties = new Properties();
        Path configPath = Paths.get(configFile);

        if (!Files.exists(configPath)) {
            System.out.println("Warning: Crawler configuration file not found: " + configFile + ", using defaults");
            return;
        }

        try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
            properties.load(fis);
        }

        loadProperties(properties);

        System.out.println("Crawler configuration loaded from: " + configPath.toAbsolutePath());
    }

    private void setDefaults() {
        // CV Generation
        this.cvConfigFile = "configuration/config.properties";

        // Crawler settings
        this.maxApplications = 10;
        this.debugMode = false;

        // Visualization and speed settings
        this.visualizationEnabled = true;
        this.pollingRate = 500;
        this.crawlingSpeed = 5;

        // Browser settings
        this.browserDataDir = "playwright-session";
        this.headless = false;
        this.slowMo = 1000;
        this.viewportWidth = 1920;
        this.viewportHeight = 1080;
        this.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        this.browserArgs = "--disable-blink-features=AutomationControlled,--disable-dev-shm-usage,--no-sandbox,--disable-web-security";
        this.acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
        this.acceptLanguageHeader = "en-US,en;q=0.9";

        // Timing settings
        this.pageTimeout = 60000;
        this.navigationTimeout = 60000;
        this.pageLoadDelay = 3000;
        this.searchResultsDelay = 3000;
        this.jobCardLoadDelay = 2000;
        this.applicationDelay = 5000;
        this.applicationStepDelay = 2000;
        this.elementInteractionDelay = 1000;
        this.processingStartDelay = 2000;
        this.processingTimeout = 30000;
        this.processingCompleteDelay = 3000;
        this.processingFallbackDelay = 5000;
        this.confirmationDialogDelay = 2000;

        this.baseUrl = "";
        this.searchKeywords = "junior software development";
    }

    private void loadProperties(Properties properties) {
        // Load crawler type-specific config if present
        String configCrawlerName = TypedProperties.stringProp(properties, "crawler.name", "");
        if (!configCrawlerName.isEmpty()) {
            this.crawlerName = configCrawlerName;
        }

        // CV Generation
        this.cvConfigFile = TypedProperties.stringProp(properties, "cv.config.file", this.cvConfigFile);

        // Crawler settings
        this.maxApplications = TypedProperties.intProp(properties, "crawler.max.applications", this.maxApplications, 0);
        this.debugMode = TypedProperties.boolProp(properties, "crawler.debug.mode", this.debugMode);

        // Visualization and speed settings
        this.visualizationEnabled = TypedProperties.boolProp(properties, "crawler.visualization.enabled", this.visualizationEnabled);
        this.pollingRate = TypedProperties.intProp(properties, "crawler.polling.rate", this.pollingRate, 1);
        this.crawlingSpeed = TypedProperties.intProp(properties, "crawler.speed", this.crawlingSpeed);

        // Validate crawling speed (1-10)
        if (this.crawlingSpeed < 1) {
            this.crawlingSpeed = 1;
        }
        if (this.crawlingSpeed > 10) {
            this.crawlingSpeed = 10;
        }

        // Browser settings
        this.browserDataDir = TypedProperties.stringProp(properties, "browser.data.dir", this.browserDataDir);
        this.headless = TypedProperties.boolProp(properties, "browser.headless", this.headless);
        this.slowMo = TypedProperties.intProp(properties, "browser.slow.mo", this.slowMo, 0);
        this.viewportWidth = TypedProperties.intProp(properties, "browser.viewport.width", this.viewportWidth, 1);
        this.viewportHeight = TypedProperties.intProp(properties, "browser.viewport.height", this.viewportHeight, 1);
        this.userAgent = TypedProperties.stringProp(properties, "browser.user.agent", this.userAgent);
        this.browserArgs = TypedProperties.stringProp(properties, "browser.args", this.browserArgs);
        this.acceptHeader = TypedProperties.stringProp(properties, "browser.accept.header", this.acceptHeader);
        this.acceptLanguageHeader = TypedProperties.stringProp(properties, "browser.accept.language.header", this.acceptLanguageHeader);

        // Timing settings (milliseconds, never negative)
        this.pageTimeout = TypedProperties.intProp(properties, "timing.page.timeout", this.pageTimeout, 0);
        this.navigationTimeout = TypedProperties.intProp(properties, "timing.navigation.timeout", this.navigationTimeout, 0);
        this.pageLoadDelay = TypedProperties.intProp(properties, "timing.page.load.delay", this.pageLoadDelay, 0);
        this.searchResultsDelay = TypedProperties.intProp(properties, "timing.search.results.delay", this.searchResultsDelay, 0);
        this.jobCardLoadDelay = TypedProperties.intProp(properties, "timing.job.card.load.delay", this.jobCardLoadDelay, 0);
        this.applicationDelay = TypedProperties.intProp(properties, "timing.application.delay", this.applicationDelay, 0);
        this.applicationStepDelay = TypedProperties.intProp(properties, "timing.application.step.delay", this.applicationStepDelay, 0);
        this.elementInteractionDelay = TypedProperties.intProp(properties, "timing.element.interaction.delay", this.elementInteractionDelay, 0);
        this.processingStartDelay = TypedProperties.intProp(properties, "timing.processing.start.delay", this.processingStartDelay, 0);
        this.processingTimeout = TypedProperties.intProp(properties, "timing.processing.timeout", this.processingTimeout, 0);
        this.processingCompleteDelay = TypedProperties.intProp(properties, "timing.processing.complete.delay", this.processingCompleteDelay, 0);
        this.processingFallbackDelay = TypedProperties.intProp(properties, "timing.processing.fallback.delay", this.processingFallbackDelay, 0);
        this.confirmationDialogDelay = TypedProperties.intProp(properties, "timing.confirmation.dialog.delay", this.confirmationDialogDelay, 0);

        // Site settings
        this.baseUrl = TypedProperties.stringProp(properties, "site.base.url", this.baseUrl);
        this.searchKeywords = TypedProperties.stringProp(properties, "site.search.keywords", this.searchKeywords);
    }

    /**
     * Create a site-specific configuration by name
     */
    public static CrawlerConfig forSite(String siteName) throws IOException {
        String configFile = "configuration/" + siteName.toLowerCase() + "crawler.properties";
        return new CrawlerConfig(configFile, siteName);
    }
}
