package com.cvmaker.crawler.reed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobSearchServiceTest {

    @Test
    void cardSelectorsDoNotIncludeTheOverBroadDataQaMatch() {
        // "[data-qa*='job']" matched titles and company names inside each card as well.
        assertThat(JobSearchService.JOB_CARDS_SELECTORS).doesNotContain("[data-qa*='job']");
        assertThat(JobSearchService.JOB_CARDS_SELECTORS).doesNotHaveDuplicates();
    }
}
