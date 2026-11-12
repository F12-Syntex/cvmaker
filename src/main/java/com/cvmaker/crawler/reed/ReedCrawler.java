package com.cvmaker.crawler.reed;

import java.nio.file.Files;
import java.nio.file.Path;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.CrawlDelays;
import com.cvmaker.crawler.JobInfo;

public class ReedCrawler extends AbstractJobCrawler {

    private JobSearchService searchService;
    private JobDescriptionExtractor descriptionExtractor;
    private JobApplicationService applicationService;

    public ReedCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public ReedCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
    }

    @Override
    public void setupBrowser() {
        super.setupBrowser();

        // ✅ initialize services AFTER page is created
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
            System.out.println("🚀 Starting Reed job search...");

            if (!searchService.performJobSearch()) {
                System.out.println("❌ Could not perform job search.");
                return;
            }

            while (applicationsSubmitted < crawlerConfig.getMaxApplications()) {
                JobInfo job = searchService.findNextEasyApplyJob();

                if (job == null) {
                    System.out.println("✅ No more jobs or application limit reached.");
                    break;
                }

                String description = descriptionExtractor.extract(job);
                Path cv = generateCVForJob(job, description);

                if (cv != null && Files.exists(cv)) {
                    boolean applied = applicationService.applyForJob(cv);
                    if (applied) {
                        applicationsSubmitted++;
                        System.out.printf("🎉 Applied to %d/%d jobs\n",
                                applicationsSubmitted, crawlerConfig.getMaxApplications());
                    }
                }

                // Leave the job or application page before pausing, so the next search step
                // starts from the results list.
                searchService.returnToResults();
                page.waitForTimeout(CrawlDelays.adjustedDelay(
                        crawlerConfig.getApplicationDelay(), crawlerConfig.getCrawlingSpeed()));
            }

            printSessionSummary();

        } catch (Exception e) {
            System.out.println("⚠️ Error in ReedCrawler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}