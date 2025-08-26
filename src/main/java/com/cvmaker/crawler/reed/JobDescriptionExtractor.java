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

    // Common selectors for Reed job descriptions
    private static final String SPECIFIC_SELECTOR =
        ".job-details-drawer-modal_jobSection__42ckh.job-details-drawer-modal_jobDescription__r4Xn1";

    private static final String[] JOB_DESCRIPTION_SELECTORS = {
        "article.card.job-card_jobCard__MkcJD",
        "article[class*='job-card_jobCard']",
        "[class*='job-card_jobCard__MkcJD']",
        "article.card"
    };

    private static final String[] COMMON_DESC_SELECTORS = {
        "[class*='job-description']",
        "[class*='jobDescription']",
        "[class*='job-details']",
        "[class*='jobDetails']",
        "[class*='description-container']",
        "[id*='job-description']",
        "[id*='jobDescription']",
        "[data-testid*='description']",
        "[data-qa*='description']"
    };

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
            // 1. Try the specific Reed selector
            Locator specific = page.locator(SPECIFIC_SELECTOR).first();
            if (specific != null && specific.isVisible()) {
                description.append(specific.textContent()).append("\n");
                return description.toString();
            }

            // 2. Try known job card selectors
            for (String selector : JOB_DESCRIPTION_SELECTORS) {
                Locator card = page.locator(selector).first();
                if (card != null && card.isVisible()) {
                    description.append(card.textContent()).append("\n");
                    return description.toString();
                }
            }

            // 3. Try common description selectors
            for (String selector : COMMON_DESC_SELECTORS) {
                Locator element = page.locator(selector).first();
                if (element != null && element.isVisible()) {
                    String text = element.textContent();
                    if (text != null && text.length() > 100) {
                        description.append(text).append("\n");
                        return description.toString();
                    }
                }
            }

            // 4. Fallback: entire body
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