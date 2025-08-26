package com.cvmaker.crawler.reed;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.JobInfo;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Handles searching for jobs and iterating through results on Reed.
 */
public class JobSearchService {
    private final Page page;
    private final CrawlerConfig config;
    private final AbstractJobCrawler crawler;

    private static final String SEARCH_INPUT_SELECTOR = "input[name='keywords']";

    // Broad job card selectors (from original ReedCrawler)
    private static final String[] JOB_CARDS_SELECTORS = {
        ".job-card_jobCard__MkcJD",
        "[class*='job-card_jobCard']",
        ".job-result",
        ".card.job-card",
        ".job-card",
        "article[data-qa='job-result']",
        "[data-qa*='job']",
        ".job-result-card"
    };

    // Full Easy Apply button selectors (from original ReedCrawler)
    private static final String[] EASY_APPLY_SELECTORS = {
        "button:has-text('Easy Apply')",
        "a:has-text('Easy Apply')",
        "[data-qa*='easy-apply']",
        "[class*='easy-apply']",
        "button:has-text('Quick Apply')",
        "a:has-text('Quick Apply')",
        "[data-qa*='quick-apply']",
        "[class*='quick-apply']",
        ".easy-apply",
        ".quick-apply",
        "button[class*='Easy']",
        "a[class*='Easy']"
    };

    // Keyword fallback
    private static final String[] EASY_APPLY_KEYWORDS = { "easy apply", "quick apply" };

    public JobSearchService(Page page, CrawlerConfig config, AbstractJobCrawler crawler) {
        this.page = page;
        this.config = config;
        this.crawler = crawler;
    }

    /**
     * Perform an initial job search using the configured keywords.
     */
    public boolean performJobSearch() {
        try {
            page.navigate(config.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Small delay like original
            page.waitForTimeout(config.getCrawlingSpeed());

            Locator input = page.locator(SEARCH_INPUT_SELECTOR).first();
            if (input == null || !input.isVisible()) {
                System.out.println("⚠️ Search input not found");
                return false;
            }

            input.fill(config.getSearchKeywords());
            input.press("Enter");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(config.getCrawlingSpeed());

            for (String selector : JOB_CARDS_SELECTORS) {
                Locator jobs = page.locator(selector.trim());
                if (jobs.count() > 0) {
                    System.out.println("✅ Found " + jobs.count() + " job results");
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            System.out.println("⚠️ Error performing job search: " + e.getMessage());
            return false;
        }
    }

    /**
     * Find the next Easy Apply job and click it.
     */
    public JobInfo findNextEasyApplyJob() {
        try {
            for (String selector : JOB_CARDS_SELECTORS) {
                Locator jobs = page.locator(selector.trim());
                int count = jobs.count();

                for (int i = crawler.getJobsChecked(); i < count; i++) {
                    crawler.setJobsChecked(crawler.getJobsChecked() + 1); // increment jobs checked
                    Locator jobCard = jobs.nth(i);

                    // 1. Try Easy Apply selectors
                    for (String eaSelector : EASY_APPLY_SELECTORS) {
                        Locator eaButton = jobCard.locator(eaSelector.trim());
                        if (eaButton.count() > 0 && eaButton.first().isVisible()) {
                            crawler.setEasyApplyJobsFound(crawler.getEasyApplyJobsFound() + 1);
                            JobInfo job = extractJobInfo(jobCard, i);
                            jobCard.click();
                            return job;
                        }
                    }

                    // 2. Fallback: keyword detection
                    String text = jobCard.textContent();
                    if (text != null) {
                        String lower = text.toLowerCase();
                        for (String keyword : EASY_APPLY_KEYWORDS) {
                            if (lower.contains(keyword)) {
                                crawler.setEasyApplyJobsFound(crawler.getEasyApplyJobsFound() + 1);
                                JobInfo job = extractJobInfo(jobCard, i);
                                jobCard.click();
                                return job;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error finding Easy Apply job: " + e.getMessage());
        }
        return null;
    }

    private JobInfo extractJobInfo(Locator jobCard, int index) {
        JobInfo job = new JobInfo();
        try {
            String text = jobCard.textContent();
            if (text != null && !text.isEmpty()) {
                String[] lines = text.split("\n");
                job.setTitle(lines[0].trim());
                if (lines.length > 1) {
                    job.setCompany(lines[1].trim());
                }
            } else {
                job.setTitle("Job " + index);
                job.setCompany("Company");
            }
        } catch (Exception e) {
            job.setTitle("Job " + index);
            job.setCompany("Company");
        }
        return job;
    }
}