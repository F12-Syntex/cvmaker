package com.cvmaker.crawler.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.FormField;
import com.cvmaker.service.ai.AiService;
import com.cvmaker.service.ai.LLMModel;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Responsible for filling forms automatically using AI values.
 */
public class FormFiller {
    private final Page page;
    private final CrawlerConfig config;
    private final FieldHandler fieldHandler;
    private final AiService aiService;

    public FormFiller(Page page, CrawlerConfig config) {
        this.page = page;
        this.config = config;
        this.fieldHandler = new FieldHandler(page);
        this.aiService = new AiService(LLMModel.GPT_5_1_MINI, 0.5); // more deterministic than analyzer
    }

    /**
     * Fill the form using AI-suggested values.
     */
    public void fillForm(Map<String, String> formContext) {
        try {
            System.out.println("📝 Starting automatic form filling...");

            List<FormField> fields = identifyFormFields();
            int successCount = 0;
            int totalFields = 0;

            for (FormField field : fields) {
                if (field.isVisible() && !field.isReadOnly()) {
                    totalFields++;
                    String value = getAIValueForField(field, formContext);
                    if (value != null && !value.isEmpty()) {
                        boolean ok = fieldHandler.fill(field, value);
                        if (ok) successCount++;
                    }
                }
            }

            System.out.printf("✅ Form filling complete! Filled %d out of %d fields.\n",
                    successCount, totalFields);

            System.out.println("\nPlease review the filled form and press Enter to submit...");
            System.in.read();

            if (submitForm()) {
                System.out.println("🎉 Form submitted successfully!");
            } else {
                System.out.println("⚠️ Form submission failed. Please submit manually.");
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error during form filling: " + e.getMessage());
        }
    }

    // -------------------
    // Internal helpers
    // -------------------

    private String getAIValueForField(FormField field, Map<String, String> formContext) {
        try {
            String prompt = String.format(
                """
                Suggest an appropriate value for this form field:
                Type: %s
                Label: %s
                Identifier: %s
                Required: %s
                Current Context: %s

                Provide only the value, no explanation.
                """,
                field.getType(),
                field.getLabel(),
                field.getIdentifier(),
                field.isRequired(),
                formContext.toString()
            );

            String suggestion = aiService.query(prompt);
            return cleanAISuggestion(suggestion, field.getType());

        } catch (Exception e) {
            System.out.println("⚠️ Error getting AI value for field: " + e.getMessage());
            return null;
        }
    }

    private String cleanAISuggestion(String suggestion, String fieldType) {
        if (suggestion == null || suggestion.isEmpty()) return null;

        suggestion = suggestion.replaceAll("```.*?```", "").trim();

        switch (fieldType.toLowerCase()) {
            case "email":
                return suggestion.matches(".*@.*\\..*") ? suggestion : null;
            case "tel":
                return suggestion.replaceAll("[^0-9+\\-()]", "");
            case "number":
                return suggestion.replaceAll("[^0-9\\-.]", "");
            default:
                return suggestion;
        }
    }

    private boolean submitForm() {
        try {
            String[] submitSelectors = {
                "button[type='submit']",
                "input[type='submit']",
                "button:has-text('Submit')",
                "button:has-text('Apply')",
                ".submit-button",
                "#submit-button"
            };

            for (String selector : submitSelectors) {
                Locator submitButton = page.locator(selector).first();
                if (submitButton != null && submitButton.isVisible()) {
                    submitButton.click();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private List<FormField> identifyFormFields() {
        List<FormField> fields = new ArrayList<>();

        try {
            String[] selectors = {
                "input:not([type='hidden'])",
                "select",
                "textarea"
            };

            for (String selector : selectors) {
                Locator elements = page.locator(selector);
                int count = elements.count();

                for (int i = 0; i < count; i++) {
                    try {
                        Locator element = elements.nth(i);
                        if (element.isVisible()) {
                            FormField field = new FormField(element);
                            if (isValidFormField(field)) {
                                fields.add(field);
                            }
                        }
                    } catch (Exception ignore) {}
                }
            }

            // Sort fields by vertical position
            Collections.sort(fields, (a, b) -> {
                try {
                    double ya = a.getElement().boundingBox().y;
                    double yb = b.getElement().boundingBox().y;
                    return Double.compare(ya, yb);
                } catch (Exception e) {
                    return 0;
                }
            });

        } catch (Exception e) {
            System.out.println("⚠️ Error identifying form fields: " + e.getMessage());
        }

        return fields;
    }

    private boolean isValidFormField(FormField field) {
        if (field == null || field.getElement() == null) return false;
        if (!field.isVisible()) return false;

        String type = field.getType().toLowerCase();
        if (type.equals("hidden") || type.equals("submit") || type.equals("reset")) {
            return false;
        }

        String id = field.getIdentifier();
        return id != null && !id.isEmpty() && !id.equals("unnamed_field");
    }
}