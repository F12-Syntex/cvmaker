package com.cvmaker.application.management.sheets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Remembers the ID of the spreadsheet the application created, in a small text file, so that a
 * run without {@code GOOGLE_SHEETS_ID} reuses that spreadsheet instead of creating another one.
 */
public final class SpreadsheetIdStore {

    /**
     * Default file, in the working directory. The repository's {@code .gitignore} ignores
     * {@code *.txt}, so it is not committed.
     */
    public static final String DEFAULT_FILE_NAME = "google_sheet_id.txt";

    /** Spreadsheet IDs are URL-safe tokens; anything else in the file is not an ID. */
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path file;

    /**
     * @param file the file that holds the ID; it does not have to exist
     */
    public SpreadsheetIdStore(Path file) {
        this.file = file;
    }

    /** A store on {@link #DEFAULT_FILE_NAME} in the working directory. */
    public static SpreadsheetIdStore inWorkingDirectory() {
        return new SpreadsheetIdStore(Path.of(DEFAULT_FILE_NAME));
    }

    /** The file that holds the ID. */
    public Path file() {
        return file;
    }

    /**
     * Reads the saved ID: the first line that is not blank and does not start with {@code #}.
     *
     * @return the ID, or empty when the file is missing, unreadable or holds no valid ID
     */
    public Optional<String> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                return ID_PATTERN.matcher(value).matches() ? Optional.of(value) : Optional.empty();
            }
        } catch (IOException e) {
            System.err.println("Could not read " + file + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Saves the ID, replacing any earlier one.
     *
     * @param spreadsheetId the ID to save
     * @throws IOException when the file cannot be written
     * @throws IllegalArgumentException when the value is not a spreadsheet ID
     */
    public void save(String spreadsheetId) throws IOException {
        if (spreadsheetId == null || !ID_PATTERN.matcher(spreadsheetId.trim()).matches()) {
            throw new IllegalArgumentException("Not a spreadsheet ID: " + spreadsheetId);
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String content = "# Spreadsheet created by the job application tracker. Delete this file to start a new one.\n"
                + spreadsheetId.trim() + "\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /**
     * Lists the IDs to try, in order: the configured one, then the saved one.
     *
     * @param configuredId the ID from the configuration ({@code GOOGLE_SHEETS_ID}), may be null
     * @return zero, one or two distinct IDs
     */
    public List<String> candidates(String configuredId) {
        List<String> ids = new ArrayList<>(2);
        if (configuredId != null && !configuredId.trim().isEmpty()) {
            ids.add(configuredId.trim());
        }
        load().filter(saved -> !ids.contains(saved)).ifPresent(ids::add);
        return ids;
    }
}
