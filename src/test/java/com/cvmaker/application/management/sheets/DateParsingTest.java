package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DateParsingTest {

    private static final LocalDate NOV_11 = LocalDate.of(2025, 11, 11);

    @ParameterizedTest
    @ValueSource(strings = {
        // RFC 2822 email Date headers
        "Tue, 11 Nov 2025 10:22:33 +0000",
        "Tue, 11 Nov 2025 10:22:33 +0000 (UTC)",
        "Tue, 11 Nov 2025 10:22:33 GMT",
        "Tue, 11 Nov 2025 02:22:33 -0800 (PST)",
        "Tue, 11 Nov 2025 02:22:33 PST",
        "11 Nov 2025 10:22:33 +0000",
        "Tue, 11 Nov 2025 10:22 +0000",
        "tue, 11 nov 2025 10:22:33 +0000",
        "Tue,  11 Nov 2025   10:22:33 +0000",
        // the day of week is wrong; the date wins
        "Mon, 11 Nov 2025 10:22:33 +0000",
        // ISO-8601
        "2025-11-11",
        "2025-11-11T10:22:33",
        "2025-11-11T10:22:33.123456",
        "2025-11-11 10:22:33",
        "2025-11-11T10:22:33Z",
        "2025-11-11T10:22:33+01:00",
        "2025-11-11T10:22:33+01:00[Europe/Paris]",
        // numeric, day first
        "11/11/2025",
        "11-11-2025",
        "11.11.2025",
        "2025/11/11",
        // text
        "11 Nov 2025",
        "11 November 2025",
        "Nov 11, 2025",
        "November 11, 2025",
        "NOVEMBER 11, 2025",
        "  11 Nov 2025  ",
        // a date inside a longer text, or an email date with an unknown zone name
        "Tue, 11 Nov 2025 10:22:33 XYZ",
        "Tuesday, 11 Nov 2025 at 10am",
        "Received: 11 November 2025 (morning)"
    })
    void parsesSupportedForms(String text) {
        assertThat(DateParsing.parseDate(text)).contains(NOV_11);
    }

    @ParameterizedTest
    @CsvSource({
        "03/04/2025, 2025-04-03", // ambiguous: read day first
        "3/4/2025,   2025-04-03",
        "25/12/2024, 2024-12-25",
        "12/25/2024, 2024-12-25", // not a day-first date, so month first
        "12-25-2024, 2024-12-25",
        "29/02/2024, 2024-02-29",
        "5 Mar 2025, 2025-03-05"
    })
    void numericDatesAreDayFirstThenMonthFirst(String text, LocalDate expected) {
        assertThat(DateParsing.parseDate(text)).contains(expected);
    }

    @Test
    void emailDateKeepsTheSendersCalendarDay() {
        // 23:30 on the 11th in California is already the 12th in UTC.
        assertThat(DateParsing.parseDate("Tue, 11 Nov 2025 23:30:00 -0800")).contains(NOV_11);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "soon", "N/A", "31/02/2025", "2025-13-01", "29/02/2025", "11 Foo 2025"})
    void rejectsWhatIsNotADate(String text) {
        assertThat(DateParsing.parseDate(text)).isEmpty();
    }

    @Test
    void monthNamesAreEnglishWhateverTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(DateParsing.parseDate("Mar 5, 2025")).contains(LocalDate.of(2025, 3, 5));
            assertThat(DateParsing.parseDate("5 October 2025")).contains(LocalDate.of(2025, 10, 5));
            assertThat(DateParsing.parseDate("Sun, 5 Oct 2025 08:00:00 +0000")).contains(LocalDate.of(2025, 10, 5));
            assertThat(DateParsing.toDisplayDate("May 5, 2025")).isEqualTo("05/05/2025");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void displayDateIsDayMonthYear() {
        assertThat(DateParsing.toDisplayDate("Tue, 11 Nov 2025 10:22:33 +0000 (UTC)")).isEqualTo("11/11/2025");
        assertThat(DateParsing.toDisplayDate("2025-03-05")).isEqualTo("05/03/2025");
        assertThat(DateParsing.toDisplayDate("05/03/2025")).isEqualTo("05/03/2025");
    }

    @Test
    void displayDateIsEmptyForMissingAndUnchangedForUnparsedText() {
        assertThat(DateParsing.toDisplayDate(null)).isEmpty();
        assertThat(DateParsing.toDisplayDate("  ")).isEmpty();
        assertThat(DateParsing.toDisplayDate("next Tuesday")).isEqualTo("next Tuesday");
    }

    @Test
    void timestampWithOffsetIsAnExactInstant() {
        Instant expected = Instant.parse("2025-11-11T10:22:33Z");

        assertThat(DateParsing.parseTimestamp("2025-11-11T10:22:33Z")).contains(expected);
        assertThat(DateParsing.parseTimestamp("2025-11-11T11:22:33+01:00")).contains(expected);
        assertThat(DateParsing.parseTimestamp("Tue, 11 Nov 2025 02:22:33 -0800 (PST)")).contains(expected);
        assertThat(DateParsing.parseTimestamp(String.valueOf(expected.toEpochMilli()))).contains(expected);
    }

    @Test
    void localTimestampUsesTheGivenZone() {
        ZoneId paris = ZoneId.of("Europe/Paris");

        assertThat(DateParsing.parseTimestamp("2025-11-11T11:22:33", paris))
                .contains(Instant.parse("2025-11-11T10:22:33Z"));
        assertThat(DateParsing.parseTimestamp("2025-11-11 11:22:33.5", ZoneOffset.UTC))
                .contains(Instant.parse("2025-11-11T11:22:33.500Z"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "yesterday", "2025-11-11", "11/11/2025", "12345"})
    void timestampNeedsATime(String text) {
        assertThat(DateParsing.parseTimestamp(text, ZoneOffset.UTC)).isEmpty();
    }
}
