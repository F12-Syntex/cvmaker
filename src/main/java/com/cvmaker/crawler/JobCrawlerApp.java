package com.cvmaker.crawler;

import com.cvmaker.crawler.CrawlerFactory.CrawlerType;

/**
 * Main application entry point for job crawlers.
 */
public class JobCrawlerApp {

    public static void main(String[] args) {
        CrawlerManager manager = new CrawlerManager();

        try {
            // Create the crawler through the factory. For custom settings, pass a
            // CrawlerConfig as the second argument of createCrawler.
            JobCrawler formCrawler = CrawlerFactory.createCrawler(CrawlerType.FORM);
            manager.addCrawler(formCrawler);

            // Start a specific crawler (manager.startAllCrawlers() starts all added ones)
            manager.startCrawler(formCrawler);

            // Wait for completion
            manager.waitForCompletion();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            manager.shutdown();
        }
    }
}
