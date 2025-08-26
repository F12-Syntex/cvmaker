package com.cvmaker.crawler.reed;

import java.nio.file.Path;

import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Handles the full application process on Reed:
 * Apply button -> Update -> Upload CV -> Submit -> Confirm dialog.
 */
public class JobApplicationService {
    private final Page page;
    private final CrawlerConfig config;

    // Full selectors (restored from original ReedCrawler)
    private static final String[] APPLY_BUTTON_SELECTOR = {
        "button:has-text('Apply Now')",
        "a:has-text('Apply Now')",
        "button:has-text('Apply')",
        "a:has-text('Apply')",
        "[data-qa*='apply']",
        "button[class*='apply']",
        "a[class*='apply']",
        ".apply-button",
        ".btn-apply"
    };

    private static final String[] UPDATE_BUTTON_SELECTOR = {
        "button:has-text('Update')",
        "a:has-text('Update')",
        "[data-qa*='update']",
        "button[class*='update']",
        ".update-button"
    };

    private static final String[] CV_UPLOAD_SELECTOR = {
        "button:has-text('Choose your own CV file')",
        "a:has-text('Choose your own CV file')",
        "button:has-text('Choose CV')",
        "button:has-text('Upload CV')",
        "[data-qa*='upload-cv']",
        "[data-qa*='choose-cv']",
        "button[class*='cv-upload']",
        "input[type='file'][accept*='pdf']",
        "label[for*='cv']",
        ".cv-upload-button"
    };

    private static final String[] FILE_INPUT_SELECTOR = {
        "input[type='file']",
        "input[accept*='pdf']",
        "input[name*='cv']",
        "input[id*='cv']",
        "input[class*='cv']"
    };

    private static final String[] PROCESSING_SELECTOR = {
        ":has-text('CV processing')",
        ":has-text('Processing')",
        ":has-text('Uploading')",
        ".spinner",
        ".loading",
        "[class*='processing']"
    };

    private static final String[] SUBMIT_BUTTON_SELECTOR = {
        "button:has-text('Submit Application')",
        "button:has-text('Submit')",
        "a:has-text('Submit Application')",
        "a:has-text('Submit')",
        "[data-qa*='submit']",
        "button[class*='submit']",
        ".submit-button",
        ".btn-submit"
    };

    private static final String[] CONFIRMATION_SELECTOR = {
        "button:has-text('OK')",
        "button:has-text('Ok')",
        "button:has-text('Confirm')",
        "button:has-text('Yes')",
        "[data-qa*='confirm']",
        ".modal button:has-text('OK')",
        ".dialog button:has-text('OK')"
    };

    public JobApplicationService(Page page, CrawlerConfig config) {
        this.page = page;
        this.config = config;
    }

    public boolean applyForJob(Path cvPath) {
        try {
            System.out.println("➡️ Starting job application process...");

            if (!clickAny(APPLY_BUTTON_SELECTOR, "Apply Now")) return false;
            page.waitForTimeout(adjustedDelay(config.getApplicationStepDelay()));

            clickAny(UPDATE_BUTTON_SELECTOR, "Update");
            page.waitForTimeout(adjustedDelay(config.getElementInteractionDelay()));

            if (!clickAny(CV_UPLOAD_SELECTOR, "Choose CV")) return false;
            page.waitForTimeout(adjustedDelay(config.getElementInteractionDelay()));

            if (!uploadFile(cvPath)) return false;
            waitForProcessing();

            if (!clickAny(SUBMIT_BUTTON_SELECTOR, "Submit")) return false;
            handleConfirmationDialog();

            System.out.println("✅ Job application completed!");
            return true;

        } catch (Exception e) {
            System.out.println("⚠️ Error during application: " + e.getMessage());
            return false;
        }
    }

    // -------------------
    // Helpers
    // -------------------

    private boolean clickAny(String[] selectors, String name) {
        for (String selector : selectors) {
            try {
                Locator el = page.locator(selector).first();
                if (el != null && el.isVisible()) {
                    System.out.println("✔️ Clicking " + name + " button (" + selector + ")");
                    el.scrollIntoViewIfNeeded();
                    el.click();
                    return true;
                }
            } catch (Exception ignore) {}
        }
        System.out.println("❌ " + name + " button not found");
        return false;
    }

    private boolean uploadFile(Path cvPath) {
        for (String selector : FILE_INPUT_SELECTOR) {
            try {
                Locator input = page.locator(selector).first();
                if (input != null && input.count() > 0) {
                    System.out.println("📄 Uploading CV: " + cvPath);
                    input.setInputFiles(cvPath);
                    return true;
                }
            } catch (Exception ignore) {}
        }
        return false;
    }

    private void waitForProcessing() {
        try {
            page.waitForTimeout(adjustedDelay(config.getProcessingStartDelay()));
            for (String selector : PROCESSING_SELECTOR) {
                Locator proc = page.locator(selector).first();
                if (proc != null && proc.isVisible()) {
                    proc.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.HIDDEN)
                        .setTimeout(config.getProcessingTimeout()));
                }
            }
            page.waitForTimeout(adjustedDelay(config.getProcessingCompleteDelay()));
            System.out.println("✔️ CV processing finished");
        } catch (Exception e) {
            System.out.println("⚠️ Processing wait error: " + e.getMessage());
        }
    }

    private void handleConfirmationDialog() {
        try {
            page.waitForTimeout(adjustedDelay(config.getConfirmationDialogDelay()));
            for (String selector : CONFIRMATION_SELECTOR) {
                Locator confirm = page.locator(selector).first();
                if (confirm != null && confirm.isVisible()) {
                    confirm.click(new Locator.ClickOptions().setForce(true));
                    page.waitForTimeout(adjustedDelay(config.getElementInteractionDelay()));
                    System.out.println("✔️ Confirmation dialog handled");
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error handling confirmation: " + e.getMessage());
        }
    }

    private int adjustedDelay(int baseDelay) {
        float speedMultiplier = 1.5f - (config.getCrawlingSpeed() * 0.12f);
        return Math.max(100, Math.round(baseDelay * speedMultiplier));
    }
}