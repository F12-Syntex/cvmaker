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
    private String searchInputSelector;

    // Job search selectors
    private String jobCardSelectors;
    private String jobDescriptionSelectors;
    private String easyApplySelectors;
    private String easyApplyKeywords;

    // Application selectors
    private String applyButtonSelectors;
    private String updateButtonSelectors;
    private String cvUploadSelectors;
    private String fileInputSelectors;
    private String processingSelectors;
    private String submitButtonSelectors;
    private String confirmationSelectors;

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

        // Site-specific defaults
        switch (crawlerName.toLowerCase()) {
            case "reed":
                setReedDefaults();
                break;
            case "indeed":
                setIndeedDefaults();
                break;
            case "linkedin":
                setLinkedInDefaults();
                break;
            default:
                setReedDefaults(); // Default to Reed
        }
    }
    
    private void setReedDefaults() {
        // Site settings
        this.baseUrl = "https://www.reed.co.uk/";
        this.searchKeywords = "web development";
        this.searchInputSelector = "input[name='keywords']";

        // Job search selectors
        this.jobCardSelectors = ".job-card_jobCard__MkcJD,[class*='job-card_jobCard'],.job-result,.card.job-card,.job-card,article[data-qa='job-result'],[data-qa*='job'],.job-result-card";
        this.jobDescriptionSelectors = "article.card.job-card_jobCard__MkcJD,article[class*='job-card_jobCard'],[class*='job-card_jobCard__MkcJD'],article.card";
        this.easyApplySelectors = "button:has-text('Easy Apply'),a:has-text('Easy Apply'),[data-qa*='easy-apply'],[class*='easy-apply'],button:has-text('Quick Apply'),a:has-text('Quick Apply'),[data-qa*='quick-apply'],[class*='quick-apply'],.easy-apply,.quick-apply,button[class*='Easy'],a[class*='Easy']";
        this.easyApplyKeywords = "easy apply,quick apply";

        // Application selectors
        this.applyButtonSelectors = "button:has-text('Apply Now'),a:has-text('Apply Now'),button:has-text('Apply'),a:has-text('Apply'),[data-qa*='apply'],button[class*='apply'],a[class*='apply'],.apply-button,.btn-apply";
        this.updateButtonSelectors = "button:has-text('Update'),a:has-text('Update'),[data-qa*='update'],button[class*='update'],.update-button";
        this.cvUploadSelectors = "button:has-text('Choose your own CV file'),a:has-text('Choose your own CV file'),button:has-text('Choose CV'),button:has-text('Upload CV'),[data-qa*='upload-cv'],[data-qa*='choose-cv'],button[class*='cv-upload'],input[type='file'][accept*='pdf'],label[for*='cv'],.cv-upload-button";
        this.fileInputSelectors = "input[type='file'],input[accept*='pdf'],input[name*='cv'],input[id*='cv'],input[class*='cv']";
        this.processingSelectors = ":has-text('CV processing'),:has-text('Processing'),:has-text('Uploading'),.spinner,.loading,[class*='processing']";
        this.submitButtonSelectors = "button:has-text('Submit Application'),button:has-text('Submit'),a:has-text('Submit Application'),a:has-text('Submit'),[data-qa*='submit'],button[class*='submit'],.submit-button,.btn-submit";
        this.confirmationSelectors = "button:has-text('OK'),button:has-text('Ok'),button:has-text('Confirm'),button:has-text('Yes'),[data-qa*='confirm'],.modal button:has-text('OK'),.dialog button:has-text('OK')";
    }
    
    private void setIndeedDefaults() {
        // Indeed-specific settings
        this.baseUrl = "https://www.indeed.com/";
        this.searchInputSelector = "input#text-input-what,input[name='q']";
        
        // Job search selectors
        this.jobCardSelectors = ".job_seen_beacon,.resultContent,div[data-testid='job-card']";
        this.jobDescriptionSelectors = "#jobDescriptionText,#job-description";
        this.easyApplySelectors = ".iaP,.iaJobTitle button,button:has-text('Apply easily')";
        this.easyApplyKeywords = "apply now,easy apply,apply easily,quick apply";
        
        // Application selectors
        this.applyButtonSelectors = ".ia-IndeedApplyButton,button:has-text('Apply now'),a:has-text('Apply now')";
        this.updateButtonSelectors = "button:has-text('Edit'),button:has-text('Update')";
        this.cvUploadSelectors = "button:has-text('upload'),button:has-text('Upload'),label:has-text('Upload resume')";
        this.fileInputSelectors = "input[type='file'],input[accept*='pdf'],input[name*='resume']";
        this.processingSelectors = ".processing,.loader,div[data-testid='loading']";
        this.submitButtonSelectors = "button[type='submit'],button:has-text('Submit'),button:has-text('Apply')";
        this.confirmationSelectors = ".modal button:has-text('OK'),.dialog button:has-text('Done'),button:has-text('Continue')";
    }
    
    private void setLinkedInDefaults() {
        // LinkedIn-specific settings
        this.baseUrl = "https://www.linkedin.com/jobs/";
        this.searchInputSelector = "input.jobs-search-box__text-input";
        
        // Job search selectors
        this.jobCardSelectors = ".job-card-container,.jobs-search-results__list-item";
        this.jobDescriptionSelectors = ".jobs-description,.jobs-box__html-content";
        this.easyApplySelectors = "button.jobs-apply-button,button:has-text('Easy Apply')";
        this.easyApplyKeywords = "easy apply";
        
        // Application selectors
        this.applyButtonSelectors = "button.jobs-apply-button,button:has-text('Apply'),button:has-text('Easy Apply')";
        this.updateButtonSelectors = "button:has-text('Edit')";
        this.cvUploadSelectors = "button:has-text('Upload resume'),input[name='resume-upload']";
        this.fileInputSelectors = "input[type='file'],input[accept*='pdf']";
        this.processingSelectors = ".artdeco-loader";
        this.submitButtonSelectors = "button:has-text('Submit application'),button.artdeco-button--primary";
        this.confirmationSelectors = "button:has-text('Done'),button:has-text('OK')";
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
        if (this.crawlingSpeed < 1) this.crawlingSpeed = 1;
        if (this.crawlingSpeed > 10) this.crawlingSpeed = 10;

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
        this.searchInputSelector = properties.getProperty("site.search.input.selector", this.searchInputSelector);

        // Job search selectors
        this.jobCardSelectors = properties.getProperty("selectors.job.cards", this.jobCardSelectors);
        this.jobDescriptionSelectors = properties.getProperty("selectors.job.description", this.jobDescriptionSelectors);
        this.easyApplySelectors = properties.getProperty("selectors.easy.apply", this.easyApplySelectors);
        this.easyApplyKeywords = properties.getProperty("selectors.easy.apply.keywords", this.easyApplyKeywords);

        // Application selectors
        this.applyButtonSelectors = properties.getProperty("selectors.apply.button", this.applyButtonSelectors);
        this.updateButtonSelectors = properties.getProperty("selectors.update.button", this.updateButtonSelectors);
        this.cvUploadSelectors = properties.getProperty("selectors.cv.upload", this.cvUploadSelectors);
        this.fileInputSelectors = properties.getProperty("selectors.file.input", this.fileInputSelectors);
        this.processingSelectors = properties.getProperty("selectors.processing", this.processingSelectors);
        this.submitButtonSelectors = properties.getProperty("selectors.submit.button", this.submitButtonSelectors);
        this.confirmationSelectors = properties.getProperty("selectors.confirmation", this.confirmationSelectors);
    }
    
    /**
     * Create a site-specific configuration by name
     */
    public static CrawlerConfig forSite(String siteName) throws IOException {
        String configFile = "configuration/" + siteName.toLowerCase() + "crawler.properties";
        return new CrawlerConfig(configFile, siteName);
    }
}