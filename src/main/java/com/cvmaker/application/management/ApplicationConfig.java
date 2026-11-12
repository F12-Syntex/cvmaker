package com.cvmaker.application.management;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Settings for the job application manager.
 *
 * <p>Values are read from a {@code .env} file in the working directory when one exists, and
 * otherwise from the process environment. Blank values count as missing. Optional settings have
 * plain getters that return {@code null} (or a default) when unset; code that cannot work without a
 * value should call {@link #require(String)}, which fails with a message naming the missing key.
 */
public class ApplicationConfig {

    public static final String APPLICATION_NAME = "Job Application Manager";
    public static final String TOKENS_DIRECTORY_PATH = "tokens";
    public static final String PROCESSED_EMAILS_FILE = "processed_emails.txt";
    public static final String JOB_APPLICATIONS_DB_FILE = "job_applications.txt";
    public static final String SYSTEM_STATE_FILE = "system_state.txt";

    /** Key names, in one place so error messages and docs agree. */
    public static final String GOOGLE_CLIENT_ID = "GOOGLE_CLIENT_ID";
    public static final String GOOGLE_CLIENT_SECRET = "GOOGLE_CLIENT_SECRET";
    public static final String GOOGLE_SHEETS_ID = "GOOGLE_SHEETS_ID";
    public static final String EXA_API_KEY = "EXA_API_KEY";
    /** Optional directory for the local data files. Defaults to the working directory. */
    public static final String DATA_DIR = "CVMAKER_DATA_DIR";

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    /**
     * Returns the value for {@code key} from {@code .env}, falling back to the process environment.
     *
     * @return the trimmed value, or {@code null} when it is unset or blank
     */
    public static String get(String key) {
        return lookup(key, dotenv::get, System::getenv);
    }

    /** Like {@link #get(String)}, but returns {@code defaultValue} when the key is unset or blank. */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the value for {@code key}, or throws when it is unset or blank.
     *
     * @throws IllegalStateException naming the key and where it can be set
     */
    public static String require(String key) {
        return requireValue(key, get(key));
    }

    /** Pure lookup: the first non-blank value from {@code primary}, then {@code fallback}. */
    static String lookup(String key, Function<String, String> primary, Function<String, String> fallback) {
        String value = normalise(primary.apply(key));
        if (value == null) {
            value = normalise(fallback.apply(key));
        }
        return value;
    }

    /** Pure validation behind {@link #require(String)}. */
    static String requireValue(String key, String value) {
        String normalised = normalise(value);
        if (normalised == null) {
            throw new IllegalStateException("Missing required setting " + key
                    + ": add " + key + "=... to the .env file in the working directory"
                    + " or set it as an environment variable.");
        }
        return normalised;
    }

    private static String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String getGoogleClientId() {
        return get(GOOGLE_CLIENT_ID);
    }

    public static String getGoogleClientSecret() {
        return get(GOOGLE_CLIENT_SECRET);
    }

    /**
     * @deprecated Unused: Sheets uses the same OAuth client as Gmail ({@link #getGoogleClientId()}).
     *             Kept only for source compatibility; will be removed.
     */
    @Deprecated
    public static String getGoogleSheetsId() {
        return get("GOOGLE_SHEETS_CLIENT_ID");
    }

    /**
     * @deprecated Unused: Sheets uses the same OAuth client as Gmail ({@link #getGoogleClientSecret()}).
     *             Kept only for source compatibility; will be removed.
     */
    @Deprecated
    public static String getGoogleSheetsSecret() {
        return get("GOOGLE_SHEETS_CLIENT_SECRET");
    }

    /** The spreadsheet ID, or an empty string when none is configured yet. */
    public static String getGoogleSheetId() {
        return get(GOOGLE_SHEETS_ID, "");
    }

    public static String getExaApiKey() {
        return get(EXA_API_KEY);
    }

    /** Directory that holds the local data files ({@value #DATA_DIR}, default: working directory). */
    public static Path getDataDirectory() {
        return dataDirectory(get(DATA_DIR));
    }

    /** Pure resolution behind {@link #getDataDirectory()}. */
    static Path dataDirectory(String configured) {
        String value = normalise(configured);
        return value == null ? Paths.get(".") : Paths.get(value);
    }

    public static Path getProcessedEmailsFile() {
        return getDataDirectory().resolve(PROCESSED_EMAILS_FILE);
    }

    public static Path getJobApplicationsDbFile() {
        return getDataDirectory().resolve(JOB_APPLICATIONS_DB_FILE);
    }

    public static Path getSystemStateFile() {
        return getDataDirectory().resolve(SYSTEM_STATE_FILE);
    }
}
