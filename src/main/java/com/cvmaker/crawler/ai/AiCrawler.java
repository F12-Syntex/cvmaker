package com.cvmaker.crawler.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.AbstractJobCrawler;
import com.cvmaker.crawler.ExternalRedirectHandler;
import com.cvmaker.crawler.cache.PageInputCacheManagerCSV;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.LoadState;

/**
 * AI-powered universal crawler that can attempt job applications
 * on ANY website by interpreting cached CSV of inputs and buttons.
 */
public class AiCrawler extends AbstractJobCrawler {

    private final AiService aiService;

    public AiCrawler() throws Exception {
        this(new CrawlerConfig());
    }

    public AiCrawler(CrawlerConfig crawlerConfig) throws Exception {
        super(crawlerConfig);
        this.aiService = new AiService(LLMModel.GPT_4_1_MINI, 0.6);
    }

    @Override
    public String getCrawlerName() {
        return "AI Universal Job Crawler";
    }

    @Override
    public void processJobsAndApply() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("\n🤖 AI Crawler Ready!");
            System.out.println("Enter a job application URL:");
            String url = scanner.nextLine().trim();

            // Navigate to the provided URL
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(crawlerConfig.getPageLoadDelay());

            // Save CSV cache of inputs/buttons
            PageInputCacheManagerCSV.saveInputCache("ai-crawler", page);

            // Read CSV back as text
            Path csvPath = Paths.get("cache", "ai-crawler", url.replaceAll("[^a-zA-Z0-9-_]", "_") + ".inputs.csv");
            if (!Files.exists(csvPath)) {
                System.out.println("⚠️ No cached CSV found at " + csvPath);
                return;
            }

            String csvContent = Files.readString(csvPath);

            // Build AI prompt
            String prompt = String.format("""
                You are an expert job applicant assistant.
                The user wants to apply for a SOFTWARE ENGINEERING job on ANY website.

                Here is the simplified INPUT/BUTTON CSV from the webpage:
                %s

                Instructions:
                - Interpret what each field means (name, email, resume upload, job title, etc).
                - Provide realistic values for a job application.
                - Simulate pressing the right buttons (e.g., Apply, Next, Submit).
                - If scrolling is needed, indicate it.
                - Return a JSON array of ordered actions with this format:
                  [
                    {"action": "fill", "selector": "...", "value": "..."},
                    {"action": "click", "selector": "..."},
                    {"action": "scroll", "selector": "..."}
                  ]

                Only return valid JSON. No explanations.
                """, csvContent);

            System.out.println("🧠 Sending page structure to AI...");
            String aiResponse = aiService.query(prompt);
            System.out.println("AI Response: " + aiResponse);

            // Execute AI instructions
            executeActions(aiResponse);

            // 🔹 Step 3: Handle external redirect after AI actions
            ExternalRedirectHandler redirectHandler = new ExternalRedirectHandler(page, crawlerConfig);
            redirectHandler.handleRedirect();

        } catch (Exception e) {
            System.out.println("⚠️ Error in AI Crawler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeActions(String aiJson) {
        try {
            // Very simple regex parsing (AI returns JSON array)
            String[] actions = aiJson.split("\\{");
            for (String act : actions) {
                if (act.contains("fill")) {
                    String selector = extractValue(act, "selector");
                    String value = extractValue(act, "value");
                    if (!selector.isEmpty() && !value.isEmpty()) {
                        System.out.println("✍️ Filling " + selector + " with '" + value + "'");
                        Locator field = page.locator(selector).first();
                        if (field != null && field.isVisible()) {
                            field.fill(value);
                        }
                    }
                } else if (act.contains("click")) {
                    String selector = extractValue(act, "selector");
                    if (!selector.isEmpty()) {
                        System.out.println("🖱️ Clicking " + selector);
                        Locator button = page.locator(selector).first();
                        if (button != null && button.isVisible()) {
                            button.click();
                            page.waitForLoadState(LoadState.NETWORKIDLE);
                        }
                    }
                } else if (act.contains("scroll")) {
                    String selector = extractValue(act, "selector");
                    if (!selector.isEmpty()) {
                        System.out.println("📜 Scrolling to " + selector);
                        Locator el = page.locator(selector).first();
                        if (el != null && el.isVisible()) {
                            el.scrollIntoViewIfNeeded();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Failed to execute AI actions: " + e.getMessage());
        }
    }

    private String extractValue(String jsonFragment, String key) {
        try {
            int idx = jsonFragment.indexOf("\"" + key + "\"");
            if (idx == -1) return "";
            int colon = jsonFragment.indexOf(":", idx);
            int quote1 = jsonFragment.indexOf("\"", colon + 1);
            int quote2 = jsonFragment.indexOf("\"", quote1 + 1);
            return jsonFragment.substring(quote1 + 1, quote2);
        } catch (Exception e) {
            return "";
        }
    }
}