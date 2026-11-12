package com.cvmaker.crawler.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cvmaker.crawler.FormField;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Handles low-level interactions with form fields.
 *
 * <p>Fields are dispatched on their tag first and their {@code type} second (see
 * {@link FormField#getKind()}), so {@code <select>} and {@code <textarea>} are handled as such.
 * When a select or radio group has no option matching the wanted value the field is left
 * unfilled and recorded in {@link #getUnfilledFields()}; nothing is picked at random.
 */
public class FieldHandler {
    private static final Set<String> TEXT_KINDS =
            Set.of("text", "email", "tel", "number", "password", "url", "textarea");
    private static final Set<String> AFFIRMATIVE =
            Set.of("true", "yes", "y", "accept", "agree", "on", "1", "checked");

    private final Page page;
    private final List<String> unfilled = new ArrayList<>();

    public FieldHandler(Page page) {
        this.page = page;
    }

    /**
     * Fill a field based on its kind (tag, then type).
     *
     * @return true only if the value was applied and the page shows it applied
     */
    public boolean fill(FormField field, String value) {
        if (field == null || field.getElement() == null || value == null) {
            return false;
        }
        String kind = field.getKind();
        try {
            if (TEXT_KINDS.contains(kind)) {
                field.getElement().fill(value);
                return verifyText(field);
            }
            switch (kind) {
                case "select":
                    return handleSelect(field, value);
                case "radio":
                    return handleRadio(field, value);
                case "checkbox":
                    return handleCheckbox(field, value);
                default:
                    markUnfilled(field, "unsupported field kind '" + kind + "'");
                    return false;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error filling field " + field.getIdentifier() + ": " + e.getMessage());
            markUnfilled(field, "error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fields this handler could not fill, each as "identifier: reason", in the order they failed.
     */
    public List<String> getUnfilledFields() {
        return Collections.unmodifiableList(unfilled);
    }

    private boolean handleSelect(FormField field, String value) {
        Locator select = field.getElement();
        Locator optionElements = select.locator("option");
        int count = optionElements.count();
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Locator option = optionElements.nth(i);
            options.add(new Option(option.textContent(), option.getAttribute("value")));
        }

        String chosen = findOptionValue(options, value);
        if (chosen == null) {
            markUnfilled(field, "no option matches '" + value + "'");
            return false;
        }
        select.selectOption(chosen);
        String actual = select.inputValue();
        return chosen.equals(actual);
    }

    private boolean handleRadio(FormField field, String value) {
        String name = field.getName();
        if (name == null || name.isEmpty()) {
            markUnfilled(field, "radio button has no name, cannot find its group");
            return false;
        }

        Page target = field.getElement().page() != null ? field.getElement().page() : page;
        Locator radios = target.locator("input[type='radio'][name='" + name + "']");
        int count = radios.count();
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(new Option(null, radios.nth(i).getAttribute("value")));
        }

        int index = findOptionIndex(options, value);
        if (index < 0) {
            markUnfilled(field, "no radio choice matches '" + value + "'");
            return false;
        }
        Locator radio = radios.nth(index);
        radio.check();
        return radio.isChecked();
    }

    private boolean handleCheckbox(FormField field, String value) {
        boolean shouldCheck = isAffirmative(value);
        if (shouldCheck) {
            field.getElement().check();
        } else {
            field.getElement().uncheck();
        }
        return field.getElement().isChecked() == shouldCheck;
    }

    private boolean verifyText(FormField field) {
        String actual = field.getElement().inputValue();
        return actual != null && !actual.isEmpty();
    }

    private void markUnfilled(FormField field, String reason) {
        String entry = field.getIdentifier() + ": " + reason;
        unfilled.add(entry);
        System.out.println("⏭️ Left unfilled: " + entry);
    }

    // -------------------
    // Pure helpers (package-private for tests)
    // -------------------

    /** One choice of a select or radio group: its visible text and its value attribute. */
    record Option(String text, String value) {
        /** The value the browser submits: the value attribute, or the text when it is missing. */
        String effectiveValue() {
            return value != null ? value : (text == null ? "" : text.trim());
        }
    }

    /**
     * Index of the option whose value, or failing that whose visible text, equals {@code wanted}
     * (ignoring case and surrounding whitespace). Returns -1 if none matches; never guesses.
     */
    static int findOptionIndex(List<Option> options, String wanted) {
        String w = normalise(wanted);
        if (w.isEmpty() || options == null) {
            return -1;
        }
        for (int i = 0; i < options.size(); i++) {
            if (normalise(options.get(i).value()).equals(w)) {
                return i;
            }
        }
        for (int i = 0; i < options.size(); i++) {
            if (normalise(options.get(i).text()).equals(w)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The value to pass to {@code selectOption} for {@code wanted}, or null if no option matches.
     */
    static String findOptionValue(List<Option> options, String wanted) {
        int i = findOptionIndex(options, wanted);
        return i < 0 ? null : options.get(i).effectiveValue();
    }

    /** Whether a suggested checkbox value means "tick it". */
    static boolean isAffirmative(String value) {
        return AFFIRMATIVE.contains(normalise(value));
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
