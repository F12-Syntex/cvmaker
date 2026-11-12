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
