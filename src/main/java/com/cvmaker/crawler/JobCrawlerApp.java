package com.cvmaker.crawler;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.CrawlerFactory.CrawlerType;

/**
 * Main application entry point for job crawlers.
 */
public class JobCrawlerApp {

    public static void main(String[] args) {
        CrawlerManager manager = new CrawlerManager();

        try {
            // Example 1: Using factory pattern
            JobCrawler formCrawler = CrawlerFactory.createCrawler(CrawlerType.FORM);
            manager.addCrawler(formCrawler);

            // Example 2: Custom configuration
            CrawlerConfig customConfig = new CrawlerConfig();
            customConfig.setMaxApplications(5);
            customConfig.setDebugMode(true);

            // Start a specific crawler
            manager.startCrawler(formCrawler);

            // Or start all added crawlers
            // manager.startAllCrawlers();
            // Wait for completion
            manager.waitForCompletion();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            manager.shutdown();
        }
    }
}
