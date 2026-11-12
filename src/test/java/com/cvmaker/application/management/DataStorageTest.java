package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStorageTest {

    @TempDir
    Path dir;

    private Path db() {
        return dir.resolve(ApplicationConfig.JOB_APPLICATIONS_DB_FILE);
    }

    private static JobApplicationData fullRecord(String id) {
        JobApplicationData d = new JobApplicationData();
        d.setEmailId(id);
        d.setSubject("Your application || \"Senior\" dev |");
        d.setFromEmail("hr@acme.example");
        d.setCompanyName("Acme Grüße GmbH");
        d.setPositionTitle("Engineer");
        d.setDate("Tue, 3 Jun 2025 11:05:30 +0200");
        d.setCategory(EmailCategory.values()[0]);
        d.setExtractedInfo("line one\nline two\r\n|| tail |");
        d.setInterviewDate("2025-06-10");
        d.setInterviewLocation("Room 1\tBuilding 2");
        d.setApplicationStatus("Interview");
        d.setConfidenceScore(0.85);
        d.setJobRelated(true);
        d.setProcessedTimestamp("2025-06-03T12:00:00");
        d.setApplicationUrl("https://acme.example/jobs/1");
        d.setEmailLink("https://mail.google.com/mail/u/0/#inbox/" + id);
        d.setProvider("LinkedIn");
        d.setSalaryRange("€50k-€60k");
        d.setWorkLocation("Remote");
        d.setWorkType("Full-time");
        d.setContactPerson("Jane");
        d.setContactEmail("jane@acme.example");
        d.setNextSteps("Call\nthen test");
        d.setApplicationDeadline("2025-07-01");
        d.setRequiredSkills("Java, SQL");
        d.setRejectionReason(null);
        d.setOfferDetails(null); // the legacy format cannot tell "" from null
        d.setLastUpdated("2025-06-03T12:00:00");
        d.setEmailTimestamp("2025-06-03T11:05:30");
        return d;
    }

    private static void assertSameRecord(JobApplicationData actual, JobApplicationData expected) {
        assertThat(actual.toJsonLine()).isEqualTo(expected.toJsonLine());
    }

    @Test
    void jsonLineRoundTripKeepsEveryFieldAndHasNoLineBreak() {
        JobApplicationData original = fullRecord("abc123");
        original.setOfferDetails("");

        String line = original.toJsonLine();
        JobApplicationData copy = JobApplicationData.fromJsonLine(line);

        assertThat(line).doesNotContain("\n").doesNotContain("\r");
        assertThat(copy.getExtractedInfo()).isEqualTo("line one\nline two\r\n|| tail |");
        assertThat(copy.getSubject()).isEqualTo("Your application || \"Senior\" dev |");
        assertThat(copy.getCompanyName()).isEqualTo("Acme Grüße GmbH");
        assertThat(copy.getConfidenceScore()).isEqualTo(0.85);
        assertThat(copy.isJobRelated()).isTrue();
        assertThat(copy.getCategory()).isEqualTo(EmailCategory.values()[0]);
        assertThat(copy.getRejectionReason()).isNull();
        assertThat(copy.getOfferDetails()).isEmpty();
        assertSameRecord(copy, original);
    }

    @Test
    void fromJsonLineRejectsGarbage() {
        assertThat(JobApplicationData.fromJsonLine("{\"emailId\":\"x\",\"subj")).isNull();
        assertThat(JobApplicationData.fromJsonLine("not json")).isNull();
        assertThat(JobApplicationData.fromJsonLine(null)).isNull();
    }

    @Test
    void saveThenLoadRoundTripsThroughFile() {
        DataStorage storage = new DataStorage(dir);
        JobApplicationData a = fullRecord("id-a");
        JobApplicationData b = fullRecord("id-b");
        b.setExtractedInfo(null);

        storage.saveJobApplicationData(a);
        storage.saveJobApplicationData(b);
        Map<String, JobApplicationData> loaded = new DataStorage(dir).loadJobApplicationsDb();

        assertThat(loaded).containsOnlyKeys("id-a", "id-b");
        assertSameRecord(loaded.get("id-a"), a);
        assertSameRecord(loaded.get("id-b"), b);
    }

    @Test
    void saveReplacesRecordWithSameIdAndWritesOnlyJsonLines() throws IOException {
        DataStorage storage = new DataStorage(dir);
        JobApplicationData first = fullRecord("same");
        storage.saveJobApplicationData(first);
        JobApplicationData second = fullRecord("same");
        second.setApplicationStatus("Offer");
        storage.saveJobApplicationData(second);

        List<String> lines = Files.readAllLines(db(), StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith("{");
        assertThat(storage.loadJobApplicationsDb().get("same").getApplicationStatus()).isEqualTo("Offer");
        assertThat(tempFiles()).isEmpty();
    }

    @Test
    void createsMissingDataDirectory() {
        Path nested = dir.resolve("a/b");
        DataStorage storage = new DataStorage(nested);

        storage.saveJobApplicationData(fullRecord("x"));
        storage.saveProcessedEmailId("x");
        storage.saveSystemState();

        assertThat(storage.loadJobApplicationsDb()).containsOnlyKeys("x");
        assertThat(storage.loadProcessedEmailIds()).containsExactly("x");
    }

    // ------------------------------------------------------------- legacy format

    @Test
    @SuppressWarnings("deprecation")
    void legacyFileWrittenByOldSerializerStillLoads() throws IOException {
        JobApplicationData old = fullRecord("legacy1");
        old.setExtractedInfo("plain notes");
        old.setNextSteps("next");
        old.setSubject("Subject");
        Files.writeString(db(), old.serialize() + "\n", StandardCharsets.UTF_8);

        Map<String, JobApplicationData> loaded = new DataStorage(dir).loadJobApplicationsDb();

        assertThat(loaded).containsOnlyKeys("legacy1");
        assertSameRecord(loaded.get("legacy1"), old);
    }

    @Test
    void legacyRecordWithTrailingEmptyFieldsLoads() throws IOException {
        // 29 fields, everything after isJobRelated empty: the old split() dropped the trailing
        // empties, saw 13 fields and discarded the record.
        StringBuilder line = new StringBuilder("e1||Subj||from@x||Acme||Dev||date||||info||||||Applied||0.5||true");
        for (int i = 13; i < 29; i++) {
            line.append("||");
        }
        Files.writeString(db(), line + "\n", StandardCharsets.UTF_8);

        Map<String, JobApplicationData> loaded = new DataStorage(dir).loadJobApplicationsDb();

        assertThat(loaded).containsOnlyKeys("e1");
        assertThat(loaded.get("e1").getCompanyName()).isEqualTo("Acme");
        assertThat(loaded.get("e1").getApplicationStatus()).isEqualTo("Applied");
        assertThat(loaded.get("e1").getEmailTimestamp()).isNull();
    }

    @Test
    void oldestFourteenFieldLegacyRecordLoads() throws IOException {
        Files.writeString(db(), "e2||S||f||Co||P||d||||i||||||Applied||0.9||true||2025-01-01T10:00:00\n",
                StandardCharsets.UTF_8);

        JobApplicationData loaded = new DataStorage(dir).loadJobApplicationsDb().get("e2");

        assertThat(loaded.getProcessedTimestamp()).isEqualTo("2025-01-01T10:00:00");
        assertThat(loaded.getConfidenceScore()).isEqualTo(0.9);
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyRecordsBrokenByNewlinesAreJoinedBack() throws IOException {
        JobApplicationData early = fullRecord("brokenEarly");
        early.setSubject("Subject");
        early.setExtractedInfo("para one\n\npara two");   // field 7: first fragment is short
        early.setNextSteps("next");
        JobApplicationData late = fullRecord("brokenLate");
        late.setSubject("Subject");
        late.setExtractedInfo("notes");
        late.setNextSteps("step one\nstep two");          // field 22: second fragment is short
        JobApplicationData clean = fullRecord("clean");
        clean.setSubject("Subject");
        clean.setExtractedInfo("notes");
        clean.setNextSteps("next");
        Files.writeString(db(), early.serialize() + "\n" + late.serialize() + "\n" + clean.serialize() + "\n",
                StandardCharsets.UTF_8);

        Map<String, JobApplicationData> loaded = new DataStorage(dir).loadJobApplicationsDb();

        assertThat(loaded).containsOnlyKeys("brokenEarly", "brokenLate", "clean");
        assertThat(loaded.get("brokenEarly").getExtractedInfo()).isEqualTo("para one\n\npara two");
        assertThat(loaded.get("brokenEarly").getEmailTimestamp()).isEqualTo(early.getEmailTimestamp());
        assertThat(loaded.get("brokenLate").getNextSteps()).isEqualTo("step one\nstep two");
        assertThat(loaded.get("brokenLate").getEmailTimestamp()).isEqualTo(late.getEmailTimestamp());
        assertSameRecord(loaded.get("clean"), clean);
    }

    @Test
    @SuppressWarnings("deprecation")
    void savingOverLegacyFileConvertsToJsonAndKeepsBackup() throws IOException {
        JobApplicationData old = fullRecord("old");
        old.setSubject("Subject");
        old.setExtractedInfo("notes");
        old.setNextSteps("next");
        String legacyContent = old.serialize() + "\n";
        Files.writeString(db(), legacyContent, StandardCharsets.UTF_8);

        DataStorage storage = new DataStorage(dir);
        storage.saveJobApplicationData(fullRecord("new"));

        assertThat(Files.readAllLines(db(), StandardCharsets.UTF_8)).hasSize(2).allMatch(l -> l.startsWith("{"));
        Map<String, JobApplicationData> loaded = storage.loadJobApplicationsDb();
        assertThat(loaded).containsOnlyKeys("old", "new");
        assertSameRecord(loaded.get("old"), old);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> backups = files.filter(p -> p.getFileName().toString().contains(".bak-")).toList();
            assertThat(backups).hasSize(1);
            assertThat(Files.readString(backups.get(0), StandardCharsets.UTF_8)).isEqualTo(legacyContent);
        }
    }

    @Test
    void truncatedLastLineIsSkippedAndOtherRecordsLoad() throws IOException {
        String good = fullRecord("good").toJsonLine();
        Files.writeString(db(), good + "\n" + good.substring(0, good.length() / 2), StandardCharsets.UTF_8);

        assertThat(new DataStorage(dir).loadJobApplicationsDb()).containsOnlyKeys("good");
    }

    @Test
    void parseRecordsCountsUnreadableLines() {
        DataStorage.ParsedDb parsed = DataStorage.parseRecords(List.of("{broken", "", "short||line"));

        assertThat(parsed.records).isEmpty();
        assertThat(parsed.unreadable).isEqualTo(2);
        assertThat(parsed.legacy).isTrue();
        assertThat(parsed.needsBackup()).isTrue();
    }

    // ------------------------------------------------------------- atomic writes

    @Test
    void failedWriteLeavesOriginalFileAndNoTempFile() throws IOException {
        Path target = dir.resolve("file.txt");
        Files.writeString(target, "original\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> DataStorage.writeAtomically(target, w -> {
            w.write("partial new content");
            throw new IOException("disk full");
        })).isInstanceOf(IOException.class).hasMessage("disk full");

        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("original\n");
        assertThat(tempFiles()).isEmpty();
    }

    @Test
    void writeAtomicallyReplacesContent() throws IOException {
        Path target = dir.resolve("file.txt");
        Files.writeString(target, "original\n", StandardCharsets.UTF_8);

        DataStorage.writeAtomically(target, w -> w.write("new ü\n"));

        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo("new ü\n");
        assertThat(tempFiles()).isEmpty();
    }

    private List<Path> tempFiles() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
        }
    }

    // ------------------------------------------------------------- processed IDs

    @Test
    void processedIdsAppendAndRecoverFromPartialLastLine() throws IOException {
        Path ids = dir.resolve(ApplicationConfig.PROCESSED_EMAILS_FILE);
        Files.writeString(ids, "id1\npart", StandardCharsets.UTF_8); // interrupted append
        DataStorage storage = new DataStorage(dir);

        storage.saveProcessedEmailId("id2");
        storage.saveProcessedEmailId(null);
        storage.saveProcessedEmailId("  ");

        assertThat(storage.loadProcessedEmailIds()).containsExactlyInAnyOrder("id1", "part", "id2");
        assertThat(Files.readString(ids, StandardCharsets.UTF_8)).isEqualTo("id1\npart\nid2\n");
    }

    // ------------------------------------------------------------- CSV export

    @Test
    void csvCellNeutralisesFormulasAndEscapesQuotes() {
        assertThat(DataStorage.csvCell("=HYPERLINK(\"http://evil\")")).isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(DataStorage.csvCell("+1")).isEqualTo("\"'+1\"");
        assertThat(DataStorage.csvCell("-cmd")).isEqualTo("\"'-cmd\"");
        assertThat(DataStorage.csvCell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(DataStorage.csvCell("\tx")).isEqualTo("\"'\tx\"");
        assertThat(DataStorage.csvCell("plain, text")).isEqualTo("\"plain, text\"");
        assertThat(DataStorage.csvCell("")).isEqualTo("\"\"");
        assertThat(DataStorage.csvCell(null)).isEqualTo("\"\"");
    }

    @Test
    void csvExportIsUtf8LocaleIndependentAndSkipsNonJobEmails() throws IOException {
        Map<String, JobApplicationData> records = new LinkedHashMap<>();
        JobApplicationData job = fullRecord("job");
        job.setSubject("=cmd|' /C calc'!A0");
        records.put("job", job);
        JobApplicationData other = fullRecord("other");
        other.setJobRelated(false);
        records.put("other", other);
        Path out = dir.resolve("export.csv");

        Locale previous = Locale.getDefault();
        int count;
        try {
            Locale.setDefault(Locale.GERMANY);
            count = new DataStorage(dir).exportJobApplicationsToCSV(records, out.toString());
        } finally {
            Locale.setDefault(previous);
        }

        String csv = Files.readString(out, StandardCharsets.UTF_8);
        assertThat(count).isEqualTo(1);
        assertThat(csv).startsWith("Email ID,Subject,");
        assertThat(csv).contains("\"'=cmd|' /C calc'!A0\"");
        assertThat(csv).contains(",0.85,");
        assertThat(csv).contains("Acme Grüße GmbH");
        assertThat(csv).contains("\"line one\nline two\r\n|| tail |\"");
        assertThat(csv).doesNotContain("\"other\"");
    }
}
