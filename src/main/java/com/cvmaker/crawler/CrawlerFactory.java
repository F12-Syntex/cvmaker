package com.cvmaker.crawler;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.generic.GenericCrawler;
import com.cvmaker.crawler.reed.ReedCrawler;

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
            case REED -> {
                return new ReedCrawler(config);
            }
            case GENERIC -> {
                return new GenericCrawler(config);
            }
            default ->
                throw new IllegalArgumentException("Unsupported crawler type: " + type);
        }
        // Add more crawler types here as they are implemented
    }

    /**
     * Enum defining the types of crawlers available.
     */
    public enum CrawlerType {
        REED,
        GENERIC
        // Add more crawler types here as they are implemented
    }
}
