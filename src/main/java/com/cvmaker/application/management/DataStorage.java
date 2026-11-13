package com.cvmaker.application.management;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local files of the application manager: processed email IDs, the job application database and
 * the last-run state.
 *
 * <p>The database holds one JSON object per line ({@link JobApplicationData#toJsonLine()}), in
 * UTF-8. Files in the older {@code ||}-separated format still load, including records that were
 * split over several lines by a newline inside a field; they are converted to JSON on the next save,
 * after a one-off backup copy of the old file. Every rewrite goes to a temporary file in the same
 * directory which is then moved over the target, so a crash never leaves a truncated file.
 */
public class DataStorage {

    private static final DateTimeFormatter BACKUP_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String CSV_HEADER = "Email ID,Subject,From,Company,Position,Date,Category,Status,"
            + "Interview Date,Interview Location,Notes,Confidence,Processed Timestamp";

    private final Path processedEmailsFile;
    private final Path jobApplicationsDbFile;
    private final Path systemStateFile;

    /** Uses the files in {@link ApplicationConfig#getDataDirectory()}. */
    public DataStorage() {
        this(ApplicationConfig.getDataDirectory());
    }

    /** Uses the standard file names inside {@code dataDirectory}. */
    public DataStorage(Path dataDirectory) {
        this.processedEmailsFile = dataDirectory.resolve(ApplicationConfig.PROCESSED_EMAILS_FILE);
        this.jobApplicationsDbFile = dataDirectory.resolve(ApplicationConfig.JOB_APPLICATIONS_DB_FILE);
        this.systemStateFile = dataDirectory.resolve(ApplicationConfig.SYSTEM_STATE_FILE);
    }

    // ------------------------------------------------------------------ processed email IDs

    public Set<String> loadProcessedEmailIds() {
        Set<String> ids = new HashSet<>();
        if (!Files.exists(processedEmailsFile)) {
            return ids;
        }
        try {
            for (String line : readLines(processedEmailsFile)) {
                String id = line.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading processed email IDs: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Appends one ID. Appending is safe here: an interrupted append can only leave a partial last
     * line, which matches no real ID (so that email is simply processed again), and the next append
     * starts on a fresh line.
     */
    public synchronized void saveProcessedEmailId(String emailId) {
        if (emailId == null || emailId.isBlank()) {
            return;
        }
        try {
            createParentDirectories(processedEmailsFile);
            String prefix = endsWithoutNewline(processedEmailsFile) ? "\n" : "";
            Files.writeString(processedEmailsFile, prefix + emailId.trim() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Error saving processed email ID: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ job application database

    /** Loads the database, keyed by email ID, in file order. Later records win over earlier ones. */
    public Map<String, JobApplicationData> loadJobApplicationsDb() {
        try {
            return readDb().records;
        } catch (IOException e) {
            System.err.println("Error loading job applications database: " + e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Stamps the processed time on {@code data} and stores it, replacing an earlier record with the
     * same email ID. The whole file is rewritten atomically.
     */
    public synchronized void saveJobApplicationData(JobApplicationData data) {
        data.setProcessedTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        try {
            ParsedDb existing = readDb();
            putRecord(existing.records, data);
            writeDb(existing.records, existing.needsBackup());
        } catch (IOException e) {
            System.err.println("Error saving job application data: " + e.getMessage());
        }
    }

    /** Replaces the whole database with {@code records}, atomically. */
    public synchronized void saveJobApplicationsDb(Map<String, JobApplicationData> records) throws IOException {
        writeDb(records, Files.exists(jobApplicationsDbFile) && readDb().needsBackup());
    }

    private ParsedDb readDb() throws IOException {
        if (!Files.exists(jobApplicationsDbFile)) {
            return new ParsedDb();
        }
        ParsedDb parsed = parseRecords(readLines(jobApplicationsDbFile));
        if (parsed.unreadable > 0) {
            System.err.println("Warning: skipped " + parsed.unreadable + " unreadable record(s) in "
                    + jobApplicationsDbFile);
        }
        return parsed;
    }

    private void writeDb(Map<String, JobApplicationData> records, boolean backupFirst) throws IOException {
        if (backupFirst && Files.exists(jobApplicationsDbFile)) {
            backup(jobApplicationsDbFile);
        }
        writeAtomically(jobApplicationsDbFile, writer -> {
            for (JobApplicationData data : records.values()) {
                if (data != null) {
                    writer.write(data.toJsonLine());
                    writer.write('\n');
                }
            }
        });
    }

    private static void backup(Path file) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".bak-" + LocalDateTime.now().format(BACKUP_SUFFIX));
        try {
            Files.copy(file, backup);
            System.out.println("Backed up " + file + " to " + backup + " before converting it to JSON lines");
        } catch (FileAlreadyExistsException e) {
            // A backup from the same second already exists
        }
    }

    /** Result of parsing the database file. */
    static final class ParsedDb {
        final Map<String, JobApplicationData> records = new LinkedHashMap<>();
        /** Records that could not be decoded (truncated JSON, or legacy lines with too few fields). */
        int unreadable;
        /** Whether any record was in the legacy {@code ||} format. */
        boolean legacy;

        /** The file holds content that a JSON rewrite would change or drop: keep a copy first. */
        boolean needsBackup() {
            return legacy || unreadable > 0;
        }
    }

    /**
     * Parses database lines in either format. A legacy record that was broken over several lines by
     * a newline inside a field is joined back: a line with fewer than 14 {@code ||} fields cannot
     * start a record, so it is appended to the previous one, as is the line after a fragment that is
     * itself too short.
     */
    static ParsedDb parseRecords(List<String> lines) {
        ParsedDb result = new ParsedDb();
        StringBuilder pending = null;
        for (String line : lines) {
            String trimmed = line.strip();
            // The JSON parser tolerates text after the closing brace, so a record line must also end with one
            JobApplicationData json = trimmed.startsWith("{") && trimmed.endsWith("}")
                    ? JobApplicationData.fromJsonLine(trimmed) : null;
            if (trimmed.startsWith("{") && json == null && pending != null && isIncompleteLegacy(pending)) {
                // Continuation of a legacy record split by a newline: it only looks like JSON
                pending.append('\n').append(line);
            } else if (trimmed.startsWith("{")) {
                flushLegacy(pending, result);
                pending = null;
                JobApplicationData data = json;
                if (data == null) {
                    result.unreadable++;
                } else {
                    putRecord(result.records, data);
                }
            } else if (trimmed.isEmpty()) {
                if (pending != null && isIncompleteLegacy(pending)) {
                    pending.append('\n');
                }
            } else if (pending != null
                    && (isIncompleteLegacy(pending)
                        || JobApplicationData.legacyFieldCount(line) < JobApplicationData.LEGACY_MIN_FIELDS)) {
                pending.append('\n').append(line);
            } else {
                flushLegacy(pending, result);
                pending = new StringBuilder(line);
            }
        }
        flushLegacy(pending, result);
        return result;
    }

    /**
     * Stores {@code data} under its email ID. A record without one gets its own synthetic key, so
     * several of them stay distinct instead of replacing each other on a shared null key.
     */
    private static void putRecord(Map<String, JobApplicationData> records, JobApplicationData data) {
        String key = data.getEmailId();
        if (key == null || key.isBlank()) {
            int n = 1;
            do {
                key = NO_ID_KEY_PREFIX + n++;
            } while (records.containsKey(key));
        }
        records.put(key, data);
    }

    /** Map key prefix for records that have no email ID; never written to the file. */
    private static final String NO_ID_KEY_PREFIX = "\u0000no-email-id-";

    private static boolean isIncompleteLegacy(StringBuilder pending) {
        return JobApplicationData.legacyFieldCount(pending.toString()) < JobApplicationData.LEGACY_MIN_FIELDS;
    }

    private static void flushLegacy(StringBuilder pending, ParsedDb result) {
        if (pending == null) {
            return;
        }
        result.legacy = true;
        JobApplicationData data = JobApplicationData.deserialize(pending.toString().strip());
        if (data == null) {
            result.unreadable++;
        } else {
            putRecord(result.records, data);
        }
    }

    // ------------------------------------------------------------------ CSV export

    /**
     * Writes the job-related records to {@code filePath} as RFC 4180 CSV in UTF-8. Cells that a
     * spreadsheet would run as a formula are prefixed with {@code '}. The file is written atomically.
     *
     * @return the number of rows written
     */
    public int exportJobApplicationsToCSV(Map<String, JobApplicationData> jobApplicationsDb, String filePath)
            throws IOException {
        int[] count = {0};
        writeAtomically(Path.of(filePath), writer -> {
            writer.write(CSV_HEADER);
            writer.write('\n');
            for (JobApplicationData data : jobApplicationsDb.values()) {
                if (data != null && data.isJobRelated()) {
                    writer.write(toCsvRow(data));
                    writer.write('\n');
                    count[0]++;
                }
            }
        });
        return count[0];
    }

    static String toCsvRow(JobApplicationData data) {
        return String.join(",",
                csvCell(data.getEmailId()),
                csvCell(data.getSubject()),
                csvCell(data.getFromEmail()),
                csvCell(data.getCompanyName()),
                csvCell(data.getPositionTitle()),
                csvCell(data.getDate()),
                csvCell(data.getCategory() != null ? data.getCategory().getDisplayName() : null),
                csvCell(data.getApplicationStatus()),
                csvCell(data.getInterviewDate()),
                csvCell(data.getInterviewLocation()),
                csvCell(data.getExtractedInfo()),
                String.format(Locale.ROOT, "%.2f", data.getConfidenceScore()),
                csvCell(data.getProcessedTimestamp()));
    }

    /** One quoted CSV cell: formula-like text is neutralised, quotes are doubled. */
    static String csvCell(String field) {
        if (field == null) {
            return "\"\"";
        }
        String value = field;
        // A spreadsheet trims leading whitespace before deciding whether a cell is a formula
        int first = 0;
        while (first < value.length()
                && (Character.isWhitespace(value.charAt(first)) || Character.isSpaceChar(value.charAt(first)))) {
            first++;
        }
        boolean formula = first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0;
        boolean control = !value.isEmpty() && "\t\r".indexOf(value.charAt(0)) >= 0;
        if (formula || control) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------ system state

    public synchronized void saveSystemState() {
        String content = "last_run=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n";
        try {
            writeAtomically(systemStateFile, writer -> writer.write(content));
        } catch (IOException e) {
            System.err.println("Error saving system state: " + e.getMessage());
        }
    }

    public void loadSystemState() {
        if (!Files.exists(systemStateFile)) {
            return;
        }
        try {
            for (String line : readLines(systemStateFile)) {
                line = line.trim();
                if (line.startsWith("last_run=")) {
                    System.out.println("Last run: " + line.substring("last_run=".length()));
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading system state: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ file helpers

    /** Writes text to a writer; may throw {@link IOException}. */
    @FunctionalInterface
    interface WriterAction {
        void writeTo(Writer writer) throws IOException;
    }

    /**
     * Writes {@code target} through a temporary file in the same directory, synced to disk and then
     * moved over the target (atomically where the file system supports it). If {@code action} or
     * the write fails, the target is left as it was and the temporary file is removed.
     */
    static void writeAtomically(Path target, WriterAction action) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, absolute.getFileName().toString() + ".", ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp.toFile());
                 Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                action.writeTo(writer);
                writer.flush();
                out.getFD().sync();
            }
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Reads all lines as UTF-8; bytes that are not valid UTF-8 (old platform-charset files) become U+FFFD. */
    private static List<String> readLines(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void createParentDirectories(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static boolean endsWithoutNewline(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(raf.length() - 1);
            return raf.read() != '\n';
        }
    }
}
