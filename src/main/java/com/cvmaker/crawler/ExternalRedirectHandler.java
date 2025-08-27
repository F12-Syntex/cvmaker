package com.cvmaker.crawler;

import java.net.URI;
import java.util.Scanner;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.generic.FormAnalyzer;
import com.cvmaker.crawler.generic.FormFiller;
import com.microsoft.playwright.Page;

/**
 * Handles detection of external redirects after clicking Apply.
 * Works for Reed, Indeed, LinkedIn, etc.
 */
public class ExternalRedirectHandler {

    private final Page page;
    private final CrawlerConfig config;

    public ExternalRedirectHandler(Page page, CrawlerConfig config) {
        this.page = page;
        this.config = config;
    }

    public void handleRedirect() {
        try {
            page.waitForTimeout(config.getPageLoadDelay());

            String currentUrl = page.url();
            if (currentUrl == null || currentUrl.isEmpty()) {
                System.out.println("⚠️ No URL detected after Apply click.");
                return;
            }

            URI uri = new URI(currentUrl);
            String domain = uri.getHost();

            // If still on job board → maybe intermediate form
            if (domain != null && isJobBoardDomain(domain)) {
                if (detectIntermediateForm()) {
                    System.out.println("📝 Intermediate form detected. Using AI to fill it...");
                    handleForm();
                    page.waitForTimeout(config.getApplicationStepDelay());
                    currentUrl = page.url();
                    uri = new URI(currentUrl);
                    domain = uri.getHost();
                } else {
                    System.out.println("ℹ️ Still on job board (" + domain + "). No external redirect yet.");
                    return;
                }
            }

            // If external site detected
            if (domain != null && !isJobBoardDomain(domain)) {
                System.out.println("🌍 Redirected to external site: " + domain);
                System.out.println("⏸ Paused. Inspect site manually and press ENTER to continue...");
                try (Scanner scanner = new Scanner(System.in)) {
                    scanner.nextLine();
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error handling external redirect: " + e.getMessage());
        }
    }

    private boolean isJobBoardDomain(String domain) {
        if (config.getBaseUrl() == null || config.getBaseUrl().isEmpty()) return false;
        try {
            URI base = new URI(config.getBaseUrl());
            String baseDomain = base.getHost();
            return domain.contains(baseDomain);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean detectIntermediateForm() {
        try {
            return page.locator("form").count() > 0 &&
                   (page.locator("input, textarea, select").count() > 2);
        } catch (Exception e) {
            return false;
        }
    }

    private void handleForm() {
        try {
            FormAnalyzer analyzer = new FormAnalyzer(page, config);
            analyzer.analyzeForm();

            FormFiller filler = new FormFiller(page, config);
            filler.fillForm(analyzer.getFormContext());

        } catch (Exception e) {
            System.out.println("⚠️ Failed to handle form: " + e.getMessage());
        }
    }
}