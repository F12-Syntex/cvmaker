package com.cvmaker.crawler.generic;

import java.io.IOException;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.ConsolePrompt;
import com.microsoft.playwright.Page;

/**
 * AI-powered generic form filling crawler.
 * Delegates analysis to {@link FormAnalyzer} and filling to {@link FormFiller}.
 */
public class GenericCrawler extends AbstractJobCrawler {

    // Created in setupBrowser(): both need the Page, which does not exist before that.
    private FormAnalyzer analyzer;
    private FormFiller filler;

    public GenericCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public GenericCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
    }

    @Override
    public void setupBrowser() {
        super.setupBrowser();

        if (page == null) {
            throw new IllegalStateException("Browser setup did not produce a page for " + getCrawlerName());
        }

        // Initialize the form services AFTER the page is created
        this.analyzer = createAnalyzer(page);
        this.filler = createFiller(page);
    }

    /**
     * Build the form analyzer for the given page. Overridable for tests.
     */
    protected FormAnalyzer createAnalyzer(Page page) {
        return new FormAnalyzer(page, crawlerConfig);
    }

    /**
     * Build the form filler for the given page. Overridable for tests.
     */
    protected FormFiller createFiller(Page page) {
        return new FormFiller(page, crawlerConfig);
    }

    /**
     * Block until the user presses Enter. Leaves {@code System.in} open, because
     * {@link FormFiller} reads from it again before submitting.
     */
    protected void waitForEnter() throws IOException {
        ConsolePrompt.waitForEnter(System.in);
    }

    @Override
    public String getCrawlerName() {
        return "AI-Powered Generic Form Filler";
    }

    @Override
    public void processJobsAndApply() {
        if (analyzer == null || filler == null) {
            System.out.println("⚠️ Browser is not set up; call setupBrowser() before processJobsAndApply().");
            return;
        }

        try {
            System.out.println("\n🤖 AI-Powered Form Filler Ready!");
            System.out.println("Navigate to the form you want to fill and press Enter...");

            waitForEnter();

            // Step 1: Analyze form with AI
            analyzer.analyzeForm();

            // Step 2: Fill form with AI-suggested values
            filler.fillForm(analyzer.getFormContext());

        } catch (Exception e) {
            System.out.println("⚠️ Error during form processing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
