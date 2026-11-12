package com.cvmaker.crawler;

import java.util.Objects;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.form.FormCrawler;
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
     * @throws NullPointerException If the type is null
     * @throws Exception If crawler creation fails
     */
    public static JobCrawler createCrawler(CrawlerType type, CrawlerConfig config) throws Exception {
        Objects.requireNonNull(type, "crawler type");

        // A switch expression over the enum has no default branch: the compiler
        // rejects it when a new CrawlerType has no case here.
        return switch (type) {
            case REED -> new ReedCrawler(config);
            case FORM -> new FormCrawler(config);
            case GENERIC -> new GenericCrawler(config);
        };
    }

    /**
     * Enum defining the types of crawlers available.
     */
    public enum CrawlerType {
        REED,
        GENERIC,
        FORM
        // Add more crawler types here as they are implemented
    }
}
