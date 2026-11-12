package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GmailServiceTest {

    @Test
    void effectiveDateKeepsParseableHeader() {
        String header = "Tue, 3 Jun 2025 11:05:30 +0200";

        assertThat(GmailService.effectiveDate(header, 0L)).isEqualTo(header);
    }

    @Test
    void effectiveDateFallsBackToInternalDate() {
        long internal = 1748948730000L; // 2025-06-03T11:05:30Z

        assertThat(GmailService.effectiveDate("", internal)).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
        assertThat(GmailService.effectiveDate("garbage", internal)).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
        assertThat(GmailService.effectiveDate(null, internal)).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
    }

    @Test
    void effectiveDateWithoutInternalDateKeepsHeader() {
        assertThat(GmailService.effectiveDate("garbage", null)).isEqualTo("garbage");
    }
}
