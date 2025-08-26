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

    // Broad job card selectors
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

    // Easy Apply button selectors
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
    private static final String[] EASY_APPLY_KEYWORDS = {"easy apply", "quick apply"};

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

            // Small delay
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
                    crawler.setJobsChecked(crawler.getJobsChecked() + 1);
                    Locator jobCard = jobs.nth(i);

                    JobInfo job = extractJobInfo(jobCard, crawler.getJobsChecked());

                    // 1. Easy Apply button selectors
                    for (String eaSelector : EASY_APPLY_SELECTORS) {
                        Locator eaButton = jobCard.locator(eaSelector.trim());
                        if (eaButton.count() > 0 && eaButton.first().isVisible()) {
                            crawler.setEasyApplyJobsFound(crawler.getEasyApplyJobsFound() + 1);
                            System.out.printf("(%d) %s - %s - applying\n",
                                    crawler.getJobsChecked(),
                                    job.getTitle(),
                                    job.getCompany());
                            jobCard.click();
                            return job;
                        }
                    }

                    // 2. Keyword fallback
                    String text = jobCard.textContent();
                    if (text != null) {
                        String lower = text.toLowerCase();
                        for (String keyword : EASY_APPLY_KEYWORDS) {
                            if (lower.contains(keyword)) {
                                crawler.setEasyApplyJobsFound(crawler.getEasyApplyJobsFound() + 1);
                                System.out.printf("(%d) %s - %s - applying\n",
                                        crawler.getJobsChecked(),
                                        job.getTitle(),
                                        job.getCompany());
                                jobCard.click();
                                return job;
                            }
                        }
                    }

                    // If not Easy Apply → skip
                    System.out.printf("(%d) %s - %s - skipping\n",
                            crawler.getJobsChecked(),
                            job.getTitle(),
                            job.getCompany());
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error finding job: " + e.getMessage());
        }
        return null;
    }

    private JobInfo extractJobInfo(Locator jobCard, int index) {
        JobInfo job = new JobInfo();

        try {
            // Correct selector for title
            Locator titleEl = jobCard.locator("a[data-qa='job-card-title']").first();
            if (titleEl != null && titleEl.count() > 0) {
                job.setTitle(titleEl.textContent().trim());
            } else {
                job.setTitle("Job " + index);
            }

            // Company selector (likely still works, but adding fallback)
            Locator companyEl = jobCard.locator("[data-qa='job-company-name']").first();
            if (companyEl != null && companyEl.count() > 0) {
                job.setCompany(companyEl.textContent().trim());
            } else {
                job.setCompany("Unknown Company");
            }

        } catch (Exception e) {
            job.setTitle("Job " + index);
            job.setCompany("Unknown Company");
        }

        return job;
    }
}
