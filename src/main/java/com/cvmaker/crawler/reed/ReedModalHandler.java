package com.cvmaker.crawler.reed;

import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Rule-based modal handler for Reed.
 * ⚠ AI-free: ONLY handles Quick/Easy Apply modals.
 * Skips all other modal types.
 */
public class ReedModalHandler {

    private final Page page;
    private final CrawlerConfig crawlerConfig;

    public ReedModalHandler(Page page, CrawlerConfig crawlerConfig) {
        this.page = page;
        this.crawlerConfig = crawlerConfig;
    }

    /**
     * Handle modal if it's a Quick/Easy Apply.
     *
     * @return true if we applied (quick apply), false otherwise (skip).
     */
    public boolean handleModal() {
        try {
            Locator modal = getModal();
            if (modal == null) {
                System.out.println("⚠ No modal detected.");
                return false;
            }

            System.out.println("🟢 Modal detected (rule-based handler).");

            // Already applied → skip
            if (modal.locator(":text-matches('You applied', 'i')").count() > 0
                    || modal.locator("label:has-text('Applied')").count() > 0) {
                System.out.println("⚠ Already applied — skipping job.");
                closeModal(modal);
                return false;
            }

            // Quick/Easy Apply (the only case we support here)
            Locator submitBtn = modal.locator("button[data-qa='submit-application-btn']");
            if (submitBtn.count() > 0 && submitBtn.first().isVisible()) {
                System.out.println("🚀 Submitting Quick/Easy Apply...");
                submitBtn.first().click();
                page.waitForTimeout(crawlerConfig.getElementInteractionDelay());
                closeModal(modal);
                return true;
            }

            // Anything else (screening Qs, CV upload, external apply) → skip
            System.out.println("⚠ Non-quick modal detected (screening/CV upload/external). Skipping.");
            closeModal(modal);
            return false;

        } catch (Exception e) {
            System.out.println("⚠ Error handling modal: " + e.getMessage());
            return false;
        }
    }

    private Locator getModal() {
        Locator modal = page.locator("div[data-qa='apply-job-modal'], div[data-qa='job-details-drawer-modal']");
        return modal.count() > 0 ? modal.first() : null;
    }

    private void closeModal(Locator modal) {
        try {
            Locator closeBtn = modal.locator("button[aria-label='Close'], header button").first();
            if (closeBtn.count() > 0) {
                closeBtn.click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(500);
                return;
            }
            page.keyboard().press("Escape");
        } catch (Exception e) {
            System.out.println("⚠ Failed to close modal: " + e.getMessage());
        }
    }
}