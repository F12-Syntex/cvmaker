package com.cvmaker.crawler;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Utility class for visualizing browser automation actions in the UI.
 * Can be used by any crawler implementation to provide visual feedback.
 *
 * <p>A disabled visualizer does nothing and never touches the page, so callers
 * do not need to check whether visualization is on. Visualization is cosmetic:
 * no method here throws when the page is closed, navigating or otherwise
 * unable to run the script.
 */
public class PageVisualizer {

    /**
     * Defines {@code window.highlightElement(selector)} and
     * {@code window.displayAction(text)} in the page.
     *
     * <p>Playwright runs an init script as plain script source. It must be
     * statements (here an immediately invoked function), not a bare
     * {@code () => {...}} expression, which would be evaluated and never called.
     */
    static final String INIT_SCRIPT
            = "(() => {"
            + "   window.highlightElement = function(selector) {"
            + "       const elements = document.querySelectorAll(selector);"
            + "       elements.forEach(el => {"
            + "           const originalBorder = el.style.border;"
            + "           const originalBg = el.style.backgroundColor;"
            + "           el.style.border = '3px solid #FF5733';"
            + "           el.style.backgroundColor = 'rgba(255, 87, 51, 0.2)';"
            + "           el.scrollIntoView({ behavior: 'smooth', block: 'center' });"
            + "           setTimeout(() => {"
            + "               el.style.border = originalBorder;"
            + "               el.style.backgroundColor = originalBg;"
            + "           }, 1500);"
            + "       });"
            + "       return elements.length;"
            + "   };"
            + "   window.displayAction = function(action) {"
            + "       if (!document.body) { return; }"
            + "       const overlay = document.createElement('div');"
            + "       overlay.style.position = 'fixed';"
            + "       overlay.style.top = '20px';"
            + "       overlay.style.right = '20px';"
            + "       overlay.style.padding = '10px';"
            + "       overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.8)';"
            + "       overlay.style.color = 'white';"
            + "       overlay.style.borderRadius = '5px';"
            + "       overlay.style.zIndex = '9999';"
            + "       overlay.style.transition = 'opacity 0.5s';"
            + "       overlay.style.fontSize = '14px';"
            + "       overlay.style.fontFamily = 'Arial, sans-serif';"
            + "       overlay.innerText = '➤ ' + action;"
            + "       document.body.appendChild(overlay);"
            + "       setTimeout(() => {"
            + "           overlay.style.opacity = '0';"
            + "           setTimeout(() => overlay.remove(), 500);"
            + "       }, 2000);"
            + "   };"
            + "})();";

    private final Page page;
    private final boolean enabled;

    /**
     * Creates a new PageVisualizer.
     *
     * @param page The Playwright page to visualize actions on; may be null when
     *        {@code enabled} is false
     * @param enabled Whether visualization is enabled
     */
    public PageVisualizer(Page page, boolean enabled) {
        this.page = page;
        // Without a page there is nothing to draw on.
        this.enabled = enabled && page != null;

        if (this.enabled) {
            initializeVisualization();
        }
    }

    /**
     * A visualizer that does nothing. Use it where there is no page (yet).
     */
    public static PageVisualizer disabled() {
        return new PageVisualizer(null, false);
    }

    /**
     * Whether this visualizer draws anything.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Initialize the visualization scripts on the page.
     */
    private void initializeVisualization() {
        try {
            // For every document the page loads from now on...
            page.addInitScript(INIT_SCRIPT);
            // ...and for the document that is already open.
            page.evaluate(INIT_SCRIPT);

            System.out.println("Page visualization initialized");
        } catch (Exception e) {
            System.out.println("Failed to initialize visualization: " + e.getMessage());
        }
    }

    /**
     * Display an action message on the page.
     *
     * @param action The action message to display
     */
    public void visualizeAction(String action) {
        if (!enabled) return;

        try {
            page.evaluate("action => window.displayAction && window.displayAction(action)", action);
        } catch (Exception e) {
            // Silently fail if visualization fails
        }
    }

    /**
     * Highlight elements matching a selector.
     *
     * @param selector The CSS selector to highlight
     */
    public void highlightElements(String selector) {
        if (!enabled) return;

        try {
            Object count = page.evaluate(
                    "selector => window.highlightElement ? window.highlightElement(selector) : 0", selector);
            System.out.println("Highlighted " + count + " elements with selector: " + selector);
        } catch (Exception e) {
            // Silently fail if highlighting fails
        }
    }

    /**
     * Highlight a specific element.
     *
     * @param element The Playwright locator to highlight
     */
    public void highlightElement(Locator element) {
        if (!enabled || element == null) return;

        try {
            element.evaluate("el => {"
                + "const originalBorder = el.style.border;"
                + "const originalBg = el.style.backgroundColor;"
                + "el.style.border = '3px solid #FF5733';"
                + "el.style.backgroundColor = 'rgba(255, 87, 51, 0.2)';"
                + "el.scrollIntoView({ behavior: 'smooth', block: 'center' });"
                + "setTimeout(() => {"
                + "    el.style.border = originalBorder;"
                + "    el.style.backgroundColor = originalBg;"
                + "}, 1500);"
                + "}"
            );
        } catch (Exception e) {
            // Silently fail if highlighting fails
        }
    }
}
