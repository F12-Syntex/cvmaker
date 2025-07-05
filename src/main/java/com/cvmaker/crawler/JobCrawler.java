package com.cvmaker.crawler;

/**
 * Base interface for all job site crawlers.
 */
public interface JobCrawler {

    /**
     * Initialize the crawler and set up resources.
     */
    void initialize() throws Exception;

    /**
     * Set up the browser instance for the crawler.
     */
    void setupBrowser();

    /**
     * Open the site and handle login if needed.
     */
    void openForLogin();

    /**
     * Process jobs and apply to them.
     */
    void processJobsAndApply();

    /**
     * Close the crawler and clean up resources.
     */
    void close();

    /**
     * Set maximum number of applications to submit.
     */
    void setMaxApplications(int maxApplications);

    /**
     * Get the name of this crawler.
     */
    String getCrawlerName();
}
