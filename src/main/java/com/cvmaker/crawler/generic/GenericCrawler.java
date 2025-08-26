package com.cvmaker.crawler.generic;

import java.util.Scanner;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;

/**
 * AI-powered generic form filling crawler.
 * Delegates analysis to {@link FormAnalyzer} and filling to {@link FormFiller}.
 */
public class GenericCrawler extends AbstractJobCrawler {

    private final FormAnalyzer analyzer;
    private final FormFiller filler;

    public GenericCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public GenericCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
        this.analyzer = new FormAnalyzer(page, crawlerConfig);
        this.filler = new FormFiller(page, crawlerConfig);
    }

    @Override
    public String getCrawlerName() {
        return "AI-Powered Generic Form Filler";
    }

    @Override
    public void processJobsAndApply() {
        try {
            System.out.println("\n🤖 AI-Powered Form Filler Ready!");
            System.out.println("Navigate to the form you want to fill and press Enter...");

            try (Scanner scanner = new Scanner(System.in)) {
                scanner.nextLine();
            }

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