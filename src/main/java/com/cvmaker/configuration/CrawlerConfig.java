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
    private static final String INDEED_CONFIG_FILE = "configuration/indeedcrawler.properties";

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
        this.cvConfigFile = "config.properties";

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
        String configCrawlerName = properties.getProperty("crawler.name");
        if (configCrawlerName != null && !configCrawlerName.isEmpty()) {
            this.crawlerName = configCrawlerName;
        }

        // CV Generation
        this.cvConfigFile = properties.getProperty("cv.config.file", this.cvConfigFile);

        // Crawler settings
        this.maxApplications = Integer.parseInt(properties.getProperty("crawler.max.applications", String.valueOf(this.maxApplications)));
        this.debugMode = Boolean.parseBoolean(properties.getProperty("crawler.debug.mode", String.valueOf(this.debugMode)));

        // Visualization and speed settings
        this.visualizationEnabled = Boolean.parseBoolean(properties.getProperty("crawler.visualization.enabled", String.valueOf(this.visualizationEnabled)));
        this.pollingRate = Integer.parseInt(properties.getProperty("crawler.polling.rate", String.valueOf(this.pollingRate)));
        this.crawlingSpeed = Integer.parseInt(properties.getProperty("crawler.speed", String.valueOf(this.crawlingSpeed)));

        // Validate crawling speed (1-10)
        if (this.crawlingSpeed < 1) {
            this.crawlingSpeed = 1;
        }
        if (this.crawlingSpeed > 10) {
            this.crawlingSpeed = 10;
        }

        // Browser settings
        this.browserDataDir = properties.getProperty("browser.data.dir", this.browserDataDir);
        this.headless = Boolean.parseBoolean(properties.getProperty("browser.headless", String.valueOf(this.headless)));
        this.slowMo = Integer.parseInt(properties.getProperty("browser.slow.mo", String.valueOf(this.slowMo)));
        this.viewportWidth = Integer.parseInt(properties.getProperty("browser.viewport.width", String.valueOf(this.viewportWidth)));
        this.viewportHeight = Integer.parseInt(properties.getProperty("browser.viewport.height", String.valueOf(this.viewportHeight)));
        this.userAgent = properties.getProperty("browser.user.agent", this.userAgent);
        this.browserArgs = properties.getProperty("browser.args", this.browserArgs);
        this.acceptHeader = properties.getProperty("browser.accept.header", this.acceptHeader);
        this.acceptLanguageHeader = properties.getProperty("browser.accept.language.header", this.acceptLanguageHeader);

        // Timing settings
        this.pageTimeout = Integer.parseInt(properties.getProperty("timing.page.timeout", String.valueOf(this.pageTimeout)));
        this.navigationTimeout = Integer.parseInt(properties.getProperty("timing.navigation.timeout", String.valueOf(this.navigationTimeout)));
        this.pageLoadDelay = Integer.parseInt(properties.getProperty("timing.page.load.delay", String.valueOf(this.pageLoadDelay)));
        this.searchResultsDelay = Integer.parseInt(properties.getProperty("timing.search.results.delay", String.valueOf(this.searchResultsDelay)));
        this.jobCardLoadDelay = Integer.parseInt(properties.getProperty("timing.job.card.load.delay", String.valueOf(this.jobCardLoadDelay)));
        this.applicationDelay = Integer.parseInt(properties.getProperty("timing.application.delay", String.valueOf(this.applicationDelay)));
        this.applicationStepDelay = Integer.parseInt(properties.getProperty("timing.application.step.delay", String.valueOf(this.applicationStepDelay)));
        this.elementInteractionDelay = Integer.parseInt(properties.getProperty("timing.element.interaction.delay", String.valueOf(this.elementInteractionDelay)));
        this.processingStartDelay = Integer.parseInt(properties.getProperty("timing.processing.start.delay", String.valueOf(this.processingStartDelay)));
        this.processingTimeout = Integer.parseInt(properties.getProperty("timing.processing.timeout", String.valueOf(this.processingTimeout)));
        this.processingCompleteDelay = Integer.parseInt(properties.getProperty("timing.processing.complete.delay", String.valueOf(this.processingCompleteDelay)));
        this.processingFallbackDelay = Integer.parseInt(properties.getProperty("timing.processing.fallback.delay", String.valueOf(this.processingFallbackDelay)));
        this.confirmationDialogDelay = Integer.parseInt(properties.getProperty("timing.confirmation.dialog.delay", String.valueOf(this.confirmationDialogDelay)));

        // Site settings
        this.baseUrl = properties.getProperty("site.base.url", this.baseUrl);
        this.searchKeywords = properties.getProperty("site.search.keywords", this.searchKeywords);
    }

    /**
     * Create a site-specific configuration by name
     */
    public static CrawlerConfig forSite(String siteName) throws IOException {
        String configFile = "configuration/" + siteName.toLowerCase() + "crawler.properties";
        return new CrawlerConfig(configFile, siteName);
    }
}
