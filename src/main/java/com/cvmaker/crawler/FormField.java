package com.cvmaker.crawler;

import com.microsoft.playwright.Locator;

/**
 * Represents a form field with its properties and associated element.
 */
public class FormField {
    private Locator element;
    private String identifier;
    private String type;
    private String label;
    private boolean required;
    private boolean readOnly;
    private String value;
    private String placeholder;
    private String name;
    private String id;

    public FormField() {
    }

    public FormField(Locator element) {
        this.element = element;
        this.extractFieldProperties();
    }

    private void extractFieldProperties() {
        try {
            this.type = element.getAttribute("type");
            this.name = element.getAttribute("name");
            this.id = element.getAttribute("id");
            this.placeholder = element.getAttribute("placeholder");
            this.required = element.getAttribute("required") != null;
            this.readOnly = element.getAttribute("readonly") != null;
            
            // Generate identifier based on available properties
            this.identifier = generateIdentifier();
            
            // Try to find associated label
            this.label = findAssociatedLabel();
            
        } catch (Exception e) {
            // Handle silently - properties will remain null
        }
    }

    private String generateIdentifier() {
        if (id != null && !id.isEmpty()) return id;
        if (name != null && !name.isEmpty()) return name;
        if (placeholder != null && !placeholder.isEmpty()) return placeholder;
        return "unnamed_field";
    }

    private String findAssociatedLabel() {
        try {
            if (id != null && !id.isEmpty()) {
                Locator label = element.page().locator("label[for='" + id + "']");
                if (label.count() > 0) {
                    return label.first().textContent();
                }
            }
            
            // Try finding nearest label
            Locator nearestLabel = element.locator("xpath=./preceding::label[1]");
            if (nearestLabel.count() > 0) {
                return nearestLabel.first().textContent();
            }
        } catch (Exception e) {
            // Continue without label
        }
        return null;
    }

    // Getters and Setters
    public Locator getElement() {
        return element;
    }

    public void setElement(Locator element) {
        this.element = element;
        if (element != null) {
            this.extractFieldProperties();
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getType() {
        return type != null ? type : "text";
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isVisible() {
        try {
            return element != null && element.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("FormField{identifier='%s', type='%s', label='%s', required=%s}",
                identifier, type, label, required);
    }
}