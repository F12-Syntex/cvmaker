package com.cvmaker.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import lombok.Data;

@Data
public class CrawlerConfig {

    private static final String DEFAULT_CONFIG_FILE = "configuration/reedcrawler.properties";

    /** Property that opts in to {@link #UNSAFE_BROWSER_ARGS}. Defaults to {@code false}. */
    public static final String UNSAFE_BROWSER_ARGS_PROPERTY = "browser.unsafe.args.enabled";

    /**
     * Chromium flags that weaken the browser's security. The crawler uses a persistent profile that
     * holds the user's logins and visits arbitrary sites, so these flags are only passed to Chromium
     * when {@link #UNSAFE_BROWSER_ARGS_PROPERTY} is {@code true}. Without the opt-in they are removed
     * from {@code browser.args}; with it they are added if missing.
     */
    public static final List<String> UNSAFE_BROWSER_ARGS = List.of("--no-sandbox", "--disable-web-security");

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
    /** Configured {@code browser.args}; read it through {@link #getBrowserArgs()}, which applies the opt-in. */
    private String browserArgs;
    /** {@code browser.unsafe.args.enabled}: pass {@link #UNSAFE_BROWSER_ARGS} to Chromium. */
    private boolean unsafeBrowserArgsEnabled;
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
        this.browserArgs = "--disable-blink-features=AutomationControlled,--disable-dev-shm-usage";
        this.unsafeBrowserArgsEnabled = false;
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
        this.unsafeBrowserArgsEnabled = TypedProperties.boolProp(properties, UNSAFE_BROWSER_ARGS_PROPERTY, this.unsafeBrowserArgsEnabled);
        warnAboutUnsafeBrowserArgs();
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
     * Effective Chromium arguments as a comma-separated string (the format callers split on ",").
     * {@link #UNSAFE_BROWSER_ARGS} are only included when {@code browser.unsafe.args.enabled=true}.
     */
    public String getBrowserArgs() {
        return String.join(",", getBrowserArgList());
    }

    /** Effective Chromium arguments, trimmed, without empty entries, unsafe flags only when opted in. */
    public List<String> getBrowserArgList() {
        return effectiveBrowserArgs(browserArgs, unsafeBrowserArgsEnabled);
    }

    /**
     * Compute the Chromium arguments to launch with from the configured comma-separated list.
     * Without {@code allowUnsafe} every {@link #UNSAFE_BROWSER_ARGS} entry (also in the
     * {@code --flag=value} form) is removed; with it, missing unsafe flags are appended.
     */
    static List<String> effectiveBrowserArgs(String configuredArgs, boolean allowUnsafe) {
        List<String> result = new ArrayList<>();
        for (String arg : TypedProperties.splitList(configuredArgs)) {
            if (!allowUnsafe && isUnsafeBrowserArg(arg)) {
                continue;
            }
            if (!result.contains(arg)) {
                result.add(arg);
            }
        }
        if (allowUnsafe) {
            for (String unsafe : UNSAFE_BROWSER_ARGS) {
                if (result.stream().noneMatch(arg -> flagName(arg).equals(unsafe))) {
                    result.add(unsafe);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean isUnsafeBrowserArg(String arg) {
        return UNSAFE_BROWSER_ARGS.contains(flagName(arg));
    }

    private static String flagName(String arg) {
        int eq = arg.indexOf('=');
        return eq < 0 ? arg : arg.substring(0, eq);
    }

    private void warnAboutUnsafeBrowserArgs() {
        if (unsafeBrowserArgsEnabled) {
            System.out.println("Warning: " + UNSAFE_BROWSER_ARGS_PROPERTY + "=true, launching Chromium with "
                    + UNSAFE_BROWSER_ARGS + " on profile '" + browserDataDir + "'. Only use this on a throwaway profile.");
            return;
        }
        List<String> dropped = new ArrayList<>();
        for (String arg : TypedProperties.splitList(browserArgs)) {
            if (isUnsafeBrowserArg(arg)) {
                dropped.add(arg);
            }
        }
        if (!dropped.isEmpty()) {
            System.out.println("Warning: ignoring unsafe browser.args " + dropped + "; set "
                    + UNSAFE_BROWSER_ARGS_PROPERTY + "=true to allow them.");
        }
    }

    /**
     * Create a site-specific configuration by name
     */
    public static CrawlerConfig forSite(String siteName) throws IOException {
        String configFile = "configuration/" + siteName.toLowerCase() + "crawler.properties";
        return new CrawlerConfig(configFile, siteName);
    }
}
