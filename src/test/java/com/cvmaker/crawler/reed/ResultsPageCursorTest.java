package com.cvmaker.crawler.reed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ResultsPageCursorTest {

    private static final List<String> SELECTORS = List.of(".a", ".b", ".c");

    @Test
    void resolvesTheFirstSelectorThatMatchesAnything() {
        Map<String, Integer> counts = Map.of(".a", 0, ".b", 4, ".c", 9);
        assertThat(ResultsPageCursor.firstMatchingSelector(SELECTORS, counts::get)).isEqualTo(".b");
    }

    @Test
    void returnsNullWhenNoSelectorMatches() {
        assertThat(ResultsPageCursor.firstMatchingSelector(SELECTORS, s -> 0)).isNull();
        assertThat(ResultsPageCursor.firstMatchingSelector(List.of(), s -> 5)).isNull();
    }

    @Test
    void skipsSelectorsWhoseCountFails() {
        Map<String, Integer> counts = Map.of(".b", 2);
        String found = ResultsPageCursor.firstMatchingSelector(SELECTORS, s -> {
            if (s.equals(".a")) {
                throw new IllegalStateException("invalid selector");
            }
            return counts.getOrDefault(s, 0);
        });
        assertThat(found).isEqualTo(".b");
    }

    @Test
    void resolvesOncePerResultsPage() {
        ResultsPageCursor cursor = new ResultsPageCursor();
        cursor.startResultsPage("https://www.reed.co.uk/jobs/java-jobs");
        List<String> queried = new ArrayList<>();
        assertThat(cursor.resolveSelector(SELECTORS, s -> {
            queried.add(s);
            return 3;
        })).isTrue();
        assertThat(cursor.resolveSelector(SELECTORS, s -> {
            queried.add(s);
            return 3;
        })).isTrue();
        assertThat(queried).containsExactly(".a");
        assertThat(cursor.getSelector()).isEqualTo(".a");
    }

    @Test
    void walksEachCardExactlyOnce() {
        ResultsPageCursor cursor = new ResultsPageCursor();
        cursor.startResultsPage("u");
        List<Integer> seen = new ArrayList<>();
        int i;
        while ((i = cursor.nextCardIndex(3)) >= 0) {
            seen.add(i);
        }
        assertThat(seen).containsExactly(0, 1, 2);
        assertThat(cursor.nextCardIndex(3)).isEqualTo(-1);
    }

    @Test
    void indexIsLocalToThePageAndIgnoresAGlobalCounter() {
        // The old loop started at the crawler-wide jobsChecked, so after checking 5 cards on one
        // selector it began the next selector (or the next results page) at card 5.
        ResultsPageCursor cursor = new ResultsPageCursor();
        cursor.startResultsPage("page-1");
        for (int k = 0; k < 5; k++) {
            cursor.nextCardIndex(5);
        }
        cursor.startResultsPage("page-2");
        assertThat(cursor.nextCardIndex(3)).isZero();
        assertThat(cursor.getSelector()).isNull();
    }

    @Test
    void continuesWhereItLeftOffWhenTheCardListGrowsOrShrinks() {
        ResultsPageCursor cursor = new ResultsPageCursor();
        cursor.startResultsPage("u");
        assertThat(cursor.nextCardIndex(2)).isEqualTo(0);
        // After returning to the results page, fewer cards are rendered.
        assertThat(cursor.nextCardIndex(1)).isEqualTo(-1);
        // More cards are rendered again: the cursor did not advance past an unseen card.
        assertThat(cursor.nextCardIndex(4)).isEqualTo(1);
    }

    @Test
    void handlesNegativeCount() {
        ResultsPageCursor cursor = new ResultsPageCursor();
        cursor.startResultsPage("u");
        assertThat(cursor.nextCardIndex(-1)).isEqualTo(-1);
        assertThat(cursor.nextCardIndex(1)).isEqualTo(0);
    }

    @Test
    void needsReturnOnlyWhenOffTheResultsPage() {
        ResultsPageCursor cursor = new ResultsPageCursor();
        assertThat(cursor.needsReturn("https://www.reed.co.uk/jobs/123")).isFalse();

        cursor.startResultsPage("https://www.reed.co.uk/jobs/java-jobs?keywords=java");
        assertThat(cursor.needsReturn("https://www.reed.co.uk/jobs/java-jobs?keywords=java")).isFalse();
        assertThat(cursor.needsReturn("https://www.reed.co.uk/jobs/java-jobs?keywords=java#top")).isFalse();
        assertThat(cursor.needsReturn("https://www.reed.co.uk/jobs/java-developer/55512345")).isTrue();
        assertThat(cursor.needsReturn(null)).isTrue();
    }

    @Test
    void sameDocumentIgnoresFragmentsOnly() {
        assertThat(ResultsPageCursor.sameDocument("a?x=1#f", "a?x=1")).isTrue();
        assertThat(ResultsPageCursor.sameDocument("a?x=1", "a?x=2")).isFalse();
        assertThat(ResultsPageCursor.sameDocument(null, "a")).isFalse();
        assertThat(ResultsPageCursor.sameDocument(null, null)).isFalse();
    }
}
