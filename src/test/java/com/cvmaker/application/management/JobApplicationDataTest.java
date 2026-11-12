package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

class JobApplicationDataTest {

    private static String utc(String header) {
        return JobApplicationData.parseEmailTimestamp(header, ZoneOffset.UTC);
    }

    @Test
    void parsesRfc2822DateWithNumericOffset() {
        assertThat(utc("Tue, 3 Jun 2025 11:05:30 +0200")).isEqualTo("2025-06-03T09:05:30");
        assertThat(utc("Tue, 03 Jun 2025 11:05:30 -0700")).isEqualTo("2025-06-03T18:05:30");
    }

    @Test
    void toleratesCommentsWhitespaceAndMissingSeconds() {
        assertThat(utc("Tue, 3 Jun 2025 11:05:30 +0000 (UTC)")).isEqualTo("2025-06-03T11:05:30");
        assertThat(utc("  Tue,  3 Jun 2025   11:05 +0100 (BST) ")).isEqualTo("2025-06-03T10:05:00");
    }

    @Test
    void toleratesMissingOrWrongWeekday() {
        assertThat(utc("3 Jun 2025 11:05:30 +0000")).isEqualTo("2025-06-03T11:05:30");
        assertThat(utc("Mon, 3 Jun 2025 11:05:30 +0000")).isEqualTo("2025-06-03T11:05:30"); // was a Tuesday
    }

    @Test
    void acceptsNamedZones() {
        assertThat(utc("Tue, 3 Jun 2025 11:05:30 GMT")).isEqualTo("2025-06-03T11:05:30");
        assertThat(utc("Tue, 3 Jun 2025 11:05:30 UTC")).isEqualTo("2025-06-03T11:05:30");
        assertThat(utc("Tue, 3 Jun 2025 11:05:30 UT")).isEqualTo("2025-06-03T11:05:30");
    }

    @Test
    void convertsToRequestedZone() {
        assertThat(JobApplicationData.parseEmailTimestamp("Tue, 3 Jun 2025 11:05:30 +0000",
                ZoneId.of("Europe/Berlin"))).isEqualTo("2025-06-03T13:05:30");
    }

    @Test
    void returnsNullForUnparseableInput() {
        assertThat(utc(null)).isNull();
        assertThat(utc("")).isNull();
        assertThat(utc("yesterday")).isNull();
        assertThat(utc("2025-06-03")).isNull();
    }

    @Test
    void constructorUsesEmailDateNotProcessingTime() {
        JobApplicationData data = new JobApplicationData("id", "s", "f", "Tue, 3 Jun 2025 11:05:30 +0000");

        String expected = ZonedDateTime.of(2025, 6, 3, 11, 5, 30, 0, ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        assertThat(data.getEmailTimestamp()).isEqualTo(expected);
        // GoogleSheetsService.isNewerApplication parses it with LocalDateTime.parse
        assertThat(LocalDateTime.parse(data.getEmailTimestamp())).isNotNull();
    }

    @Test
    void constructorFallsBackToNowForUnparseableDate() {
        LocalDateTime before = LocalDateTime.now().withNano(0);

        JobApplicationData data = new JobApplicationData("id", "s", "f", "not a date");

        assertThat(LocalDateTime.parse(data.getEmailTimestamp())).isAfterOrEqualTo(before);
    }

    @Test
    void emailsAreOrderedBySendTimeAcrossOffsets() {
        JobApplicationData earlier = new JobApplicationData("a", "s", "f", "Tue, 3 Jun 2025 12:00:00 +0200");
        JobApplicationData later = new JobApplicationData("b", "s", "f", "Tue, 3 Jun 2025 11:30:00 +0000");

        assertThat(LocalDateTime.parse(later.getEmailTimestamp()))
                .isAfter(LocalDateTime.parse(earlier.getEmailTimestamp()));
    }
}
