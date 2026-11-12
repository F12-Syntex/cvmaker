package com.cvmaker.application.management.sheets;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place where the tracker parses dates.
 *
 * <p>Dates reach the spreadsheet from three sources: the email {@code Date} header (RFC 2822),
 * timestamps the application writes itself (ISO-8601), and free text extracted by the AI
 * ("12 March 2025", "03/04/2025"). All month and day names are English, whatever the default
 * locale of the JVM is.
 *
 * <p>Purely numeric dates are ambiguous. They are read day first ({@code dd/MM/yyyy}), which is
 * also the format the sheet displays, and month first only when the day-first reading is not a
 * real date ({@code 12/25/2024}).
 */
public final class DateParsing {

    /** The date format shown in the spreadsheet. */
    public static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ENGLISH);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** A trailing RFC 2822 comment, as in {@code +0000 (UTC)}. */
    private static final Pattern TRAILING_COMMENT = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
    /** A leading day of week, as in {@code Tue, }. It is redundant, and sometimes wrong. */
    private static final Pattern LEADING_DAY_OF_WEEK = Pattern.compile("^[A-Za-z]{3,9},\\s*");
    private static final Pattern TRAILING_ZONE_NAME = Pattern.compile("\\s([A-Za-z]{1,5})$");
    private static final Pattern DAY_MONTH_YEAR_ANYWHERE = Pattern.compile(
            "\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?,?\\s+(\\d{4})\\b");
    private static final Pattern EPOCH_MILLIS = Pattern.compile("^\\d{12,14}$");

    /** Zone names RFC 2822 still allows in old mail (section 4.3), as offsets. */
    private static final Map<String, String> OBSOLETE_ZONES = Map.ofEntries(
            Map.entry("UT", "+0000"), Map.entry("UTC", "+0000"), Map.entry("GMT", "+0000"), Map.entry("Z", "+0000"),
            Map.entry("EST", "-0500"), Map.entry("EDT", "-0400"),
            Map.entry("CST", "-0600"), Map.entry("CDT", "-0500"),
            Map.entry("MST", "-0700"), Map.entry("MDT", "-0600"),
            Map.entry("PST", "-0800"), Map.entry("PDT", "-0700"),
            Map.entry("BST", "+0100"), Map.entry("CET", "+0100"), Map.entry("CEST", "+0200"));

    private static final DateTimeFormatter LOCAL_DATE_TIME_WITH_SPACE = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter(Locale.ENGLISH);

    /** Date-only formats, in the order they are tried. Day first comes before month first. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            strict("d/M/uuuu"),
            strict("d-M-uuuu"),
            strict("d.M.uuuu"),
            strict("uuuu/M/d"),
            strict("M/d/uuuu"),
            strict("M-d-uuuu"),
            strict("d MMM uuuu"),
            strict("d MMMM uuuu"),
            strict("d MMM, uuuu"),
            strict("d MMMM, uuuu"),
            strict("MMM d, uuuu"),
            strict("MMMM d, uuuu"),
            strict("MMM d uuuu"),
            strict("MMMM d uuuu"));

    private DateParsing() {
    }

    /**
     * Parses a date in any of the supported forms and returns its calendar date.
     *
     * <p>For a value with a time zone (an email header) this is the date in that zone, so
     * {@code Tue, 11 Nov 2025 23:30:00 -0800} is 11 November.
     *
     * @param text the date text, may be null
     * @return the date, or empty when the text is null, blank or not a date
     */
    public static Optional<LocalDate> parseDate(String text) {
        String value = normalize(text);
        if (value == null) {
            return Optional.empty();
        }

        Optional<OffsetDateTime> zoned = parseZoned(value);
        if (zoned.isPresent()) {
            return zoned.map(OffsetDateTime::toLocalDate);
        }
        Optional<LocalDateTime> local = parseLocalDateTime(value);
        if (local.isPresent()) {
            return local.map(LocalDateTime::toLocalDate);
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value, format));
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        return findDayMonthYear(value);
    }

    /**
     * Formats a date for the spreadsheet.
     *
     * @param text the date text, may be null
     * @return {@code dd/MM/yyyy} when the text is a date; an empty string when it is null or
     *         blank; otherwise the text unchanged, so that unparsed information is not lost
     */
    public static String toDisplayDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        return parseDate(text).map(DISPLAY_FORMAT::format).orElse(text);
    }

    /**
     * Parses a point in time, using the system default zone for values without one.
     *
     * @param text the timestamp text, may be null
     * @return the instant, or empty when the text is not a timestamp
     * @see #parseTimestamp(String, ZoneId)
     */
    public static Optional<Instant> parseTimestamp(String text) {
        return parseTimestamp(text, ZoneId.systemDefault());
    }

    /**
     * Parses a point in time. Accepted: ISO-8601 with an offset or zone, ISO-8601 local
     * date-times (with {@code T} or a space), RFC 2822 email dates and epoch milliseconds.
     * A date without a time is not a timestamp.
     *
     * @param text the timestamp text, may be null
     * @param zoneForLocalValues the zone assumed for values that carry no offset
     * @return the instant, or empty when the text is not a timestamp
     */
    public static Optional<Instant> parseTimestamp(String text, ZoneId zoneForLocalValues) {
        String value = normalize(text);
        if (value == null) {
            return Optional.empty();
        }
        if (EPOCH_MILLIS.matcher(value).matches()) {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        }
        Optional<OffsetDateTime> zoned = parseZoned(value);
        if (zoned.isPresent()) {
            return zoned.map(OffsetDateTime::toInstant);
        }
        try {
            return parseLocalDateTime(value).map(local -> local.atZone(zoneForLocalValues).toInstant());
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String value = WHITESPACE.matcher(text.trim()).replaceAll(" ");
        return value.isEmpty() ? null : value;
    }

    /** ISO-8601 with offset or zone, or an RFC 2822 date-time. */
    private static Optional<OffsetDateTime> parseZoned(String value) {
        try {
            return Optional.of(OffsetDateTime.parse(value));
        } catch (DateTimeParseException e) {
            // not an ISO offset date-time
        }
        try {
            return Optional.of(ZonedDateTime.parse(value).toOffsetDateTime());
        } catch (DateTimeParseException e) {
            // not an ISO zoned date-time
        }
        return parseRfc2822(value);
    }

    private static Optional<OffsetDateTime> parseRfc2822(String value) {
        String candidate = TRAILING_COMMENT.matcher(value).replaceFirst("");
        candidate = LEADING_DAY_OF_WEEK.matcher(candidate).replaceFirst("");

        Matcher zone = TRAILING_ZONE_NAME.matcher(candidate);
        if (zone.find()) {
            String offset = OBSOLETE_ZONES.get(zone.group(1).toUpperCase(Locale.ROOT));
            if (offset == null) {
                return Optional.empty();
            }
            candidate = candidate.substring(0, zone.start(1)) + offset;
        }
        try {
            return Optional.of(OffsetDateTime.parse(candidate, DateTimeFormatter.RFC_1123_DATE_TIME));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDateTime> parseLocalDateTime(String value) {
        try {
            return Optional.of(LocalDateTime.parse(value));
        } catch (DateTimeParseException e) {
            // not ISO with 'T'
        }
        try {
            return Optional.of(LocalDateTime.parse(value, LOCAL_DATE_TIME_WITH_SPACE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Last resort: a "11 Nov 2025" somewhere inside a longer text. */
    private static Optional<LocalDate> findDayMonthYear(String value) {
        Matcher matcher = DAY_MONTH_YEAR_ANYWHERE.matcher(value);
        while (matcher.find()) {
            String candidate = matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3);
            for (String pattern : new String[]{"d MMM uuuu", "d MMMM uuuu"}) {
                try {
                    return Optional.of(LocalDate.parse(candidate, strict(pattern)));
                } catch (DateTimeParseException e) {
                    // try the next pattern, then the next match
                }
            }
        }
        return Optional.empty();
    }

    private static DateTimeFormatter strict(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
    }
}
