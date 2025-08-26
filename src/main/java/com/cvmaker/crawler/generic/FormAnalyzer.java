package com.cvmaker.crawler.generic;

import java.util.HashMap;
import java.util.Map;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.generic.utils.AIResponseParser;
import com.cvmaker.crawler.generic.utils.FieldExtractor;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Responsible for analyzing a form using AI.
 * Extracts structure, builds a prompt, queries AI, and stores context.
 */
public class FormAnalyzer {
    private final Page page;
    private final CrawlerConfig config;
    private final AiService aiService;
    private final Map<String, String> formContext = new HashMap<>();

    public FormAnalyzer(Page page, CrawlerConfig config) {
        this.page = page;
        this.config = config;
        this.aiService = new AiService(LLMModel.GPT_5_1_MINI, 0.7); // creative responses
    }

    /**
     * Analyze the form using DOM inspection + AI analysis.
     */
    public void analyzeForm() {
        try {
            System.out.println("🧠 Analyzing form structure and context...");

            String structure = extractFormStructure();
            String context = extractPageContext();

            String prompt = createAnalysisPrompt(structure, context);

            System.out.println("🤖 Sending form structure to AI for analysis...");
            String aiResponse = aiService.query(prompt);

            formContext.clear();
            formContext.putAll(AIResponseParser.parse(aiResponse));

            System.out.println("✅ AI analysis complete. Extracted " + formContext.size() + " field mappings.");

        } catch (Exception e) {
            System.out.println("⚠️ Error during form analysis: " + e.getMessage());
        }
    }

    /**
     * Return the AI-analyzed form context (field → value mapping).
     */
    public Map<String, String> getFormContext() {
        return formContext;
    }

    // -----------------------
    // Internal helper methods
    // -----------------------

    private String extractFormStructure() {
        StringBuilder structure = new StringBuilder();
        try {
            Locator formFields = page.locator("input, select, textarea");
            int count = formFields.count();

            structure.append("Form Fields Found:\n");

            for (int i = 0; i < count; i++) {
                Locator field = formFields.nth(i);
                if (field.isVisible()) {
                    Map<String, String> info = FieldExtractor.extractFieldInfo(field);
                    structure.append(formatFieldInfo(info)).append("\n");
                }
            }

            Locator sections = page.locator("form h1, form h2, form h3, form legend, form fieldset");
            int sectionCount = sections.count();

            if (sectionCount > 0) {
                structure.append("\nForm Sections:\n");
                for (int i = 0; i < sectionCount; i++) {
                    structure.append("- ").append(sections.nth(i).textContent()).append("\n");
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error extracting form structure: " + e.getMessage());
        }
        return structure.toString();
    }

    private String extractPageContext() {
        StringBuilder context = new StringBuilder();
        try {
            context.append("Page Title: ").append(page.title()).append("\n");
            context.append("URL: ").append(page.url()).append("\n\n");

            String[] jobDescSelectors = {
                "[class*='job-description']",
                "[class*='description']",
                "article",
                ".job-details",
                "#job-details"
            };

            for (String selector : jobDescSelectors) {
                try {
                    Locator desc = page.locator(selector).first();
                    if (desc != null && desc.isVisible()) {
                        context.append("Job Description:\n")
                               .append(desc.textContent())
                               .append("\n\n");
                        break;
                    }
                } catch (Exception ignore) {}
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error extracting page context: " + e.getMessage());
        }
        return context.toString();
    }

    private String createAnalysisPrompt(String formStructure, String pageContext) {
        return String.format(
            """
            Analyze this job application form and provide appropriate values for each field.

            Page Context:
            %s

            Form Structure:
            %s

            Please provide a JSON response with:
            1. Suggested values for each input field
            2. Which fields are required vs optional
            3. Suggested handling for complex fields
            4. Suggested responses for essay/text areas

            Format the response as valid JSON with field mappings.
            """,
            pageContext, formStructure
        );
    }

    private String formatFieldInfo(Map<String, String> fieldInfo) {
        StringBuilder sb = new StringBuilder("Field: {");
        if (fieldInfo.containsKey("type")) sb.append("\n  Type: ").append(fieldInfo.get("type"));
        if (fieldInfo.containsKey("name")) sb.append("\n  Name: ").append(fieldInfo.get("name"));
        if (fieldInfo.containsKey("id")) sb.append("\n  ID: ").append(fieldInfo.get("id"));
        if (fieldInfo.containsKey("label")) sb.append("\n  Label: ").append(fieldInfo.get("label"));
        if (fieldInfo.containsKey("placeholder")) sb.append("\n  Placeholder: ").append(fieldInfo.get("placeholder"));
        if (fieldInfo.containsKey("required")) sb.append("\n  Required: ").append(fieldInfo.get("required"));
        sb.append("\n}");
        return sb.toString();
    }
}