package com.cvmaker.crawler.reed;

import java.nio.file.Files;
import java.nio.file.Path;

import com.cvmaker.configuration.ConfigManager;
import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.ExternalRedirectHandler;
import com.cvmaker.crawler.JobInfo;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;

public class ReedCrawler extends AbstractJobCrawler {

    private JobSearchService searchService;
    private JobDescriptionExtractor descriptionExtractor;
    private JobApplicationService applicationService;
    private ConfigManager configManager;
    private AiService aiService;

    public ReedCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public ReedCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
        this.configManager = new ConfigManager();
        this.aiService = new AiService(LLMModel.GPT_4_1_MINI);
    }

    @Override
    public void setupBrowser() {
        super.setupBrowser();
        this.searchService = new JobSearchService(page, crawlerConfig, this);
        this.descriptionExtractor = new JobDescriptionExtractor(page, crawlerConfig);
        this.applicationService = new JobApplicationService(page, crawlerConfig);
    }

    @Override
    public String getCrawlerName() {
        return "Reed";
    }

    @Override
    public void processJobsAndApply() {
        try {
            System.out.println("🟢 Starting Reed job search...");

            if (!searchService.performJobSearch()) {
                System.out.println("⚠️ Could not perform job search.");
                return;
            }

            while (applicationsSubmitted < crawlerConfig.getMaxApplications()) {
                JobInfo job = searchService.findNextJobToApply();

                if (job == null) {
                    System.out.println("✅ No more jobs or application limit reached.");
                    break;
                }

                // Extract description for CV later
                String description = descriptionExtractor.extract(job);

                boolean progressing = false;

                // 🔑 Decide which modal handler to use
                if (crawlerConfig.isAiModalEnabled()) {
                    AiModalHandler aiModalHandler = new AiModalHandler(page, crawlerConfig, aiService);
                    progressing = aiModalHandler.handleModal();
                } else {
                    ReedModalHandler modalHandler = new ReedModalHandler(page, crawlerConfig);
                    progressing = modalHandler.handleModal();
                }

                if (!progressing) {
                    System.out.printf("(%d) %s - %s → SKIPPING (not progressing)\n",
                            jobsChecked, job.getTitle(), job.getCompany());
                    continue;
                }

                // ✅ At this point the modal flow was completed (AI or non-AI)
                Path cv = generateCVForJob(job, description);

                if (cv != null && Files.exists(cv)) {
                    if (page.locator(":text-matches('You applied', 'i')").count() > 0
                            || page.locator(":text-matches('Already applied', 'i')").count() > 0) {
                        System.out.printf("(%d) %s - %s → SKIPPING (already applied)\n",
                                jobsChecked, job.getTitle(), job.getCompany());
                        continue;
                    }

                    boolean applied = applicationService.applyForJob(cv);
                    if (applied) {
                        applicationsSubmitted++;
                        System.out.printf("🚀 Applied to %d/%d jobs\n",
                                applicationsSubmitted, crawlerConfig.getMaxApplications());

                        // Handle external redirect if any
                        ExternalRedirectHandler redirectHandler = new ExternalRedirectHandler(page, crawlerConfig);
                        redirectHandler.handleRedirect();
                    }
                }

                // Delay before next job
                page.waitForTimeout(adjustedDelay(crawlerConfig.getApplicationDelay()));
            }

            printSessionSummary();

        } catch (Exception e) {
            System.out.println("⚠️ Error in ReedCrawler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}