package com.cvmaker.crawler;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * One place to find the label of a form field and to build attribute selectors safely.
 *
 * <p>Selectors are built with the attribute value as a quoted CSS string, escaped as in the CSSOM
 * "serialize a string" rule, so ids and names that contain quotes, backslashes, {@code $} or
 * {@code :} (ASP.NET produces such ids) do not break the selector. {@code "#" + id} is never used,
 * because {@code $}, {@code :} and leading digits are not valid in an unescaped id selector.
 */
public final class LabelResolver {

    private static final String PRECEDING_LABEL = "xpath=./preceding::label[1]";

    private LabelResolver() {
    }

    /**
     * Quote and escape a value as a CSS string, for use inside an attribute selector.
     *
     * @param value any text, null is treated as empty
     * @return the value in double quotes, with {@code "} and {@code \} backslash-escaped, control
     *         characters written as hex escapes and NUL replaced by U+FFFD
     */
    public static String cssString(String value) {
        String v = value == null ? "" : value;
        StringBuilder sb = new StringBuilder(v.length() + 2).append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == 0) {
                sb.append('�');
            } else if ((c >= 0x01 && c <= 0x1F) || c == 0x7F) {
                sb.append('\\').append(Integer.toHexString(c)).append(' ');
            } else if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Build {@code prefix[attribute="value"]} with the value escaped by {@link #cssString}.
     *
     * @param prefix    a tag or selector to put in front, may be empty
     * @param attribute the attribute name (a plain identifier, not escaped)
     * @param value     the attribute value to match exactly
     */
    public static String attributeSelector(String prefix, String attribute, String value) {
        return (prefix == null ? "" : prefix) + "[" + attribute + "=" + cssString(value) + "]";
    }

    /** Selector for the {@code <label for=...>} of an element id. */
    public static String labelForSelector(String id) {
        return attributeSelector("label", "for", id);
    }

    /** Selector for the element with this id; safe for any id, unlike {@code "#" + id}. */
    public static String idSelector(String id) {
        return attributeSelector("", "id", id);
    }

    /** Selector for every radio button of the group with this name. */
    public static String radioGroupSelector(String name) {
        return attributeSelector("input[type=\"radio\"]", "name", name);
    }

    /**
     * Find the label text for a form field: the {@code <label for=id>} first, then the nearest
     * {@code <label>} before the field in document order.
     *
     * @return the label text with whitespace collapsed, or "" if none is found
     */
    public static String findLabel(Locator field) {
        if (field == null) {
            return "";
        }
        return findLabel(field, safeAttribute(field, "id"));
    }

    /**
     * Same as {@link #findLabel(Locator)} when the field's id is already known.
     */
    public static String findLabel(Locator field, String id) {
        if (field == null) {
            return "";
        }
        String byFor = labelForId(safePage(field), id);
        if (!byFor.isEmpty()) {
            return byFor;
        }
        try {
            Locator nearest = field.locator(PRECEDING_LABEL);
            if (nearest != null && nearest.count() > 0) {
                return clean(nearest.first().textContent());
            }
        } catch (Exception ignore) {
            // no label
        }
        return "";
    }

    /**
     * Text of the {@code <label for=id>} on the page, or "" if there is none.
     */
    public static String labelForId(Page page, String id) {
        if (page == null || id == null || id.isEmpty()) {
            return "";
        }
        try {
            Locator label = page.locator(labelForSelector(id));
            if (label != null && label.count() > 0) {
                return clean(label.first().textContent());
            }
        } catch (Exception ignore) {
            // no label
        }
        return "";
    }

    /**
     * Join the texts of the elements named in a space-separated id list, as used by
     * {@code aria-labelledby}.
     *
     * @return the joined text, or "" if none of the ids resolves to text
     */
    public static String textByIds(Page page, String ids) {
        if (page == null || ids == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids.trim().split("\\s+")) {
            if (id.isEmpty()) {
                continue;
            }
            try {
                Locator el = page.locator(idSelector(id));
                if (el != null && el.count() > 0) {
                    String t = clean(el.first().textContent());
                    if (!t.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(t);
                    }
                }
            } catch (Exception ignore) {
                // skip this id
            }
        }
        return sb.toString();
    }

    /** Collapse runs of whitespace and trim; null becomes "". */
    static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String safeAttribute(Locator field, String name) {
        try {
            return field.getAttribute(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static Page safePage(Locator field) {
        try {
            return field.page();
        } catch (Exception e) {
            return null;
        }
    }
}
