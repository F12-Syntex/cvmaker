package com.cvmaker.application.management.sheets;

import java.util.Locale;
import java.util.Optional;

/**
 * Builds Google Sheets {@code =HYPERLINK(...)} formulas from untrusted URLs.
 *
 * <p>The URLs written to the tracker come from email content and from AI output, so they must
 * never be able to change the formula they are placed in. A URL is used only when it is a plain
 * {@code http}/{@code https} link, and every double quote inside it is doubled, which is how a
 * Sheets string literal escapes a quote. Anything else yields no formula at all, and the caller
 * leaves the cell empty.
 */
public final class HyperlinkFormula {

    /** Longest URL that is turned into a link. Longer values are treated as not a URL. */
    static final int MAX_URL_LENGTH = 2000;

    private HyperlinkFormula() {
    }

    /**
     * Returns the URL in the form it may be linked to, or empty when it must not be linked.
     *
     * <p>A URL is accepted when, after trimming, it starts with {@code http://} or
     * {@code https://} (any letter case), has something after the scheme, is at most
     * {@value #MAX_URL_LENGTH} characters long and contains no control characters (so no line
     * breaks or tabs).
     *
     * @param url the untrusted URL, may be null
     * @return the trimmed URL, or empty
     */
    public static Optional<String> safeUrl(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_URL_LENGTH) {
            return Optional.empty();
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        int schemeLength;
        if (lower.startsWith("https://")) {
            schemeLength = "https://".length();
        } else if (lower.startsWith("http://")) {
            schemeLength = "http://".length();
        } else {
            return Optional.empty();
        }

        // There must be a host after the scheme: "https://" or "https:///path" is not a link.
        if (trimmed.length() == schemeLength || isHostTerminator(trimmed.charAt(schemeLength))) {
            return Optional.empty();
        }

        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(trimmed);
    }

    /**
     * Tells whether {@link #build(String, String)} would produce a formula for this URL.
     *
     * @param url the untrusted URL, may be null
     * @return true when the URL may be linked to
     */
    public static boolean isSafeUrl(String url) {
        return safeUrl(url).isPresent();
    }

    /**
     * Builds {@code =HYPERLINK("url", "label")} for a safe URL.
     *
     * @param url the untrusted URL, may be null
     * @param label the text shown in the cell, may be null (treated as empty)
     * @return the formula, or empty when the URL must not be linked
     */
    public static Optional<String> build(String url, String label) {
        return safeUrl(url).map(safe
                -> "=HYPERLINK(\"" + escape(safe) + "\", \"" + escape(label == null ? "" : label) + "\")");
    }

    /** Escapes text for use inside a double-quoted Sheets string literal. */
    static String escape(String text) {
        return text.replace("\"", "\"\"");
    }

    private static boolean isHostTerminator(char c) {
        return c == '/' || c == '?' || c == '#' || c == '"' || Character.isWhitespace(c);
    }
}
