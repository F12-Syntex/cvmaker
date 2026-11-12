package com.cvmaker.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Typed, forgiving readers for {@link Properties} values shared by {@link ConfigManager} and
 * {@link CrawlerConfig}.
 *
 * <p>Every reader trims the raw value. A missing key returns the default silently; a malformed or
 * out-of-range value returns the default and prints a warning naming the key, so one typo in a
 * properties file no longer stops startup.
 */
final class TypedProperties {

    private TypedProperties() {
    }

    /** Trimmed string value, or the default when the key is absent. An empty value stays empty. */
    static String stringProp(Properties properties, String key, String defaultValue) {
        String raw = properties.getProperty(key);
        return raw == null ? defaultValue : raw.trim();
    }

    /** Integer value, or the default when absent or not an integer. */
    static int intProp(Properties properties, String key, int defaultValue) {
        return intProp(properties, key, defaultValue, Integer.MIN_VALUE);
    }

    /** Integer value of at least {@code min}, or the default when absent, malformed or below {@code min}. */
    static int intProp(Properties properties, String key, int defaultValue, int min) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.trim();
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            warn(key, value, "is not a whole number", defaultValue);
            return defaultValue;
        }
        if (parsed < min) {
            warn(key, value, "must be at least " + min, defaultValue);
            return defaultValue;
        }
        return parsed;
    }

    /** Double value within {@code [min, max]}, or the default when absent, malformed or out of range. */
    static double doubleProp(Properties properties, String key, double defaultValue, double min, double max) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.trim();
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            warn(key, value, "is not a number", defaultValue);
            return defaultValue;
        }
        if (Double.isNaN(parsed) || parsed < min || parsed > max) {
            warn(key, value, "must be between " + min + " and " + max, defaultValue);
            return defaultValue;
        }
        return parsed;
    }

    /**
     * Boolean value. Accepts {@code true}/{@code false} in any case with surrounding whitespace;
     * anything else (for example {@code ture}) returns the default with a warning, instead of the
     * silent {@code false} that {@link Boolean#parseBoolean(String)} gives.
     */
    static boolean boolProp(Properties properties, String key, boolean defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        warn(key, raw.trim(), "must be true or false", defaultValue);
        return defaultValue;
    }

    /**
     * Comma-separated list, each entry trimmed and empty entries dropped. Returns an unmodifiable
     * copy of the default when the key is absent.
     */
    static List<String> listProp(Properties properties, String key, List<String> defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return Collections.unmodifiableList(new ArrayList<>(defaultValue));
        }
        return splitList(raw);
    }

    /** Split a comma-separated string, trimming entries and dropping empty ones. Null gives an empty list. */
    static List<String> splitList(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return Collections.unmodifiableList(result);
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static void warn(String key, String value, String problem, Object defaultValue) {
        System.out.println("Warning: config value " + key + "='" + value + "' " + problem
                + ", using default " + defaultValue);
    }
}
