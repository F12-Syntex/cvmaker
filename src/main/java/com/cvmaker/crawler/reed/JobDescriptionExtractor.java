package com.cvmaker.crawler.reed;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.JobInfo;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Extracts job descriptions from Reed job postings.
 * Handles multiple selector strategies and fallbacks.
 */
public class JobDescriptionExtractor {
    private final Page page;
    private final CrawlerConfig config;

    // Primary Reed selector for job description
    private static final String PRIMARY_SELECTOR = "[data-qa='job-description']";

    // Backup selectors
    private static final String[] COMMON_DESC_SELECTORS = {
        "[class*='job-description']",
        "[class*='jobDescription']",
        "[class*='job-details']",
        "[class*='jobDetails']",
        "[class*='description-container']",
        "[id*='job-description']",
        "[id*='jobDescription']",
        "[data-testid*='description']"
    };

    // A backup selector's text must be longer than this to count as a description
    private static final int MIN_FALLBACK_LENGTH = 50;

    public JobDescriptionExtractor(Page page, CrawlerConfig config) {
        this.page = page;
        this.config = config;
    }

    /**
     * Extract the job description text for a given job.
     */
    public String extract(JobInfo job) {
        StringBuilder description = new StringBuilder();

        try {
            // 1. Try the modern Reed selector. An empty element (still rendering, or a placeholder)
            // falls through to the other selectors instead of returning "null" or a blank line.
            Locator primary = page.locator(PRIMARY_SELECTOR).first();
            if (primary != null && primary.isVisible()) {
                String text = ReedText.usableDescription(primary.textContent(), 0);
                if (text != null) {
                    description.append(text).append("\n");
                    return description.toString();
                }
            }

            // 2. Try common description selectors
            for (String selector : COMMON_DESC_SELECTORS) {
                Locator element = page.locator(selector).first();
                if (element != null && element.isVisible()) {
                    String text = ReedText.usableDescription(element.textContent(), MIN_FALLBACK_LENGTH);
                    if (text != null) {
                        description.append(text).append("\n");
                        return description.toString();
                    }
                }
            }

            // 3. Fallback: entire body
            String bodyText = page.textContent("body");
            if (bodyText != null && !bodyText.isEmpty()) {
                description.append(bodyText);
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error extracting job description: " + e.getMessage());
        }

        return description.toString();
    }
}