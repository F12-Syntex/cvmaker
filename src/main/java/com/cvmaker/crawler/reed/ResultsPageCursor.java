package com.cvmaker.crawler.reed;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Position within one Reed search results page. Pure logic with no Playwright types, so it can be
 * tested offline.
 *
 * <p>The job-card selector is resolved once per results page (the first selector that matches any
 * element), and cards are walked with an index that is local to that page. The crawler-wide
 * {@code jobsChecked} counter is only a statistic and is never used as an index.
 */
final class ResultsPageCursor {

    private String resultsUrl;
    private String selector;
    private int nextIndex;

    /**
     * Start walking a new results page: forget the resolved selector and reset the card index.
     *
     * @param url the URL of the results page, used to come back to it after visiting a job
     */
    void startResultsPage(String url) {
        this.resultsUrl = url;
        this.selector = null;
        this.nextIndex = 0;
    }

    /**
     * Resolve the job-card selector for this page if it is not resolved yet.
     *
     * @param selectors candidate selectors in priority order
     * @param counter   counts the elements a selector matches on the current page
     * @return true if a selector is resolved
     */
    boolean resolveSelector(List<String> selectors, ToIntFunction<String> counter) {
        if (selector == null) {
            selector = firstMatchingSelector(selectors, counter);
        }
        return selector != null;
    }

    /**
     * Take the next card index on this page.
     *
     * @param cardCount how many cards the resolved selector matches right now
     * @return the index to check next, or -1 if every card has been checked
     */
    int nextCardIndex(int cardCount) {
        if (nextIndex >= cardCount) {
            return -1;
        }
        return nextIndex++;
    }

    /**
     * @param currentUrl the URL the browser is on now
     * @return true if a results page was recorded and the browser has left it
     */
    boolean needsReturn(String currentUrl) {
        return resultsUrl != null && !sameDocument(currentUrl, resultsUrl);
    }

    String getResultsUrl() {
        return resultsUrl;
    }

    String getSelector() {
        return selector;
    }

    /**
     * @return the first selector whose count is above zero, or null; selectors whose count throws
     *         (for example an invalid selector) are skipped
     */
    static String firstMatchingSelector(List<String> selectors, ToIntFunction<String> counter) {
        for (String candidate : selectors) {
            try {
                if (counter.applyAsInt(candidate) > 0) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // try the next selector
            }
        }
        return null;
    }

    /**
     * @return true if both URLs are non-null and equal once any {@code #fragment} is removed
     */
    static boolean sameDocument(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return stripFragment(a).equals(stripFragment(b));
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }
}
