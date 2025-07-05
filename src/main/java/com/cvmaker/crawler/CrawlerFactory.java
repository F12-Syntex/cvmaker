package com.cvmaker.crawler;

import com.cvmaker.configuration.CrawlerConfig;

/**
 * Factory class for creating different types of job crawlers.
 */
public class CrawlerFactory {
    
    /**
     * Create a crawler for a specific job site.
     * 
     * @param type The type of crawler to create
     * @return The created crawler
     * @throws Exception If crawler creation fails
     */
    public static JobCrawler createCrawler(CrawlerType type) throws Exception {
        return createCrawler(type, new CrawlerConfig());
    }
    
    /**
     * Create a crawler for a specific job site with custom configuration.
     * 
     * @param type The type of crawler to create
     * @param config The configuration to use
     * @return The created crawler
     * @throws Exception If crawler creation fails
     */
    public static JobCrawler createCrawler(CrawlerType type, CrawlerConfig config) throws Exception {
        switch (type) {
            case REED:
                return new ReedCrawler(config);
            // Add more crawler types here as they are implemented
            default:
                throw new IllegalArgumentException("Unsupported crawler type: " + type);
        }
    }
    
    /**
     * Enum defining the types of crawlers available.
     */
    public enum CrawlerType {
        REED
        // Add more crawler types here as they are implemented
    }
}