package com.cvmaker.crawler.generic;

import com.cvmaker.crawler.FormField;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Handles low-level interactions with form fields.
 */
public class FieldHandler {
    private final Page page;

    public FieldHandler(Page page) {
        this.page = page;
    }

    /**
     * Fill a field based on its type.
     */
    public boolean fill(FormField field, String value) {
        try {
            switch (field.getType().toLowerCase()) {
                case "text":
                case "email":
                case "tel":
                case "number":
                case "password":
                case "url":
                    field.getElement().fill(value);
                    break;

                case "textarea":
                    field.getElement().fill(value);
                    break;

                case "select":
                    handleSelect(field.getElement(), value);
                    break;

                case "radio":
                    handleRadio(field, value);
                    break;

                case "checkbox":
                    handleCheckbox(field, value);
                    break;

                default:
                    return false;
            }

            return verify(field, value);

        } catch (Exception e) {
            System.out.println("⚠️ Error filling field " + field.getIdentifier() + ": " + e.getMessage());
            return false;
        }
    }

    private void handleSelect(Locator select, String value) {
        try {
            select.selectOption(value);
        } catch (Exception e) {
            // fallback: try options manually
            Locator options = select.locator("option");
            int count = options.count();
            for (int i = 0; i < count; i++) {
                String optionText = options.nth(i).textContent();
                String optionValue = options.nth(i).getAttribute("value");
                if (optionText != null && optionText.equalsIgnoreCase(value)) {
                    select.selectOption(optionValue);
                    return;
                }
            }
            if (count > 0) {
                String fallback = options.nth(0).getAttribute("value");
                if (fallback != null) select.selectOption(fallback);
            }
        }
    }

    private void handleRadio(FormField field, String value) {
        try {
            String name = field.getName();
            if (name == null || name.isEmpty()) return;

            Locator radios = field.getElement().page().locator("input[type='radio'][name='" + name + "']");
            int count = radios.count();

            for (int i = 0; i < count; i++) {
                Locator radio = radios.nth(i);
                String radioValue = radio.getAttribute("value");
                if (radioValue != null && radioValue.equalsIgnoreCase(value)) {
                    radio.check();
                    return;
                }
            }
            if (count > 0) radios.first().check();

        } catch (Exception e) {
            System.out.println("⚠️ Error handling radio: " + e.getMessage());
        }
    }

    private void handleCheckbox(FormField field, String value) {
        try {
            boolean shouldCheck = value.equalsIgnoreCase("true")
                               || value.equalsIgnoreCase("yes")
                               || value.equalsIgnoreCase("accept");
            if (shouldCheck) {
                field.getElement().check();
            } else {
                field.getElement().uncheck();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error handling checkbox: " + e.getMessage());
        }
    }

    private boolean verify(FormField field, String expectedValue) {
        try {
            String actual = field.getElement().inputValue();
            return actual != null && !actual.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}