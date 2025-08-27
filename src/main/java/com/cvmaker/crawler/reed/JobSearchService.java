package com.cvmaker.crawler.reed;

import java.io.IOException;

import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.JobInfo;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Handles searching and iterating through jobs on Reed.
 * Delegates modal handling to ReedModalHandler (no AI) or AiModalHandler (AI feedback loop).
 */
public class JobSearchService {

    private final Page page;
    private final CrawlerConfig config;
    private final AbstractJobCrawler crawler;
    private ConfigManager configManager;

    // Job search input
    private static final String SEARCH_INPUT_SELECTOR = "input[name='keywords']";

    // Job card selectors
    private static final String[] JOB_CARDS_SELECTORS = {
        ".job-card_jobCard__MkcJD",
        "[class*='job-card_jobCard']",
        ".job-result",
        "article[data-qa='job-card']",
        ".job-result-card"
    };

    // Quick Apply selectors
    private static final String[] QUICK_APPLY_SELECTORS = {
        "button:has-text('Easy Apply')",
        "a:has-text('Easy Apply')",
        "button:has-text('Quick Apply')",
        "a:has-text('Quick Apply')",
        "[data-qa*='easy-apply']",
        "[data-qa*='quick-apply']"
    };

    public JobSearchService(Page page, CrawlerConfig config, AbstractJobCrawler crawler) {
        this.page = page;
        this.config = config;
        this.crawler = crawler;
        try {
            this.configManager = new ConfigManager();
        } catch (IOException e) {
            System.err.println("⚠️ Could not load ConfigManager: " + e.getMessage());
        }
    }

    /** Perform initial search with configured keywords. */
    public boolean performJobSearch() {
        try {
            page.navigate(config.getBaseUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(config.getCrawlingSpeed());

            Locator input = page.locator(SEARCH_INPUT_SELECTOR).first();
            if (input == null || !input.isVisible()) {
                System.out.println("⚠️ Search input not found on page.");
                return false;
            }
            input.fill(config.getSearchKeywords());
            input.press("Enter");

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(config.getCrawlingSpeed());

            for (String selector : JOB_CARDS_SELECTORS) {
                if (page.locator(selector).count() > 0) {
                    System.out.println("✅ Found " + page.locator(selector).count() + " job results");
                    return true;
                }
            }
            System.out.println("⚠️ No jobs found.");
            return false;
        } catch (Exception e) {
            System.out.println("⚠️ Error in performJobSearch: " + e.getMessage());
            return false;
        }
    }

    /** Find the next valid job to apply for. */
    public JobInfo findNextJobToApply() {
        try {
            for (String selector : JOB_CARDS_SELECTORS) {
                Locator jobs = page.locator(selector);
                int total = jobs.count();

                for (int i = crawler.getJobsChecked(); i < total; i++) {
                    crawler.setJobsChecked(crawler.getJobsChecked() + 1);
                    Locator jobCard = jobs.nth(i);
                    JobInfo job = extractJobInfo(jobCard, crawler.getJobsChecked());

                    // Skip conditions
                    if (isAlreadyApplied(jobCard)) {
                        logSkip(job, "Already applied");
                        continue;
                    }

                    // Case 1: Apply button exists directly
                    if (hasApplyButton(jobCard)) {
                        System.out.printf("(%d) %s - %s - applying (direct Apply)\n",
                                crawler.getJobsChecked(), job.getTitle(), job.getCompany());
                        jobCard.locator("button:has-text('Apply'), a:has-text('Apply')").first().click();

                        JobInfo appliedJob = handleApplyFlow(job);
                        if (appliedJob != null) {
                            return appliedJob; // ✅ stop only if we actually applied
                        }
                        continue;
                    }

                    // Case 2: No button → click card
                    System.out.printf("(%d) %s - %s - opening details\n",
                            crawler.getJobsChecked(), job.getTitle(), job.getCompany());
                    jobCard.click();
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    page.waitForTimeout(config.getPageLoadDelay());

                    JobInfo appliedJob = handleApplyFlow(job);
                    if (appliedJob != null) {
                        return appliedJob;
                    }
                    // ❌ else continue loop
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error in findNextJobToApply: " + e.getMessage());
        }
        return null;
    }

    /** Backward-compatible alias. */
    public JobInfo findNextEasyApplyJob() {
        return findNextJobToApply();
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private JobInfo handleApplyFlow(JobInfo job) {
        boolean applied = false;

        // 🔑 Decide which modal handler to use
        if (config.isAiModalEnabled()) {
            // AI-driven feedback loop
            AiService aiService = new AiService(LLMModel.GPT_4_1_MINI);
            AiModalHandler aiModalHandler = new AiModalHandler(page, config, aiService);
            applied = aiModalHandler.handleModal();
        } else {
            // Rule-based, Quick Apply only
            ReedModalHandler modalHandler = new ReedModalHandler(page, config);
            applied = modalHandler.handleModal();
        }

        if (!applied) {
            System.out.printf("(%d) %s - %s - SKIPPING (no valid apply path)\n",
                    crawler.getJobsChecked(), job.getTitle(), job.getCompany());
            return null;
        }

        return job;
    }

    private boolean isAlreadyApplied(Locator jobCard) {
        try {
            String text = jobCard.textContent();
            return text != null && text.toLowerCase().contains("applied");
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isQuickApply(Locator jobCard) {
        try {
            for (String selector : QUICK_APPLY_SELECTORS) {
                if (jobCard.locator(selector).count() > 0) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean hasApplyButton(Locator jobCard) {
        return jobCard.locator("button:has-text('Apply'), a:has-text('Apply')").count() > 0;
    }

    private JobInfo extractJobInfo(Locator jobCard, int index) {
        JobInfo job = new JobInfo();
        try {
            Locator titleEl = jobCard.locator("a[data-qa='job-card-title']").first();
            job.setTitle(titleEl.count() > 0 ? titleEl.textContent().trim() : "Job " + index);

            Locator companyEl = jobCard.locator("[data-qa='job-company-name']").first();
            job.setCompany(companyEl.count() > 0 ? companyEl.textContent().trim() : "Unknown Company");
        } catch (Exception e) {
            job.setTitle("Job " + index);
            job.setCompany("Unknown Company");
        }
        return job;
    }

    private void logSkip(JobInfo job, String reason) {
        System.out.printf("(%d) %s - %s - SKIPPING (%s)\n",
                crawler.getJobsChecked(), job.getTitle(), job.getCompany(), reason);
    }
}