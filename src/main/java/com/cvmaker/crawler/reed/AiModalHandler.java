package com.cvmaker.crawler.reed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.service.ai.AiService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Full-control AI-powered modal handler.
 * Uses PageCacheManager-style extraction to build a full simplified
 * snapshot of ANY modal (without saving to file) and passes it to AI.
 */
public class AiModalHandler {

    private final Page page;
    private final CrawlerConfig config;
    private final AiService aiService;

    public AiModalHandler(Page page, CrawlerConfig config, AiService aiService) {
        this.page = page;
        this.config = config;
        this.aiService = aiService;
    }

    public boolean handleModal() {
        try {
            int loopCount = 0;
            System.out.println("🤖 Starting full AI modal handling...");

            while (loopCount < 15) {
                loopCount++;

                Locator modal = getAnyModal();
                if (modal == null || modal.count() == 0) {
                    System.out.println("⚠️ No modal detected, exiting loop.");
                    break;
                }

                // 1. Use PageCacheManager-style simplification of THIS modal only
                String structure = buildSimplifiedStructure(modal);

                // 2. Ask AI what to do
                String prompt = """
                    You are controlling a job application modal step-by-step.

                    Modal structure (simplified JSON):
                    %s

                    Allowed actions (JSON only):
                    - {"action":"FILL_TEXT","field":"<selector/name/id>","value":"<text>"}
                    - {"action":"SELECT_OPTION","field":"<selector/name/id>","value":"<option>"}
                    - {"action":"CLICK_BUTTON","value":"<button text>"}
                    - {"action":"SCROLL","selector":"<css selector>"}
                    - {"action":"FINISH"}

                    Rules:
                    - Always propose at least ONE action if inputs or buttons exist.
                    - Do NOT return [] unless there are truly no actionable elements.
                    - Use FINISH only if modal is clearly complete or has no actionable elements left.
                    - Respond ONLY with a JSON array of actions.
                    """.formatted(structure);

                String aiResponse = aiService.query(prompt);
                System.out.println("🤖 AI Plan: " + aiResponse);

                // 3. Execute AI's plan
                boolean finished = executeAIPlan(aiResponse, modal);

                // 4. Pause for DOM changes
                page.waitForTimeout(config.getElementInteractionDelay());

                if (finished) {
                    System.out.println("🤖 AI requested FINISH or modal empty. Ending modal loop.");
                    break;
                }
            }

            return true;

        } catch (Exception e) {
            System.out.println("⚠️ AI modal handling failed: " + e.getMessage());
            return false;
        }
    }

    private boolean executeAIPlan(String aiResponse, Locator modal) {
        try {
            if (aiResponse == null || aiResponse.isBlank()) return false;

            if (aiResponse.trim().equals("[]")) {
                if (modal.locator("input, select, textarea, button").count() > 0) {
                    System.out.println("⚠️ AI returned [], but modal still has actionable elements. Re-prompting...");
                    return false;
                } else {
                    System.out.println("✅ Modal has no actionable elements and AI returned []. Ending.");
                    return true;
                }
            }

            if (aiResponse.contains("\"FINISH\"")) {
                return true;
            }

            String[] actions = aiResponse.split("\\{");
            for (String act : actions) {
                if (act.contains("FILL_TEXT")) {
                    String field = extractValue(act, "field");
                    String value = extractValue(act, "value");
                    Locator input = modal.locator(field.isEmpty() ? "input, textarea" : field);
                    if (input.count() > 0) {
                        System.out.println("✍ Filling " + field + " with '" + value + "'");
                        input.first().fill(value);
                    }
                } else if (act.contains("SELECT_OPTION")) {
                    String field = extractValue(act, "field");
                    String value = extractValue(act, "value");
                    Locator option = modal.locator(field.isEmpty() ? "select" : field);
                    if (option.count() > 0) {
                        System.out.println("✅ Selecting option " + field + "=" + value);
                        option.first().selectOption(value);
                    }
                } else if (act.contains("CLICK_BUTTON")) {
                    String value = extractValue(act, "value");
                    Locator button = modal.locator("button:has-text('" + value + "'), a:has-text('" + value + "')");
                    if (button.count() > 0) {
                        System.out.println("🖱 Clicking button: " + value);
                        button.first().click();
                    }
                } else if (act.contains("SCROLL")) {
                    String selector = extractValue(act, "selector");
                    Locator el = modal.locator(selector);
                    if (el.count() > 0) {
                        System.out.println("⬇ Scrolling to " + selector);
                        el.first().scrollIntoViewIfNeeded();
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.out.println("⚠️ Failed executing AI actions: " + e.getMessage());
            return false;
        }
    }

    private String extractValue(String jsonFragment, String key) {
        try {
            int idx = jsonFragment.indexOf("\"" + key + "\"");
            if (idx == -1) return "";
            int colon = jsonFragment.indexOf(":", idx);
            int q1 = jsonFragment.indexOf("\"", colon + 1);
            int q2 = jsonFragment.indexOf("\"", q1 + 1);
            return jsonFragment.substring(q1 + 1, q2);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Build simplified structure of the modal (like PageCacheManager, but without saving).
     */
    private String buildSimplifiedStructure(Locator modal) {
        try {
            Map<String, Object> cacheData = new LinkedHashMap<>();

            // Headers
            List<String> headers = modal.locator("h1,h2,h3,p,label").allInnerTexts();
            cacheData.put("headers", headers);

            // Inputs
            List<Map<String, Object>> inputs = new ArrayList<>();
            Locator inputEls = modal.locator("input, select, textarea");
            for (int i = 0; i < inputEls.count(); i++) {
                Locator el = inputEls.nth(i);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("selector", safeSelector(el));
                info.put("type", safeAttr(el, "type"));
                info.put("name", safeAttr(el, "name"));
                info.put("id", safeAttr(el, "id"));
                info.put("value", safeAttr(el, "value"));
                info.put("placeholder", safeAttr(el, "placeholder"));
                info.put("required", el.getAttribute("required") != null);
                try {
                    String label = el.locator("xpath=./preceding::label[1]").textContent();
                    info.put("label", label != null ? label.trim() : "");
                } catch (Exception ignored) {
                    info.put("label", "");
                }
                inputs.add(info);
            }
            cacheData.put("inputs", inputs);

            // Buttons
            List<Map<String, Object>> buttons = new ArrayList<>();
            Locator btnEls = modal.locator("button, input[type=submit]");
            for (int i = 0; i < btnEls.count(); i++) {
                Locator el = btnEls.nth(i);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("selector", safeSelector(el));
                info.put("text", el.textContent());
                buttons.add(info);
            }
            cacheData.put("buttons", buttons);

            // Links
            List<Map<String, Object>> links = new ArrayList<>();
            Locator linkEls = modal.locator("a[href]");
            for (int i = 0; i < linkEls.count(); i++) {
                Locator el = linkEls.nth(i);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("selector", safeSelector(el));
                info.put("text", el.textContent());
                info.put("url", safeAttr(el, "href"));
                links.add(info);
            }
            cacheData.put("links", links);

            return cacheData.toString();
        } catch (Exception e) {
            return "⚠️ Failed to simplify modal: " + e.getMessage();
        }
    }

    private String safeAttr(Locator el, String name) {
        try {
            String val = el.getAttribute(name);
            return val != null ? val : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String safeSelector(Locator el) {
        try {
            return (String) el.evaluate("el => {" +
                    "let path = [];" +
                    "while (el && el.nodeType === 1) {" +
                    "let selector = el.nodeName.toLowerCase();" +
                    "if (el.id) { selector += '#' + el.id; path.unshift(selector); break; }" +
                    "else {" +
                    "let sib = el, nth = 1;" +
                    "while (sib = sib.previousElementSibling) { if (sib.nodeName === el.nodeName) nth++; }" +
                    "selector += ':nth-of-type(' + nth + ')';" +
                    "}" +
                    "path.unshift(selector);" +
                    "el = el.parentElement;" +
                    "}" +
                    "return path.join(' > ');" +
                    "}");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get ANY modal/dialog on the page, not just apply-job-modal.
     */
    private Locator getAnyModal() {
        Locator modal = page.locator("div[role='dialog'], div[data-qa='apply-job-modal'], div[data-qa='job-details-drawer-modal']");
        return modal.count() > 0 ? modal.first() : null;
    }
}