package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadsheetIdStoreTest {

    private static final String ID = "1AbC-dEf_0123456789xyz";

    @TempDir
    Path tempDir;

    @Test
    void missingFileHasNoId() {
        SpreadsheetIdStore store = new SpreadsheetIdStore(tempDir.resolve("google_sheet_id.txt"));

        assertThat(store.load()).isEmpty();
        assertThat(store.candidates(null)).isEmpty();
        assertThat(store.candidates("  ")).isEmpty();
    }

    @Test
    void savedIdIsLoadedAgain() throws Exception {
        Path file = tempDir.resolve("state/google_sheet_id.txt");
        SpreadsheetIdStore store = new SpreadsheetIdStore(file);

        store.save(ID);

        assertThat(file).exists();
        assertThat(store.load()).contains(ID);
        assertThat(new SpreadsheetIdStore(file).load()).contains(ID);
    }

    @Test
    void saveReplacesTheEarlierId() throws Exception {
        SpreadsheetIdStore store = new SpreadsheetIdStore(tempDir.resolve("id.txt"));

        store.save("first-id");
        store.save("second-id");

        assertThat(store.load()).contains("second-id");
    }

    @Test
    void loadSkipsCommentsAndBlankLinesAndTrims() throws Exception {
        Path file = tempDir.resolve("id.txt");
        Files.writeString(file, "\n# my tracker\n   " + ID + "  \nignored-second-line\n", StandardCharsets.UTF_8);

        assertThat(new SpreadsheetIdStore(file).load()).contains(ID);
    }

    @Test
    void contentThatIsNotAnIdIsIgnored() throws Exception {
        Path file = tempDir.resolve("id.txt");
        Files.writeString(file, "https://docs.google.com/spreadsheets/d/" + ID + "/edit\n", StandardCharsets.UTF_8);

        assertThat(new SpreadsheetIdStore(file).load()).isEmpty();
    }

    @Test
    void saveRejectsWhatIsNotAnId() {
        SpreadsheetIdStore store = new SpreadsheetIdStore(tempDir.resolve("id.txt"));

        assertThatIllegalArgumentException().isThrownBy(() -> store.save(null));
        assertThatIllegalArgumentException().isThrownBy(() -> store.save(""));
        assertThatIllegalArgumentException().isThrownBy(() -> store.save("two words"));
        assertThatIllegalArgumentException().isThrownBy(() -> store.save("line\nbreak"));
        assertThat(store.load()).isEmpty();
    }

    @Test
    void candidatesAreConfiguredThenSavedWithoutDuplicates() throws Exception {
        SpreadsheetIdStore store = new SpreadsheetIdStore(tempDir.resolve("id.txt"));
        store.save("saved-id");

        assertThat(store.candidates(null)).containsExactly("saved-id");
        assertThat(store.candidates("")).containsExactly("saved-id");
        assertThat(store.candidates(" configured-id ")).containsExactly("configured-id", "saved-id");
        assertThat(store.candidates("saved-id")).containsExactly("saved-id");
    }
}
