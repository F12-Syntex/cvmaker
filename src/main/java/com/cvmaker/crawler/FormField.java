package com.cvmaker.crawler;

import java.util.Locale;

import com.microsoft.playwright.Locator;

/**
 * Represents a form field with its properties and associated element.
 */
public class FormField {
    private Locator element;
    private String identifier;
    private String type;
    private String tagName;
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
        this.tagName = readTagName(element);
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

    private static String readTagName(Locator element) {
        try {
            Object tag = element.evaluate("el => el.tagName");
            return tag == null ? null : tag.toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Works out what kind of control a field is from its tag name and its {@code type} attribute.
     * {@code <select>} and {@code <textarea>} have no {@code type} attribute, so the tag decides
     * for them; for {@code <input>} (or an unknown tag) the type decides, defaulting to "text".
     *
     * @param tagName the element's tag name, any case, may be null
     * @param type    the element's {@code type} attribute, any case, may be null
     * @return a lower-case kind such as "select", "textarea", "checkbox", "radio" or "text"
     */
    public static String kindOf(String tagName, String type) {
        String tag = tagName == null ? "" : tagName.trim().toLowerCase(Locale.ROOT);
        if (tag.equals("select") || tag.equals("textarea")) {
            return tag;
        }
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? "text" : t;
    }

    private String generateIdentifier() {
        if (id != null && !id.isEmpty()) return id;
        if (name != null && !name.isEmpty()) return name;
        if (placeholder != null && !placeholder.isEmpty()) return placeholder;
        return "unnamed_field";
    }

    private String findAssociatedLabel() {
        String label = LabelResolver.findLabel(element, id);
        return label.isEmpty() ? null : label;
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

    /**
     * @return the lower-case tag name ("input", "select", "textarea"), or null if unknown
     */
    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    /**
     * @return the kind of control, decided by the tag first and then the type; see {@link #kindOf}
     */
    public String getKind() {
        return kindOf(tagName, type);
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
        return String.format("FormField{identifier='%s', tag='%s', type='%s', label='%s', required=%s}",
                identifier, tagName, type, label, required);
    }
}