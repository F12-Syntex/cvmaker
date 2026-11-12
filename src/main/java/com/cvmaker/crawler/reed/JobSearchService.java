package com.cvmaker.crawler.reed;

import java.util.List;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.CrawlDelays;
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
    private final ResultsPageCursor cursor = new ResultsPageCursor();

    private static final String SEARCH_INPUT_SELECTOR = "input[name='keywords']";

    // Job card selectors in priority order. Only the first one that matches on a results page is
    // used, so overlapping selectors no longer visit the same cards twice. The former
    // "[data-qa*='job']" entry was dropped: it also matched elements inside each card (titles,
    // company names), so it never selected whole cards.
    static final List<String> JOB_CARDS_SELECTORS = List.of(
        ".job-card_jobCard__MkcJD",
        "[class*='job-card_jobCard']",
        ".job-result",
        ".card.job-card",
        ".job-card",
        "article[data-qa='job-result']",
        ".job-result-card"
    );

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
            page.waitForTimeout(delay(config.getPageLoadDelay()));

            Locator input = page.locator(SEARCH_INPUT_SELECTOR).first();
            if (input == null || !input.isVisible()) {
                System.out.println("⚠️ Search input not found");
                return false;
            }

            input.fill(config.getSearchKeywords());
            input.press("Enter");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(delay(config.getSearchResultsDelay()));

            cursor.startResultsPage(page.url());
            if (cursor.resolveSelector(JOB_CARDS_SELECTORS, this::countMatches)) {
                System.out.println("✅ Found " + countMatches(cursor.getSelector()) + " job results");
                return true;
            }

            return false;
        } catch (Exception e) {
            System.out.println("⚠️ Error performing job search: " + e.getMessage());
            return false;
        }
    }

    /**
     * Find the next Easy Apply job on the current results page and click it. If the browser has
     * left the results page (after visiting or applying to a job), it goes back first.
     */
    public JobInfo findNextEasyApplyJob() {
        try {
            if (!returnToResults()) {
                return null;
            }
            if (!cursor.resolveSelector(JOB_CARDS_SELECTORS, this::countMatches)) {
                System.out.println("⚠️ No job cards found on the results page");
                return null;
            }

            Locator jobs = page.locator(cursor.getSelector());
            int index;
            while ((index = cursor.nextCardIndex(jobs.count())) >= 0) {
                crawler.setJobsChecked(crawler.getJobsChecked() + 1);
                int checked = crawler.getJobsChecked();
                Locator jobCard = jobs.nth(index);

                JobInfo job = extractJobInfo(jobCard, checked);

                if (isEasyApply(jobCard)) {
                    crawler.setEasyApplyJobsFound(crawler.getEasyApplyJobsFound() + 1);
                    System.out.printf("(%d) %s - %s - applying%n",
                            checked, job.getTitle(), job.getCompany());
                    jobCard.click();
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    page.waitForTimeout(delay(config.getJobCardLoadDelay()));
                    return job;
                }

                System.out.printf("(%d) %s - %s - skipping%n",
                        checked, job.getTitle(), job.getCompany());
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error finding job: " + e.getMessage());
        }
        return null;
    }

    /**
     * Go back to the results page recorded by {@link #performJobSearch()} if the browser has left
     * it. The card index is kept, so the walk continues with the next unchecked card.
     *
     * @return true if the browser is on the results page (or no results page was recorded)
     */
    public boolean returnToResults() {
        try {
            if (!cursor.needsReturn(page.url())) {
                return true;
            }
            page.navigate(cursor.getResultsUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(delay(config.getSearchResultsDelay()));
            return true;
        } catch (Exception e) {
            System.out.println("⚠️ Could not return to search results: " + e.getMessage());
            return false;
        }
    }

    private boolean isEasyApply(Locator jobCard) {
        // 1. Easy Apply button selectors
        for (String eaSelector : EASY_APPLY_SELECTORS) {
            Locator eaButton = jobCard.locator(eaSelector);
            if (eaButton.count() > 0 && eaButton.first().isVisible()) {
                return true;
            }
        }

        // 2. Keyword fallback
        return ReedText.mentionsEasyApply(jobCard.textContent());
    }

    private int countMatches(String selector) {
        return page.locator(selector).count();
    }

    private int delay(int baseDelayMs) {
        return CrawlDelays.adjustedDelay(baseDelayMs, config.getCrawlingSpeed());
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
