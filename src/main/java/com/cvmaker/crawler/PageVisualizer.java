package com.cvmaker.crawler;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Utility class for visualizing browser automation actions in the UI.
 * Can be used by any crawler implementation to provide visual feedback.
 */
public class PageVisualizer {
    
    private final Page page;
    private final boolean enabled;
    
    /**
     * Creates a new PageVisualizer.
     * 
     * @param page The Playwright page to visualize actions on
     * @param enabled Whether visualization is enabled
     */
    public PageVisualizer(Page page, boolean enabled) {
        this.page = page;
        this.enabled = enabled;
        
        if (enabled) {
            initializeVisualization();
        }
    }
    
    /**
     * Initialize the visualization scripts on the page.
     */
    private void initializeVisualization() {
        try {
            page.addInitScript(
                "() => {"
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
                + "           setTimeout(() => document.body.removeChild(overlay), 500);"
                + "       }, 2000);"
                + "   };"
                + "}"
            );
            
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
            page.evaluate("action => window.displayAction(action)", action);
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
            Object count = page.evaluate("selector => window.highlightElement(selector)", selector);
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
            );
        } catch (Exception e) {
            // Silently fail if highlighting fails
        }
    }
}