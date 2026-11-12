package com.cvmaker.crawler.reed;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure text helpers for Reed pages, kept free of Playwright types so they can be tested offline.
 */
final class ReedText {

    private static final String[] EASY_APPLY_KEYWORDS = {"easy apply", "quick apply"};

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0\\u2007\\u202f]+");

    private ReedText() {
    }

    /**
     * @param cardText the text content of a job card, may be null
     * @return true if the text mentions "Easy Apply" or "Quick Apply"
     */
    static boolean mentionsEasyApply(String cardText) {
        if (cardText == null) {
            return false;
        }
        String lower = normaliseWhitespace(cardText).toLowerCase(Locale.ROOT);
        for (String keyword : EASY_APPLY_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decide whether text taken from a description element is worth using.
     *
     * @param raw       the element's text content, may be null
     * @param minLength the trimmed text must be longer than this many characters
     * @return the trimmed text, or null if it is null, blank or too short
     */
    static String usableDescription(String raw, int minLength) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.length() <= minLength) {
            return null;
        }
        return trimmed;
    }

    /**
     * Clean a short field read from a job card (title, company).
     *
     * @param raw      the element's text content, may be null
     * @param fallback the value to use when the text is null or blank
     * @return the text on one line with whitespace collapsed, or the fallback
     */
    static String cardField(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String cleaned = normaliseWhitespace(raw);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    /**
     * Collapse every run of whitespace (including non-breaking spaces and line breaks, which
     * {@code textContent()} keeps from the markup) into one space, and trim the ends.
     *
     * @param text the text, not null
     * @return the normalised text
     */
    static String normaliseWhitespace(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }
}
