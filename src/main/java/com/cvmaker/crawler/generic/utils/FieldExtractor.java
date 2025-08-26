package com.cvmaker.crawler.generic.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.Locator;

/**
 * Utility class for extracting attributes and metadata from form fields.
 */
public class FieldExtractor {

    /**
     * Extracts common field info (type, name, id, placeholder, required, label).
     */
    public static Map<String, String> extractFieldInfo(Locator field) {
        Map<String, String> info = new HashMap<>();
        try {
            info.put("type", safeAttr(field, "type", "text"));
            info.put("name", safeAttr(field, "name", ""));
            info.put("id", safeAttr(field, "id", ""));
            info.put("placeholder", safeAttr(field, "placeholder", ""));
            info.put("required", field.getAttribute("required") != null ? "true" : "false");
            info.put("label", findAssociatedLabel(field));
        } catch (Exception e) {
            // Continue with partial info
        }
        return info;
    }

    /**
     * Extracts all relevant attributes for debugging or AI analysis.
     */
    public static Map<String, String> extractFieldAttributes(Locator element) {
        Map<String, String> attributes = new HashMap<>();

        try {
            String[] attrs = {
                "type", "name", "id", "placeholder", "value", "required",
                "readonly", "disabled", "maxlength", "min", "max", "pattern"
            };

            for (String attr : attrs) {
                String val = element.getAttribute(attr);
                if (val != null) {
                    attributes.put(attr, val);
                }
            }

            String tagName = element.evaluate("node => node.tagName").toString().toLowerCase();
            attributes.put("tagName", tagName);

            String label = findAssociatedLabel(element);
            if (label != null) {
                attributes.put("label", label);
            }

            if (tagName.equals("select")) {
                List<String> options = new ArrayList<>();
                Locator optionElements = element.locator("option");
                int optionCount = optionElements.count();

                for (int i = 0; i < optionCount; i++) {
                    String optionText = optionElements.nth(i).textContent();
                    if (optionText != null && !optionText.trim().isEmpty()) {
                        options.add(optionText.trim());
                    }
                }
                if (!options.isEmpty()) {
                    attributes.put("options", String.join("|", options));
                }
            }

        } catch (Exception e) {
            System.out.println("Warning: Error extracting field attributes: " + e.getMessage());
        }

        return attributes;
    }

    /**
     * Try to find the label for a given field.
     */
    public static String findAssociatedLabel(Locator field) {
        try {
            String id = field.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                Locator label = field.page().locator("label[for='" + id + "']");
                if (label.count() > 0) {
                    return label.first().textContent();
                }
            }
            Locator nearestLabel = field.locator("xpath=./preceding::label[1]");
            if (nearestLabel.count() > 0) {
                return nearestLabel.first().textContent();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    private static String safeAttr(Locator field, String name, String defaultValue) {
        try {
            String val = field.getAttribute(name);
            return val != null ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}